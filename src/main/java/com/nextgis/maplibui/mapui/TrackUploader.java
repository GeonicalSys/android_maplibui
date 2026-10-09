package com.nextgis.maplibui.mapui;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.map.TrackLayer;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.HttpResponse;
import com.nextgis.maplib.util.MapUtil;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.maplib.util.TrackSendSettings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Shares packet delivery between the live recorder and durable workers, on the captured layer. */
public final class TrackUploader {
    private static final Object DELIVERY_LOCK = new Object();
    private TrackUploader() {}

    private static Uri pointsUri(Context context) {
        return Uri.parse("content://" + ((IGISApplication) context.getApplicationContext()).getAuthority()
                + "/" + TrackLayer.TABLE_TRACKPOINTS);
    }

    public static boolean hasPending(Context context, TrackLayer layer) {
        try (Cursor rows = layer.query(pointsUri(context), new String[]{"rowid"},
                TrackLayer.FIELD_SENT + " = 0", null, null, "1")) {
            return rows != null && rows.moveToFirst();
        }
    }

    public static boolean upload(Context context, TrackLayer layer) throws Exception {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        synchronized (DELIVERY_LOCK) {
            if (Thread.currentThread().isInterrupted()) return false;
            if (!TrackSendSettings.isEnabled(preferences) || !hasPending(context, layer)) return true;
            String base = preferences.getString("tracker_hub_url", TrackWorker.HOST);
            String device = base + TrackWorker.URL + "/" + TrackWorker.getUid(context);
            // Registration gates delivery, never the persisted user's upload intent.
            HttpResponse registration = NetworkUtil.get(device + "/registered", null, null, false);
            if (!registration.isOk() || !new JSONObject(registration.getResponseBody()).optBoolean("registered")) return false;
            while (TrackSendSettings.isEnabled(preferences) && !Thread.currentThread().isInterrupted()) {
                JSONArray payload = new JSONArray();
                List<String> rows = new ArrayList<>();
                Uri uri = pointsUri(context);
                try (Cursor points = layer.query(uri, new String[]{"rowid AS upload_id", "*"},
                        TrackLayer.FIELD_SENT + " = 0", null,
                        TrackLayer.FIELD_TIMESTAMP + " ASC, rowid ASC", "100")) {
                    while (points != null && points.moveToNext()) {
                        GeoPoint point = new GeoPoint(points.getDouble(points.getColumnIndexOrThrow(TrackLayer.FIELD_LON)),
                                points.getDouble(points.getColumnIndexOrThrow(TrackLayer.FIELD_LAT)));
                        point.setCRS(GeoConstants.CRS_WEB_MERCATOR);
                        point.project(GeoConstants.CRS_WGS84);
                        JSONObject item = new JSONObject();
                        item.put("lt", point.getY()); item.put("ln", point.getX());
                        item.put("ts", points.getLong(points.getColumnIndexOrThrow(TrackLayer.FIELD_TIMESTAMP)) / 1000);
                        item.put("a", points.getDouble(points.getColumnIndexOrThrow(TrackLayer.FIELD_ELE)));
                        item.put("s", points.getInt(points.getColumnIndexOrThrow(TrackLayer.FIELD_SAT)));
                        item.put("ft", "3d".equals(points.getString(points.getColumnIndexOrThrow(TrackLayer.FIELD_FIX))) ? 3 : 2);
                        item.put("sp", points.getDouble(points.getColumnIndexOrThrow(TrackLayer.FIELD_SPEED)) * 18 / 5);
                        item.put("ha", points.getDouble(points.getColumnIndexOrThrow(TrackLayer.FIELD_ACCURACY)));
                        item.put("c", points.getDouble(points.getColumnIndexOrThrow(TrackLayer.FIELD_BEARING)));
                        payload.put(item);
                        rows.add(points.getString(points.getColumnIndexOrThrow("upload_id")));
                    }
                }
                if (rows.isEmpty()) return true;
                if (!TrackSendSettings.isEnabled(preferences) || Thread.currentThread().isInterrupted()) return false;
                HttpResponse response = NetworkUtil.post(device + "/packet", payload.toString(), null, null, false);
                if (!response.isOk()) return false;
                ContentValues values = new ContentValues();
                values.put(TrackLayer.FIELD_SENT, 1);
                // Timestamps can repeat across sessions; acknowledge only the rows in this packet.
                int acknowledged = layer.update(uri, values, "rowid IN (" + MapUtil.makePlaceholders(rows.size()) + ") AND "
                        + TrackLayer.FIELD_SENT + " = 0", rows.toArray(new String[0]));
                if (acknowledged != rows.size()) return false;
            }
            return !TrackSendSettings.isEnabled(preferences);
        }
    }
}
