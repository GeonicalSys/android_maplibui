package com.nextgis.maplibui.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.datasource.GeoGeometry;
import com.nextgis.maplib.datasource.GeoGeometryFactory;
import com.nextgis.maplib.datasource.GeoLineString;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.service.WalkEditService;
import com.nextgis.maplibui.service.WalkGeometrySnapshot;

import java.util.UUID;

/** Durable ownership of one walk and its optional foreground point-creation session. */
public final class WalkSessionStore {
    public static final String KEY_SESSION = "walk_session_id";
    public static final String KEY_MAP = "walk_map_path";
    public static final String KEY_FULL_GEOMETRY = "walk_full_geometry";
    public static final String KEY_PHASE = "walk_phase";
    public static final String KEY_POINT = "walk_point_session";
    public static final String KEY_POINT_LAYER = "walk_point_layer";
    public static final String KEY_POINT_TOOL = "walk_point_tool";
    public static final String KEY_POINT_STAGE = "walk_point_stage";
    public static final String KEY_REVISION = "walk_revision";
    public static final String STAGE_CHOOSE = "choose";
    public static final String STAGE_GEOMETRY = "geometry";
    public static final String STAGE_FORM = "form";

    private WalkSessionStore() { }

    public static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(WalkEditService.TEMP_PREFERENCES, Context.MODE_PRIVATE);
    }

    public static String mapPath(Context context) {
        MapBase map = ((IGISApplication) context.getApplicationContext()).getMap();
        return map == null ? "" : map.getPath().getAbsolutePath();
    }

    public static final class Snapshot {
        public final String id, mapPath, fullWkt, pointId, pointStage;
        public final int layerId, member, ring, pointLayer, pointTool;
        public final long featureId, revision;
        public final boolean gpsPaused;
        public final WalkSessionPolicy.Phase phase;

        private Snapshot(SharedPreferences prefs) {
            id = prefs.getString(KEY_SESSION, "");
            mapPath = prefs.getString(KEY_MAP, "");
            fullWkt = prefs.getString(KEY_FULL_GEOMETRY, "");
            pointId = prefs.getString(KEY_POINT, "");
            pointStage = prefs.getString(KEY_POINT_STAGE, "");
            pointLayer = prefs.getInt(KEY_POINT_LAYER, Constants.NOT_FOUND);
            pointTool = prefs.getInt(KEY_POINT_TOOL, 0);
            layerId = prefs.getInt(ConstantsUI.KEY_LAYER_ID, Constants.NOT_FOUND);
            featureId = prefs.getLong(ConstantsUI.KEY_FEATURE_ID, Constants.NOT_FOUND);
            member = prefs.getInt(WalkEditService.KEY_GEOMETRY_INDEX, 0);
            ring = prefs.getInt(WalkEditService.KEY_RING_INDEX, 0);
            revision = prefs.getLong(KEY_REVISION, 0);
            gpsPaused = prefs.getBoolean(WalkEditService.KEY_GPS_PAUSED, false);
            phase = WalkSessionPolicy.Phase.valueOf(prefs.getString(KEY_PHASE, "RECORDING"));
        }

        public boolean isPointActive() { return !pointId.isEmpty(); }
        public GeoGeometry geometry() {
            return WalkGeometrySnapshot.restore(fullWkt, member, ring);
        }
    }

    public static synchronized Snapshot load(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = preferences(context);
        if (prefs.getString(KEY_SESSION, "").isEmpty()) return null;
        try { return new Snapshot(prefs); }
        catch (RuntimeException invalid) { return null; }
    }

    public static boolean isCurrentMap(Context context, Snapshot session) {
        return session != null && session.mapPath.equals(mapPath(context));
    }

    public static boolean isPointActive(Context context) {
        Snapshot session = load(context);
        return session != null && session.isPointActive();
    }

    public static synchronized String begin(Context context, int layerId, long featureId,
                                             GeoGeometry full, int member, int ring,
                                             int insertion, String activity) {
        if (load(context) != null || WalkEditService.hasValidDraft(context)
                || ProjectOperationCoordinator.isBusy()) return null;
        GeoLineString part = WalkGeometrySnapshot.part(full, member, ring);
        String id = UUID.randomUUID().toString();
        boolean saved = preferences(context).edit().clear()
                .putString(KEY_SESSION, id).putString(KEY_MAP, mapPath(context))
                .putString(KEY_PHASE, WalkSessionPolicy.Phase.RECORDING.name())
                .putString(KEY_FULL_GEOMETRY, full.toWKT(true))
                .putString(ConstantsUI.KEY_GEOMETRY, part.toWKT(true))
                .putInt(ConstantsUI.KEY_LAYER_ID, layerId)
                .putLong(ConstantsUI.KEY_FEATURE_ID, featureId)
                .putInt(WalkEditService.KEY_GEOMETRY_INDEX, member)
                .putInt(WalkEditService.KEY_RING_INDEX, ring)
                .putInt(WalkEditService.KEY_INSERT_INDEX, insertion)
                .putString(ConstantsUI.TARGET_CLASS, activity)
                .putLong(KEY_REVISION, 1).commit();
        if (saved) notifyChanged(context);
        return saved ? id : null;
    }

    /** Called in the service's persistence transaction, without touching the point lock. */
    public static synchronized boolean updateGeometry(Context context, String id,
            GeoLineString part, int insertion, boolean gpsPaused) {
        Snapshot session = load(context);
        if (session == null || !session.id.equals(id)
                || session.phase == WalkSessionPolicy.Phase.FINISHED) return false;
        GeoGeometry full = WalkGeometrySnapshot.replace(session.geometry(), part, session.member, session.ring);
        return preferences(context).edit()
                .putString(KEY_FULL_GEOMETRY, full.toWKT(true))
                .putString(ConstantsUI.KEY_GEOMETRY, part.toWKT(true))
                .putInt(WalkEditService.KEY_INSERT_INDEX, insertion)
                .putBoolean(WalkEditService.KEY_GPS_PAUSED, gpsPaused)
                .putLong(WalkEditService.KEY_UPDATED_AT, System.currentTimeMillis())
                .putLong(KEY_REVISION, session.revision + 1).commit();
    }

    /** Upgrade an old part-only draft once its owning feature has been reconstructed. */
    public static synchronized boolean adoptLegacy(Context context, GeoGeometry full) {
        if (load(context) != null || !WalkEditService.hasValidDraft(context)) return false;
        String id = UUID.randomUUID().toString();
        boolean saved = preferences(context).edit().putString(KEY_SESSION, id)
                .putString(KEY_MAP, mapPath(context)).putString(KEY_FULL_GEOMETRY, full.toWKT(true))
                .putString(KEY_PHASE, WalkSessionPolicy.Phase.RECORDING.name())
                .putLong(KEY_REVISION, 1).commit();
        if (saved) {
            WalkEditService.adoptLegacySession(id);
            notifyChanged(context);
        }
        return saved;
    }

    public static synchronized boolean allows(Context context, String id, WalkSessionPolicy.Command command) {
        Snapshot session = load(context);
        return isCurrentMap(context, session) && WalkSessionPolicy.mayControl(
                session.id, id, session.pointId, session.phase, command);
    }

    public static synchronized boolean setPhase(Context context, String id, WalkSessionPolicy.Phase phase) {
        Snapshot session = load(context);
        if (session == null || !session.id.equals(id) || session.isPointActive()) return false;
        boolean saved = preferences(context).edit().putString(KEY_PHASE, phase.name())
                .putLong(KEY_REVISION, session.revision + 1).commit();
        if (saved) notifyChanged(context);
        return saved;
    }

    public static synchronized boolean clear(Context context, String id) {
        Snapshot session = load(context);
        if (session == null || !session.id.equals(id) || session.isPointActive()) return false;
        boolean saved = preferences(context).edit().clear().commit();
        if (saved) notifyChanged(context);
        return saved;
    }

    public static synchronized String beginPoint(Context context, int tool) {
        Snapshot session = load(context);
        if (!isCurrentMap(context, session) || !WalkSessionPolicy.mayStartPoint(
                session.id, session.pointId, session.phase)) return null;
        String token = UUID.randomUUID().toString();
        boolean saved = preferences(context).edit().putString(KEY_POINT, token)
                .putString(KEY_POINT_STAGE, STAGE_CHOOSE).putInt(KEY_POINT_TOOL, tool)
                .putInt(KEY_POINT_LAYER, Constants.NOT_FOUND).commit();
        if (saved) notifyChanged(context);
        return saved ? token : null;
    }

    public static synchronized boolean bindPoint(Context context, String token, int layerId, String stage) {
        Snapshot session = load(context);
        if (session == null || token == null || token.isEmpty() || !token.equals(session.pointId)) return false;
        boolean saved = preferences(context).edit().putInt(KEY_POINT_LAYER, layerId)
                .putString(KEY_POINT_STAGE, stage).commit();
        if (saved) notifyChanged(context);
        return saved;
    }

    public static synchronized boolean endPoint(Context context, String token) {
        Snapshot session = load(context);
        if (session == null || token == null || token.isEmpty() || !token.equals(session.pointId)) return false;
        boolean saved = preferences(context).edit().remove(KEY_POINT).remove(KEY_POINT_LAYER)
                .remove(KEY_POINT_STAGE).remove(KEY_POINT_TOOL).commit();
        if (saved) notifyChanged(context);
        return saved;
    }

    public static void notifyChanged(Context context) {
        context.sendBroadcast(new Intent(WalkEditService.WALKEDIT_CHANGE).setPackage(context.getPackageName()));
    }
}
