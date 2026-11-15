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
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import com.nextgis.maplib.util.NgwSyncResourceBudget;

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
    public static final String KEY_INITIAL_VALUES = "walk_initial_values";
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
        public final org.json.JSONObject initialValues;
        public final int layerId, member, ring, pointLayer, pointTool;
        public final long featureId, revision, updatedAt;
        public final String checkpointOwner;
        public final long checkpointSequence;
        public final int insertion;
        private final List<WalkCheckpointWriter.Edit> checkpointEdits;
        private final IOException checkpointError;
        private volatile boolean recoveryFailed;
        private final long committedCheckpointSequence;
        public final boolean gpsPaused;
        public final WalkSessionPolicy.Phase phase;

        private Snapshot(SharedPreferences prefs) {
            id = prefs.getString(KEY_SESSION, "");
            mapPath = prefs.getString(KEY_MAP, "");
            fullWkt = prefs.getString(KEY_FULL_GEOMETRY, "");
            String values = prefs.getString(KEY_INITIAL_VALUES, null);
            try { initialValues = values == null ? null : new org.json.JSONObject(values); }
            catch (org.json.JSONException invalid) { throw new IllegalStateException(invalid); }
            pointId = prefs.getString(KEY_POINT, "");
            pointStage = prefs.getString(KEY_POINT_STAGE, "");
            pointLayer = prefs.getInt(KEY_POINT_LAYER, Constants.NOT_FOUND);
            pointTool = prefs.getInt(KEY_POINT_TOOL, 0);
            layerId = prefs.getInt(ConstantsUI.KEY_LAYER_ID, Constants.NOT_FOUND);
            featureId = prefs.getLong(ConstantsUI.KEY_FEATURE_ID, Constants.NOT_FOUND);
            member = prefs.getInt(WalkEditService.KEY_GEOMETRY_INDEX, 0);
            ring = prefs.getInt(WalkEditService.KEY_RING_INDEX, 0);
            revision = prefs.getLong(KEY_REVISION, 0);
            updatedAt = prefs.getLong(WalkEditService.KEY_UPDATED_AT, 0);
            checkpointOwner = prefs.getString(WalkCheckpointWriter.KEY_OWNER, id);
            long sequence = prefs.getLong(WalkCheckpointWriter.KEY_SEQUENCE, 0);
            committedCheckpointSequence = sequence;
            int insertion = prefs.getInt(WalkEditService.KEY_INSERT_INDEX, 0);
            boolean paused = prefs.getBoolean(WalkEditService.KEY_GPS_PAUSED, false);
            List<WalkCheckpointWriter.Edit> edits = java.util.Collections.emptyList();
            IOException problem = null;
            try { edits = WalkCheckpointWriter.capture(new File(mapPath), checkpointOwner, sequence); }
            catch (IOException error) { problem = error; }
            for (WalkCheckpointWriter.Edit edit : edits) {
                sequence = edit.sequence; insertion = edit.insertion; paused = edit.paused;
            }
            checkpointEdits = edits; checkpointError = problem;
            checkpointSequence = sequence; this.insertion = insertion; gpsPaused = paused;
            phase = WalkSessionPolicy.Phase.valueOf(prefs.getString(KEY_PHASE, "RECORDING"));
        }

        public boolean isPointActive() { return !pointId.isEmpty(); }
        public boolean hasCheckpointFailure() { return checkpointError != null || recoveryFailed; }
        private GeoGeometry baseGeometry() {
            return WalkGeometrySnapshot.restore(fullWkt, member, ring);
        }
        public GeoGeometry geometry() {
            if (checkpointError != null) { recoveryFailed = true; return null; }
            GeoGeometry full = baseGeometry();
            try {
                WalkCheckpointWriter.replay(WalkGeometrySnapshot.part(full, member, ring), insertion,
                        gpsPaused, committedCheckpointSequence,
                        checkpointEdits);
            } catch (IOException | RuntimeException error) { recoveryFailed = true; return null; }
            return full;
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
        return begin(context, layerId, featureId, full, member, ring, insertion, activity, null);
    }

    /** Persist the new object's typed category in the same transaction as recorder ownership. */
    public static synchronized String begin(Context context, int layerId, long featureId,
                                             GeoGeometry full, int member, int ring,
                                             int insertion, String activity, org.json.JSONObject initialValues) {
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
                .putString(KEY_INITIAL_VALUES, featureId == Constants.NOT_FOUND && initialValues != null
                        ? initialValues.toString() : null)
                .putInt(WalkEditService.KEY_GEOMETRY_INDEX, member)
                .putInt(WalkEditService.KEY_RING_INDEX, ring)
                .putInt(WalkEditService.KEY_INSERT_INDEX, insertion)
                .putString(ConstantsUI.TARGET_CLASS, activity)
                .putLong(KEY_REVISION, 1).commit();
        if (saved) notifyChanged(context);
        return saved ? id : null;
    }

    /** Legacy synchronous API remains for callers without a live checkpoint writer. */
    public static synchronized boolean updateGeometry(Context context, String id,
            GeoLineString part, int insertion, boolean gpsPaused) {
        Snapshot session = load(context);
        if (session == null || !session.id.equals(id)
                || WalkCheckpointWriter.isActive(new File(session.mapPath), session.checkpointOwner)
                || !session.checkpointEdits.isEmpty()) return false;
        return updateGeometry(context, id, part, insertion, gpsPaused, -1, session.checkpointOwner);
    }

    /** Geometry and the acknowledged delta sequence are one durable preference commit. */
    public static synchronized boolean updateGeometry(Context context, String id,
            GeoLineString part, int insertion, boolean gpsPaused, long sequence, String owner) {
        Snapshot session = load(context);
        SharedPreferences prefs = preferences(context);
        if (session == null || !session.id.equals(id) || session.phase == WalkSessionPolicy.Phase.FINISHED
                || !session.checkpointOwner.equals(owner)
                || sequence >= 0 && sequence < prefs.getLong(WalkCheckpointWriter.KEY_SEQUENCE, 0)) return false;
        try {
            NgwSyncResourceBudget.requireHeap();
            NgwSyncResourceBudget.requireFeatureBytes(session.fullWkt.length());
            GeoGeometry full = WalkGeometrySnapshot.replace(session.baseGeometry(), part, session.member, session.ring);
            String fullWkt = full.toWKT(true), partWkt = part.toWKT(true);
            NgwSyncResourceBudget.requireFeatureBytes(fullWkt.length());
            NgwSyncResourceBudget.requireSpace(context.getFilesDir(), 2L * (fullWkt.length() + partWkt.length()) + 32768);
            SharedPreferences.Editor edit = prefs.edit()
                    .putString(KEY_FULL_GEOMETRY, fullWkt).putString(ConstantsUI.KEY_GEOMETRY, partWkt)
                    .putInt(WalkEditService.KEY_INSERT_INDEX, insertion)
                    .putBoolean(WalkEditService.KEY_GPS_PAUSED, gpsPaused)
                    .putLong(WalkEditService.KEY_UPDATED_AT, System.currentTimeMillis())
                    .putLong(KEY_REVISION, session.revision + 1);
            if (sequence >= 0) edit.putLong(WalkCheckpointWriter.KEY_SEQUENCE, sequence)
                    .putString(WalkCheckpointWriter.KEY_OWNER, owner);
            return commitDraftPreferences(prefs, edit);
        } catch (IOException | RuntimeException failure) { return false; }
    }

    /** A failed disk commit must not leave an acknowledged/cleared draft in the in-memory map. */
    public static synchronized boolean commitDraftPreferences(SharedPreferences prefs, SharedPreferences.Editor edit) {
        Map<String, ?> before = prefs.getAll();
        boolean saved;
        try { saved = edit.commit(); } catch (RuntimeException error) { saved = false; }
        if (saved) return true;
        SharedPreferences.Editor rollback = prefs.edit().clear();
        for (Map.Entry<String, ?> entry : before.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if (value instanceof String) rollback.putString(key, (String) value);
            else if (value instanceof Boolean) rollback.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) rollback.putInt(key, (Integer) value);
            else if (value instanceof Long) rollback.putLong(key, (Long) value);
            else if (value instanceof Float) rollback.putFloat(key, (Float) value);
            else if (value instanceof java.util.Set) rollback.putStringSet(key, new java.util.HashSet<>((java.util.Set<String>) value));
        }
        try { rollback.commit(); } catch (RuntimeException ignored) { }
        return false;
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
        SharedPreferences prefs = preferences(context);
        SharedPreferences.Editor edit = prefs.edit().putString(KEY_PHASE, phase.name())
                .putLong(KEY_REVISION, session.revision + 1);
        if (phase == WalkSessionPolicy.Phase.FINISHED) {
            if (WalkCheckpointWriter.isActive(new File(session.mapPath), session.checkpointOwner)) return false;
            try {
                GeoGeometry full = session.geometry();
                GeoLineString part = WalkGeometrySnapshot.part(full, session.member, session.ring);
                String fullWkt = full.toWKT(true), partWkt = part.toWKT(true);
                NgwSyncResourceBudget.requireFeatureBytes(fullWkt.length());
                NgwSyncResourceBudget.requireSpace(context.getFilesDir(), 2L * (fullWkt.length() + partWkt.length()) + 32768);
                edit.putString(KEY_FULL_GEOMETRY, fullWkt).putString(ConstantsUI.KEY_GEOMETRY, partWkt)
                        .putLong(WalkCheckpointWriter.KEY_SEQUENCE, session.checkpointSequence)
                        .putString(WalkCheckpointWriter.KEY_OWNER, session.checkpointOwner)
                        .putInt(WalkEditService.KEY_INSERT_INDEX, session.insertion)
                        .putBoolean(WalkEditService.KEY_GPS_PAUSED, session.gpsPaused);
            } catch (IOException | RuntimeException error) { return false; }
        }
        boolean saved = commitDraftPreferences(prefs, edit);
        if (saved) notifyChanged(context);
        return saved;
    }

    public static synchronized boolean clear(Context context, String id) {
        Snapshot session = load(context);
        if (session == null || !session.id.equals(id) || session.isPointActive()
                || WalkCheckpointWriter.isActive(new File(session.mapPath), session.checkpointOwner)) return false;
        SharedPreferences prefs = preferences(context);
        boolean saved = commitDraftPreferences(prefs, prefs.edit().clear());
        if (saved) {
            if (!WalkCheckpointWriter.deleteFinished(new File(session.mapPath), session.checkpointOwner))
                android.util.Log.w("WalkCheckpoint", "Discarded journal cleanup is pending");
            notifyChanged(context);
        }
        return saved;
    }

    /** Remove a session whose foreground recorder never acknowledged its initial snapshot. */
    public static synchronized boolean clearUnconfirmedStart(Context context, String id) {
        Snapshot session = load(context);
        if (session == null || !session.id.equals(id) || session.isPointActive()
                || session.phase != WalkSessionPolicy.Phase.RECORDING
                || session.revision > 1 || session.updatedAt > 0
                || !session.checkpointEdits.isEmpty()
                || WalkCheckpointWriter.isActive(new File(session.mapPath), session.checkpointOwner)
                || WalkEditService.isSessionRunning(id)) return false;
        SharedPreferences prefs = preferences(context);
        boolean saved = commitDraftPreferences(prefs, prefs.edit().clear());
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
