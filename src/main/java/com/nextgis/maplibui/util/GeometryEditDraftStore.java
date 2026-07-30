package com.nextgis.maplibui.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.datasource.GeoGeometry;
import com.nextgis.maplib.datasource.GeoGeometryFactory;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Crash-safe journal for unfinished, non-walk geometry editing.
 *
 * <p>The fragment instance-state bundle is not durable when Android removes the task or kills
 * the process. This store intentionally contains only the latest geometry and enough identity to
 * reopen the editor. Attribute-form and walk drafts have independent stores.</p>
 */
public final class GeometryEditDraftStore {
    private static final String PREFS = "geometry_edit_draft";
    private static final String KEY_STATE = "state_json";
    private static final int VERSION = 1;

    /** Values mirror MapFragment modes without introducing an app-module dependency. */
    public static final int MODE_EDIT = 2;
    public static final int MODE_EDIT_BY_TOUCH = 5;

    private GeometryEditDraftStore() {
    }

    public static final class Snapshot {
        public int layerId = Constants.NOT_FOUND;
        public long featureId = Constants.NOT_FOUND;
        public int editMode = Constants.NOT_FOUND;
        /** WKT in CRS_WEB_MERCATOR. */
        public String geometryWkt;
        /** Absolute active map path. Prevents layer-id collisions between Collector projects. */
        public String mapPath;
        public long updatedAtMs;

        public boolean isValid() {
            return layerId != Constants.NOT_FOUND
                    && featureId >= Constants.NOT_FOUND
                    && (editMode == MODE_EDIT || editMode == MODE_EDIT_BY_TOUCH)
                    && geometryWkt != null
                    && !geometryWkt.isEmpty()
                    && mapPath != null
                    && !mapPath.isEmpty();
        }
    }

    public static boolean save(Context context, Snapshot snapshot, String reason) {
        if (context == null || snapshot == null || !snapshot.isValid()) {
            HyperLog.w(Constants.TAG, "GeometryDraft save skipped: invalid snapshot reason="
                    + safeReason(reason));
            return false;
        }

        try {
            snapshot.updatedAtMs = System.currentTimeMillis();
            boolean committed = preferences(context).edit()
                    .putString(KEY_STATE, encode(snapshot).toString())
                    .commit();
            if (committed) {
                HyperLog.v(Constants.TAG, "GeometryDraft saved reason=" + safeReason(reason)
                        + " layer=" + snapshot.layerId
                        + " feature=" + snapshot.featureId
                        + " mode=" + snapshot.editMode
                        + " wktChars=" + snapshot.geometryWkt.length());
            } else {
                HyperLog.e(Constants.TAG, "GeometryDraft commit failed reason="
                        + safeReason(reason));
            }
            return committed;
        } catch (JSONException | RuntimeException e) {
            HyperLog.e(Constants.TAG, "GeometryDraft encode failed reason="
                    + safeReason(reason) + ": " + e.getMessage(), e);
            return false;
        }
    }

    public static Snapshot load(Context context) {
        if (context == null) {
            return null;
        }

        String value = preferences(context).getString(KEY_STATE, null);
        if (value == null || value.isEmpty()) {
            return null;
        }

        try {
            Snapshot snapshot = decode(new JSONObject(value));
            if (snapshot == null || !snapshot.isValid()) {
                HyperLog.w(Constants.TAG, "GeometryDraft invalid; discarding");
                clear(context, "invalid");
                return null;
            }
            HyperLog.v(Constants.TAG, "GeometryDraft loaded layer=" + snapshot.layerId
                    + " feature=" + snapshot.featureId
                    + " mode=" + snapshot.editMode
                    + " ageMs=" + Math.max(0L, System.currentTimeMillis() - snapshot.updatedAtMs)
                    + " wktChars=" + snapshot.geometryWkt.length());
            return snapshot;
        } catch (JSONException | RuntimeException e) {
            HyperLog.e(Constants.TAG, "GeometryDraft corrupt; discarding: " + e.getMessage(), e);
            clear(context, "corrupt");
            return null;
        }
    }

    public static boolean hasAnyDraft(Context context) {
        return load(context) != null;
    }

    public static void clear(Context context, String reason) {
        if (context == null) {
            return;
        }
        boolean existed = preferences(context).contains(KEY_STATE);
        boolean committed = preferences(context).edit().remove(KEY_STATE).commit();
        if (!committed) {
            HyperLog.e(Constants.TAG, "GeometryDraft clear failed reason=" + safeReason(reason));
        } else if (existed) {
            HyperLog.v(Constants.TAG, "GeometryDraft cleared reason=" + safeReason(reason));
        }
    }

    public static GeoGeometry geometryFromSnapshot(Snapshot snapshot) {
        if (snapshot == null || snapshot.geometryWkt == null || snapshot.geometryWkt.isEmpty()) {
            return null;
        }
        try {
            return GeoGeometryFactory.fromWKT(
                    snapshot.geometryWkt, GeoConstants.CRS_WEB_MERCATOR);
        } catch (RuntimeException e) {
            HyperLog.e(Constants.TAG, "GeometryDraft WKT parse failed: " + e.getMessage(), e);
            return null;
        }
    }

    static JSONObject encode(Snapshot snapshot) throws JSONException {
        return new JSONObject()
                .put("version", VERSION)
                .put("layer_id", snapshot.layerId)
                .put("feature_id", snapshot.featureId)
                .put("edit_mode", snapshot.editMode)
                .put("geometry_wkt", snapshot.geometryWkt)
                .put("map_path", snapshot.mapPath)
                .put("updated_at", snapshot.updatedAtMs);
    }

    static Snapshot decode(JSONObject json) throws JSONException {
        if (json == null || json.optInt("version", Constants.NOT_FOUND) != VERSION) {
            return null;
        }
        Snapshot snapshot = new Snapshot();
        snapshot.layerId = json.optInt("layer_id", Constants.NOT_FOUND);
        snapshot.featureId = json.optLong("feature_id", Constants.NOT_FOUND);
        snapshot.editMode = json.optInt("edit_mode", Constants.NOT_FOUND);
        snapshot.geometryWkt = json.optString("geometry_wkt", null);
        snapshot.mapPath = json.optString("map_path", null);
        snapshot.updatedAtMs = json.optLong("updated_at", 0L);
        return snapshot;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isEmpty() ? "unspecified" : reason;
    }
}
