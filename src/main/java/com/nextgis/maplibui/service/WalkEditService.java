/*
 *  Project:  NextGIS Mobile
 *  Purpose:  Mobile GIS for Android.
 *  Author:   Dmitry Baryshnikov, dmitry.baryshnikov@nextgis.com
 *  Author:   Stanislav Petriakov, becomeglory@gmail.com
 * ****************************************************************************
 *  Copyright (c) 2015-2019 NextGIS, info@nextgis.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.maplibui.service;

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.location.GnssStatus;
import android.location.GpsStatus;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;

import com.nextgis.maplib.api.GpsEventListener;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.datasource.GeoGeometry;
import com.nextgis.maplib.datasource.GeoGeometryFactory;
import com.nextgis.maplib.datasource.GeoLineString;
import com.nextgis.maplib.datasource.GeoLinearRing;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.map.MapBase;
import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.location.GpsEventSource;
import com.nextgis.maplib.util.LocationRecordingSampler;
import com.nextgis.maplib.util.LocationFixPolicy;
import com.nextgis.maplib.util.PermissionUtil;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.BackgroundRecordingSoundMonitor;
import com.nextgis.maplibui.util.NotificationHelper;
import com.nextgis.maplibui.util.WalkSessionStore;
import com.nextgis.maplibui.util.WalkSessionPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.nextgis.maplibui.util.NotificationHelper.createBuilder;

/**
 * Service to gather position data during walking
 */
@SuppressLint("MissingPermission")
public class WalkEditService extends Service implements GpsEventSource.RecordingListener
{
    private static final int WALK_NOTIFICATION_ID = 7;
    public static final String TEMP_PREFERENCES = "walkedit_temp";
    public static final String EXTRA_HEADER = "extra_";
    public static final String ACTION_STOP = "com.nextgis.maplibui.WALKEDIT_STOP";
    public static final String ACTION_START = "com.nextgis.maplibui.WALKEDIT_START";
    public static final String ACTION_FINISH = "com.nextgis.maplibui.WALKEDIT_FINISH";
    private static final String EXTRA_USER_RESUME = "walk_user_resume";
    public static final String WALKEDIT_CHANGE = "com.nextgis.maplibui.WALKEDIT_CHANGE";
    /** Intent extra / prefs: clear durable draft only on explicit Save/Cancel stop. */
    public static final String EXTRA_CLEAR_DRAFT = "clear_draft";
    public static final String KEY_CLEAR_DRAFT_ON_DESTROY = "clear_draft_on_destroy";
    public static final String KEY_UPDATED_AT = "updated_at";
    public static final String KEY_GEOMETRY_INDEX = "geometry_index";
    public static final String KEY_RING_INDEX = "ring_index";
    public static final String KEY_INSERT_INDEX = "insert_index";

    /**
     * Type-safe extra read (API 33+) — deprecated getSerializableExtra can fail to return geometry.
     */
    public static GeoGeometry readWalkGeometryExtra(Intent intent) {
        if (intent == null)
            return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getSerializableExtra(ConstantsUI.KEY_GEOMETRY, GeoGeometry.class);
        }
        return (GeoGeometry) intent.getSerializableExtra(ConstantsUI.KEY_GEOMETRY);
    }

    private SharedPreferences mSharedPreferencesTemp;
    private GpsEventSource mGpsSource;
//    protected GnssStatus.Callback mGnssCallback;
    private NotificationManager mNotificationManager;
    private String mTicker;
    private int mSmallIcon;
    private PendingIntent mOpenActivity;

    protected String mTargetActivity;
    protected Bundle mTargetExtras;
    protected GeoGeometry mGeometry;
    protected int mLayerId;
    protected long mFeatureId = Constants.NOT_FOUND;
    protected int mGeometryIndex;
    protected int mRingIndex;
    protected int mInsertIndex;
    protected boolean mShowNotification;
    /** When true, onDestroy clears walkedit_temp (explicit Save/Cancel). */
    private boolean mClearDraftOnDestroy;

    public static final String ACTION_RESUME_GPS = "com.nextgis.maplibui.WALKEDIT_RESUME_GPS";
    public static final String KEY_GPS_PAUSED = "gps_paused";
    private LocationRecordingSampler mSampler;
    private int mLastSampledIndex = -1;
    private long mLastRecordingNanos;
    private boolean mGpsPaused;
    private BackgroundRecordingSoundMonitor mRecordingSoundMonitor;
    private String mSessionId;
    private boolean mTerminalHandled;
    private static WalkEditService sActive;
    private final SharedPreferences.OnSharedPreferenceChangeListener mSessionPreferencesListener =
            (preferences, key) -> {
                if (WalkSessionStore.KEY_POINT.equals(key) && mGeometry != null && !mTerminalHandled)
                    addNotification();
            };

    @Override
    public void onCreate() {
        super.onCreate();

        mGpsSource = ((IGISApplication) getApplication()).getGpsEventSource();

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mSharedPreferencesTemp = getSharedPreferences(TEMP_PREFERENCES, MODE_MULTI_PROCESS);
        mSharedPreferencesTemp.registerOnSharedPreferenceChangeListener(mSessionPreferencesListener);
        SharedPreferences defaultPreferences = getSharedPreferences(
                getPackageName() + "_preferences", MODE_MULTI_PROCESS);
        mRecordingSoundMonitor = new BackgroundRecordingSoundMonitor(this, defaultPreferences);

        mTicker = getString(R.string.walkedit_title);
        mSmallIcon = R.drawable.ic_action_maps_directions_walk;

        mLayerId = Constants.NOT_FOUND;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        HyperLog.v(Constants.TAG, "WalkEditService.onStartCommand startId=" + startId);
        WalkSessionStore.Snapshot session = WalkSessionStore.load(this);
        String action = intent != null ? intent.getAction() : null;
        if (session != null) {
            String commandId = intent != null ? intent.getStringExtra(WalkSessionStore.KEY_SESSION) : session.id;
            if (!WalkSessionStore.isCurrentMap(this, session) || !session.id.equals(commandId)
                    || (mSessionId != null && !mSessionId.equals(session.id))) {
                if (mGeometry == null) stopSelf();
                return mGeometry == null ? START_NOT_STICKY : START_STICKY;
            }
            if (ACTION_STOP.equals(action) || ACTION_RESUME_GPS.equals(action) || ACTION_FINISH.equals(action)
                    || intent != null && intent.getBooleanExtra(EXTRA_USER_RESUME, false)) {
                WalkSessionPolicy.Command command = ACTION_STOP.equals(action)
                        ? WalkSessionPolicy.Command.DISCARD : ACTION_FINISH.equals(action)
                        ? WalkSessionPolicy.Command.FINISH : WalkSessionPolicy.Command.RESUME;
                if (!WalkSessionStore.allows(this, commandId, command)) {
                    if (mGeometry == null) stopSelf();
                    return mGeometry == null ? START_NOT_STICKY : START_STICKY;
                }
            } else if (session.phase != WalkSessionPolicy.Phase.RECORDING) {
                // A process killed after Finish was requested must never restart recording.
                if (session.phase == WalkSessionPolicy.Phase.FINISHING)
                    WalkSessionStore.setPhase(this, session.id, WalkSessionPolicy.Phase.FINISHED);
                if (mGeometry == null) stopSelf();
                return START_NOT_STICKY;
            }
            mSessionId = session.id;
            if (ACTION_STOP.equals(action) || ACTION_FINISH.equals(action)) {
                finishOwnedSession(ACTION_STOP.equals(action));
                return mTerminalHandled ? START_NOT_STICKY : START_STICKY;
            }
        } else if (intent != null && intent.hasExtra(WalkSessionStore.KEY_SESSION)) {
            // Late command for an already discarded/saved session.
            if (mGeometry == null) stopSelf();
            return mGeometry == null ? START_NOT_STICKY : START_STICKY;
        }
        if (intent != null) {

            if (action != null && !TextUtils.isEmpty(action)) {
                switch (action) {
                    case ACTION_STOP:
                        // Explicit stop from UI should clear draft; unexpected deaths must not.
                        flushWalkLocationFilterToGeometry();
                        mGpsSource.removeRecordingListener(this);
                        mClearDraftOnDestroy = intent.getBooleanExtra(EXTRA_CLEAR_DRAFT, true);
                        // Write the terminal marker after flush: persisting the final geometry
                        // writes the normal "keep draft" marker.
                        mSharedPreferencesTemp.edit()
                                .putBoolean(KEY_CLEAR_DRAFT_ON_DESTROY, mClearDraftOnDestroy)
                                .commit();
                        mGeometry = null;
                        mLayerId = Constants.NOT_FOUND;
                        mFeatureId = Constants.NOT_FOUND;
                        if (mSampler != null) mSampler.reset();
                        removeNotification();
                        stopSelf();
                        break;
                    case ACTION_RESUME_GPS:
                        // This action explicitly acknowledges the unrecorded connection.
                        if (mGeometry != null && mGpsSource.getLastRecordingLocation() != null) {
                            mGpsPaused = false;
                            mLastRecordingNanos = 0;
                            if (mSampler != null) mSampler.reset();
                            mGpsSource.removeRecordingListener(this);
                            mGpsSource.addRecordingListener(this);
                            reportWalkPersistence(persistWalkGeometryToTempPrefs());
                            sendGeometryBroadcast();
                            addNotification();
                        } else {
                            android.widget.Toast.makeText(this, R.string.walk_gps_wait,
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                        break;
                    case ACTION_START:
                        int layerId = intent.getIntExtra(ConstantsUI.KEY_LAYER_ID, Constants.NOT_FOUND);
                        long featureId = intent.getLongExtra(
                                ConstantsUI.KEY_FEATURE_ID, Constants.NOT_FOUND);
                        if (mLayerId == layerId && mFeatureId == featureId && mGeometry != null) {
                            sendGeometryBroadcast();
                        } else {
                            mLayerId = layerId;
                            mFeatureId = featureId;
                            mGeometryIndex = intent.getIntExtra(KEY_GEOMETRY_INDEX, 0);
                            mRingIndex = intent.getIntExtra(KEY_RING_INDEX, 0);
                            mGeometry = readWalkGeometryExtra(intent);
                            mInsertIndex = intent.hasExtra(KEY_INSERT_INDEX)
                                    ? intent.getIntExtra(KEY_INSERT_INDEX, 0)
                                    : getWalkGeometryVertexCount();
                            normalizeOpenWalkRing();

                            mTargetActivity = intent.getStringExtra(ConstantsUI.TARGET_CLASS);
                            mTargetExtras = intent.getBundleExtra(ConstantsUI.TARGET_EXTRAS);
                            mShowNotification = intent.getBooleanExtra(ConstantsUI.KEY_MESSAGE, true);
                            mClearDraftOnDestroy = false;
                            if (mGeometry == null) {
                                Log.e(Constants.TAG, "WalkEditService: KEY_GEOMETRY missing");
                                initTargetIntent(mTargetActivity);
                                stopWithoutLocationForeground("missing-geometry");
                                return START_NOT_STICKY;
                            }

                            mLastRecordingNanos = 0;
                            mGpsPaused = intent.getBooleanExtra(KEY_GPS_PAUSED, false);
                            if (!startWalkEdit()) {
                                return START_NOT_STICKY;
                            }
                            persistWalkDraftSnapshot(true);
                        }
                        break;
                }
            }
        } else {
            mLayerId = mSharedPreferencesTemp.getInt(ConstantsUI.KEY_LAYER_ID, Constants.NOT_FOUND);
            mFeatureId = mSharedPreferencesTemp.getLong(ConstantsUI.KEY_FEATURE_ID, Constants.NOT_FOUND);
            mGeometryIndex = mSharedPreferencesTemp.getInt(KEY_GEOMETRY_INDEX, 0);
            mRingIndex = mSharedPreferencesTemp.getInt(KEY_RING_INDEX, 0);
            mGeometry = GeoGeometryFactory.fromWKT(mSharedPreferencesTemp.getString(ConstantsUI.KEY_GEOMETRY, ""), GeoConstants.CRS_WEB_MERCATOR);
            mInsertIndex = mSharedPreferencesTemp.contains(KEY_INSERT_INDEX)
                    ? mSharedPreferencesTemp.getInt(KEY_INSERT_INDEX, 0)
                    : getWalkGeometryVertexCount();
            normalizeOpenWalkRing();
            mTargetActivity = mSharedPreferencesTemp.getString(ConstantsUI.TARGET_CLASS, "");
            mTargetExtras = loadBundle(mSharedPreferencesTemp);
            mShowNotification = mSharedPreferencesTemp.getBoolean(ConstantsUI.KEY_MESSAGE, true);
            mClearDraftOnDestroy = false;
            mLastRecordingNanos = 0;
            mGpsPaused = getWalkGeometryVertexCount() > 0;
            if (mGeometry != null && mLayerId != Constants.NOT_FOUND) {
                if (!startWalkEdit()) {
                    return START_NOT_STICKY;
                }
                persistWalkDraftSnapshot(true);
                sendGeometryBroadcast();
            } else {
                HyperLog.w(Constants.TAG, "WalkEditService sticky restart: invalid draft");
                removeNotification();
                stopSelf();
            }
        }

        return START_STICKY;

    }

    private boolean startWalkEdit() {
        SharedPreferences sharedPreferences = getSharedPreferences(getPackageName() + "_preferences", MODE_MULTI_PROCESS);

        String minTimeStr = sharedPreferences.getString(SettingsConstants.KEY_PREF_LOCATION_MIN_TIME, "2");
        String minDistanceStr = sharedPreferences.getString(SettingsConstants.KEY_PREF_LOCATION_MIN_DISTANCE, "5");
        long minTime = Long.parseLong(minTimeStr) * 1000;
        float minDistance = Float.parseFloat(minDistanceStr);

        initTargetIntent(mTargetActivity);

        if (!PermissionUtil.hasLocationPermissions(this)) {
            HyperLog.w(Constants.TAG, "WalkEditService: missing location permission");
            stopWithoutLocationForeground("missing-permission");
            return false;
        }

        mSampler = new LocationRecordingSampler(minTime, minDistance);
        NotificationHelper.showLocationInfo(this);
        if (!addNotification()) return false;
        sActive = this;
        mRecordingSoundMonitor.start();
        mGpsSource.addRecordingListener(this);
        return true;
    }

    /**
     * startForegroundService requires a timely startForeground; stop if we cannot access location.
     */
    private void stopWithoutLocationForeground(String stage) {
        HyperLog.w(Constants.TAG, "WalkEditService stopped without location foreground stage="
                + stage + "; draft retained");
        try {
            stopForeground(true);
        } catch (RuntimeException ex) {
            HyperLog.w(Constants.TAG, "WalkEditService stopForeground stage=" + stage
                    + ": " + ex.getMessage(), ex);
        }
        mNotificationManager.cancel(WALK_NOTIFICATION_ID);
        stopSelf();
    }

    private void sendGeometryBroadcast() {
        if (mSessionId != null) {
            WalkSessionStore.notifyChanged(this);
            return;
        }
        Intent broadcastIntent = new Intent(WALKEDIT_CHANGE);
        broadcastIntent.setPackage(getPackageName());
        broadcastIntent.putExtra(ConstantsUI.KEY_GEOMETRY, mGeometry);
        broadcastIntent.putExtra(KEY_GEOMETRY_INDEX, mGeometryIndex);
        broadcastIntent.putExtra(KEY_RING_INDEX, mRingIndex);
        broadcastIntent.putExtra(KEY_INSERT_INDEX, mInsertIndex);
        broadcastIntent.putExtra(KEY_GPS_PAUSED, mGpsPaused);
        broadcastIntent.setPackage(getApplicationContext().getPackageName());
        sendBroadcast(broadcastIntent);
    }

    private void normalizeOpenWalkRing() {
        if (!(mGeometry instanceof GeoLinearRing))
            return;
        GeoLinearRing ring = (GeoLinearRing) mGeometry;
        if (ring.getPointCount() > 1 && ring.isClosed())
            ring.remove(ring.getPointCount() - 1);
        mInsertIndex = Math.max(0, Math.min(mInsertIndex, ring.getPointCount()));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        HyperLog.v(Constants.TAG, "WalkEditService.onDestroy");
        mSharedPreferencesTemp.unregisterOnSharedPreferenceChangeListener(mSessionPreferencesListener);
        if (mSessionId != null) {
            if (!mTerminalHandled) {
                flushWalkLocationFilterToGeometry();
                mGpsPaused = true;
                if (mGeometry != null) persistWalkGeometryToTempPrefs();
                WalkSessionStore.notifyChanged(this);
            }
            mGpsSource.removeRecordingListener(this);
            if (mRecordingSoundMonitor != null) mRecordingSoundMonitor.release();
            if (sActive == this) sActive = null;
            removeNotification();
            super.onDestroy();
            return;
        }
        try {
            flushWalkLocationFilterToGeometry();
            mGpsSource.removeRecordingListener(this);
        } catch (Exception ex) {
            HyperLog.w(Constants.TAG, "WalkEditService.onDestroy flush: " + ex.getMessage(), ex);
        }
        try {
            removeNotification();
        } catch (Exception ex){
            HyperLog.w(Constants.TAG, "WalkEditService.onDestroy: " + ex.getMessage(), ex);
        }

        boolean clearDraft = mClearDraftOnDestroy
                || mSharedPreferencesTemp.getBoolean(KEY_CLEAR_DRAFT_ON_DESTROY, false);
        if (clearDraft) {
            mSharedPreferencesTemp.edit().clear().commit();
            HyperLog.v(Constants.TAG, "WalkEditService.onDestroy: draft cleared (explicit stop)");
        } else {
            // Unexpected end (process death, FGS kill, permission stop): keep durable draft.
            HyperLog.w(Constants.TAG, "WalkEditService unexpected walk end; keeping walkedit_temp draft");
            if (mGeometry != null) {
                persistWalkDraftSnapshot(true);
            }
            mSharedPreferencesTemp.edit()
                    .putBoolean(KEY_CLEAR_DRAFT_ON_DESTROY, false)
                    .commit();
        }

        mGpsSource.removeRecordingListener(this);

        if (mRecordingSoundMonitor != null)
            mRecordingSoundMonitor.release();

        super.onDestroy();
    }

    private void finishOwnedSession(boolean discard) {
        if (!discard) {
            flushWalkLocationFilterToGeometry();
            if (mGeometry != null && !persistWalkGeometryToTempPrefs()) {
                WalkSessionStore.setPhase(this, mSessionId, WalkSessionPolicy.Phase.RECORDING);
                android.widget.Toast.makeText(this, R.string.walk_save_failed,
                        android.widget.Toast.LENGTH_LONG).show();
                return;
            }
        }
        boolean saved = discard ? WalkSessionStore.clear(this, mSessionId)
                : WalkSessionStore.setPhase(this, mSessionId, WalkSessionPolicy.Phase.FINISHED);
        if (!saved) return;
        mTerminalHandled = true;
        mGpsSource.removeRecordingListener(this);
        if (mSampler != null) mSampler.reset();
        mGeometry = null;
        if (sActive == this) sActive = null;
        removeNotification();
        stopSelf();
    }

    public static boolean isSessionRunning(String id) {
        return sActive != null && !sActive.mTerminalHandled && id != null
                && id.equals(sActive.mSessionId);
    }

    /** Upgrade a live legacy recorder without resetting its GNSS subscription or sampler. */
    public static void adoptLegacySession(String id) {
        WalkEditService service = sActive;
        if (service == null || service.mSessionId != null || service.mGeometry == null) return;
        WalkSessionStore.Snapshot session = WalkSessionStore.load(service);
        if (session == null || !id.equals(session.id) || session.layerId != service.mLayerId
                || session.featureId != service.mFeatureId) return;
        service.mSessionId = id;
        service.persistWalkGeometryToTempPrefs();
        service.addNotification();
    }

    @Override
    public void onRecordingLocation(Location location) {
        if (location == null || mGeometry == null || mSampler == null || mGpsPaused) return;
        long nanos = location.getElapsedRealtimeNanos();
        if (nanos <= mLastRecordingNanos) return;
        if (mLastRecordingNanos > 0
                && (nanos - mLastRecordingNanos) / 1_000_000L > LocationFixPolicy.FRESHNESS_MS) {
            onRecordingUnavailable();
            return;
        }
        mLastRecordingNanos = nanos;
        mRecordingSoundMonitor.onLocationChanged(location);
        List<Location> points = mSampler.onLocation(location);
        Location correction = mSampler.takeStationaryCorrection();
        boolean changed = false;
        for (Location point : points) changed |= appendWalkGeometryPoint(point);
        if (correction != null && mGeometry instanceof GeoLineString) {
            GeoLineString line = (GeoLineString) mGeometry;
            GeoPoint point = new GeoPoint(correction.getLongitude(), correction.getLatitude());
            point.setCRS(GeoConstants.CRS_WGS84);
            point.project(GeoConstants.CRS_WEB_MERCATOR);
            changed |= WalkGeometryInsertion.correct(line, mLastSampledIndex, point);
        }
        if (changed) {
            reportWalkPersistence(persistWalkGeometryToTempPrefs());
            sendGeometryBroadcast();
        }
    }

    @Override
    public void onRecordingUnavailable() {
        mRecordingSoundMonitor.onLocationUnavailable();
        if (mGeometry == null || mGpsPaused || mLastRecordingNanos == 0) return;
        saveSampledPoints(mSampler.flush());
        mGpsPaused = true;
        reportWalkPersistence(persistWalkGeometryToTempPrefs());
        sendGeometryBroadcast();
        addNotification();
    }

    @Override
    public void onRecordingFlushComplete() {
        if (mGeometry != null && mSampler != null && !mGpsPaused)
            saveSampledPoints(mSampler.flush());
    }

    private void saveSampledPoints(List<Location> points) {
        boolean changed = false;
        for (Location point : points) changed |= appendWalkGeometryPoint(point);
        if (changed) {
            reportWalkPersistence(persistWalkGeometryToTempPrefs());
            sendGeometryBroadcast();
        }
    }

    private boolean appendWalkGeometryPoint(Location location) {
        GeoPoint point = new GeoPoint(location.getLongitude(), location.getLatitude());
        point.setCRS(GeoConstants.CRS_WGS84);
        point.project(GeoConstants.CRS_WEB_MERCATOR);

        switch (mGeometry.getType()) {
            case GeoConstants.GTLineString:
                GeoLineString line = (GeoLineString) mGeometry;
                mInsertIndex = WalkGeometryInsertion.insert(line, mInsertIndex, point);
                mLastSampledIndex = mInsertIndex - 1;
                break;
            case GeoConstants.GTLinearRing:
                GeoLinearRing ring = (GeoLinearRing) mGeometry;
                mInsertIndex = WalkGeometryInsertion.insert(ring, mInsertIndex, point);
                mLastSampledIndex = mInsertIndex - 1;
                break;
            default:
                HyperLog.w(Constants.TAG, "WalkEditService: unsupported geometry type "
                        + mGeometry.getType() + ", ignoring location update");
                return false;
        }
        return true;
    }

    private boolean persistWalkGeometryToTempPrefs() {
        return persistWalkDraftSnapshot(false);
    }

    private boolean persistWalkDraftSnapshot(boolean includeMeta) {
        if (mGeometry == null)
            return false;
        if (mSessionId != null) {
            return WalkSessionStore.updateGeometry(this, mSessionId, (GeoLineString) mGeometry,
                    mInsertIndex, mGpsPaused);
        }
        SharedPreferences.Editor edit = mSharedPreferencesTemp.edit();
        edit.putString(ConstantsUI.KEY_GEOMETRY, mGeometry.toWKT(true));
        edit.putLong(KEY_UPDATED_AT, System.currentTimeMillis());
        edit.putBoolean(KEY_CLEAR_DRAFT_ON_DESTROY, false);
        edit.putInt(KEY_GEOMETRY_INDEX, mGeometryIndex);
        edit.putInt(KEY_RING_INDEX, mRingIndex);
        edit.putInt(KEY_INSERT_INDEX, mInsertIndex);
        edit.putBoolean(KEY_GPS_PAUSED, mGpsPaused);
        if (includeMeta) {
            edit.putInt(ConstantsUI.KEY_LAYER_ID, mLayerId);
            edit.putLong(ConstantsUI.KEY_FEATURE_ID, mFeatureId);
            edit.putString(ConstantsUI.TARGET_CLASS, mTargetActivity);
            edit.putBoolean(ConstantsUI.KEY_MESSAGE, mShowNotification);
            saveBundle(edit, mTargetExtras);
        }
        return edit.commit();
    }

    private void reportWalkPersistence(boolean persisted) {
        if (!persisted) {
            HyperLog.w(Constants.TAG, "WalkEditService: failed to persist walk geometry");
            mRecordingSoundMonitor.onPersistenceFailed();
        }
    }

    /** True when walkedit_temp holds a usable interrupted walk draft. */
    public static boolean hasValidDraft(Context context) {
        if (context == null)
            return false;
        SharedPreferences prefs = context.getSharedPreferences(TEMP_PREFERENCES, MODE_MULTI_PROCESS);
        int layerId = prefs.getInt(ConstantsUI.KEY_LAYER_ID, Constants.NOT_FOUND);
        String wkt = prefs.getString(ConstantsUI.KEY_GEOMETRY, null);
        if (layerId == Constants.NOT_FOUND || TextUtils.isEmpty(wkt))
            return false;
        try {
            return GeoGeometryFactory.fromWKT(wkt, GeoConstants.CRS_WEB_MERCATOR) != null;
        } catch (RuntimeException e) {
            HyperLog.w(Constants.TAG, "WalkEditService: corrupt walk draft", e);
            return false;
        }
    }

    public static void clearDraft(Context context) {
        if (context == null)
            return;
        WalkSessionStore.Snapshot session = WalkSessionStore.load(context);
        if (session != null) {
            if (!isSessionRunning(session.id)) WalkSessionStore.clear(context, session.id);
            return;
        }
        context.getSharedPreferences(TEMP_PREFERENCES, MODE_MULTI_PROCESS).edit().clear().commit();
    }

    public static SharedPreferences getDraftPreferences(Context context) {
        return context.getSharedPreferences(TEMP_PREFERENCES, MODE_MULTI_PROCESS);
    }

    /**
     * Restart walk recording from the durable draft (Continue). Does not clear draft.
     */
    public static boolean resumeFromDraft(Context context, String targetActivity) {
        if (context == null || !hasValidDraft(context))
            return false;
        WalkSessionStore.Snapshot session = WalkSessionStore.load(context);
        if (session != null && !WalkSessionStore.allows(context, session.id,
                WalkSessionPolicy.Command.RESUME)) return false;
        if (isServiceRunning(context))
            return true;
        SharedPreferences prefs = getDraftPreferences(context);
        int layerId = prefs.getInt(ConstantsUI.KEY_LAYER_ID, Constants.NOT_FOUND);
        long featureId = prefs.getLong(ConstantsUI.KEY_FEATURE_ID, Constants.NOT_FOUND);
        int geometryIndex = prefs.getInt(KEY_GEOMETRY_INDEX, 0);
        int ringIndex = prefs.getInt(KEY_RING_INDEX, 0);
        GeoGeometry geometry = GeoGeometryFactory.fromWKT(
                prefs.getString(ConstantsUI.KEY_GEOMETRY, ""), GeoConstants.CRS_WEB_MERCATOR);
        if (geometry == null || layerId == Constants.NOT_FOUND)
            return false;
        int insertIndex = prefs.contains(KEY_INSERT_INDEX)
                ? prefs.getInt(KEY_INSERT_INDEX, 0)
                : geometry instanceof GeoLineString
                ? ((GeoLineString) geometry).getPointCount()
                : 0;

        Intent intent = new Intent(context, WalkEditService.class);
        intent.setAction(ACTION_START);
        if (session != null) intent.putExtra(WalkSessionStore.KEY_SESSION, session.id);
        intent.putExtra(ConstantsUI.KEY_LAYER_ID, layerId);
        intent.putExtra(ConstantsUI.KEY_FEATURE_ID, featureId);
        intent.putExtra(KEY_GEOMETRY_INDEX, geometryIndex);
        intent.putExtra(KEY_RING_INDEX, ringIndex);
        intent.putExtra(KEY_INSERT_INDEX, insertIndex);
        // This new-session entry point is called only after explicit Continue/Connect.
        // Sticky system restarts still take the separate paused path in onStartCommand.
        intent.putExtra(KEY_GPS_PAUSED, session == null);
        intent.putExtra(EXTRA_USER_RESUME, session != null);
        intent.putExtra(ConstantsUI.KEY_GEOMETRY, geometry);
        intent.putExtra(ConstantsUI.KEY_MESSAGE, true);
        if (!TextUtils.isEmpty(targetActivity))
            intent.putExtra(ConstantsUI.TARGET_CLASS, targetActivity);
        ContextCompat.startForegroundService(context, intent);
        return true;
    }

    /** Request an explicit stop that clears the durable draft (Save/Cancel). */
    public static void stopAndClearDraft(Context context) {
        if (context == null)
            return;
        WalkSessionStore.Snapshot session = WalkSessionStore.load(context);
        if (session != null) {
            requestCommand(context, session.id, WalkSessionPolicy.Command.DISCARD);
            return;
        }
        if (!isServiceRunning(context)) {
            clearDraft(context);
            return;
        }
        Intent intent = new Intent(context, WalkEditService.class);
        intent.setAction(ACTION_STOP);
        intent.putExtra(EXTRA_CLEAR_DRAFT, true);
        context.startService(intent);
    }

    /**
     * Detach a START_STICKY walk from a cold Activity while preserving its last geometry for the
     * Continue/Discard decision.
     */
    public static void pauseAndKeepDraft(Context context) {
        if (context == null || !isServiceRunning(context))
            return;
        // An independent session belongs to the service, not to the visible map tool.
        if (WalkSessionStore.load(context) != null) return;
        Intent intent = new Intent(context, WalkEditService.class);
        intent.setAction(ACTION_STOP);
        intent.putExtra(EXTRA_CLEAR_DRAFT, false);
        context.startService(intent);
    }

    private void flushWalkLocationFilterToGeometry() {
        if (mGeometry == null || mSampler == null || mGpsPaused) return;
        mGpsSource.flushRecordingLocations();
        saveSampledPoints(mSampler.flush());
    }

    private int getWalkGeometryVertexCount() {
        if (mGeometry == null) {
            return 0;
        }
        switch (mGeometry.getType()) {
            case GeoConstants.GTLineString:
                return ((GeoLineString) mGeometry).getPointCount();
            case GeoConstants.GTLinearRing:
                return ((GeoLinearRing) mGeometry).getPointCount();
            default:
                return 0;
        }
    }

    private boolean addNotification() {
        if (mGpsPaused) {
            Intent resume = new Intent(this, WalkEditService.class).setAction(ACTION_RESUME_GPS);
            if (mSessionId != null) {
                resume.putExtra(WalkSessionStore.KEY_SESSION, mSessionId);
                resume.setData(android.net.Uri.parse("walk-session:" + mSessionId));
            }
            PendingIntent resumeAction = PendingIntent.getService(this, 1, resume,
                    PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
            NotificationCompat.Builder paused = createBuilder(this, R.string.title_edit_by_walk)
                    .setSmallIcon(mSmallIcon)
                    .setContentTitle(getString(R.string.walk_gps_paused))
                    .setContentText(getString(R.string.walk_gps_gap_message))
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(getString(R.string.walk_gps_gap_message)))
                    .setOngoing(true);
            if (!WalkSessionStore.isPointActive(this))
                paused.addAction(R.drawable.ic_location, getString(R.string.walk_gps_resume), resumeAction);
            else paused.setContentText(getString(R.string.walk_controls_point_locked));
            if (mOpenActivity != null) paused.setContentIntent(mOpenActivity);
            return startLocationForegroundSafely(paused.build(), "gps-paused");
        }
        if (!mShowNotification) {
            NotificationCompat.Builder minimal = createBuilder(this, R.string.title_edit_by_walk);
            minimal.setSmallIcon(mSmallIcon)
                    .setContentTitle(getString(R.string.title_edit_by_walk))
                    .setContentText(getString(R.string.title_edit_by_walk))
                    .setWhen(System.currentTimeMillis())
                    .setAutoCancel(false)
                    .setOngoing(true);
            if (mOpenActivity != null)
                minimal.setContentIntent(mOpenActivity);
            return startLocationForegroundSafely(minimal.build(), "minimal-recording");
        }

        MapBase map = MapBase.getInstance();
        ILayer layer = map.getLayerById(mLayerId);
        String name = "";
        if (null != layer)
            name = layer.getName();

        mTicker = String.format(getString(R.string.walkedit_title), name);
        Bitmap largeIcon = NotificationHelper.getLargeIcon(mSmallIcon, getResources());

        NotificationCompat.Builder builder = createBuilder(this, R.string.title_edit_by_walk);

        builder.setContentIntent(mOpenActivity)
               .setSmallIcon(mSmallIcon)
               .setLargeIcon(largeIcon)
               .setTicker(mTicker)
               .setWhen(System.currentTimeMillis())
               .setAutoCancel(false)
               .setContentTitle(mTicker)
               .setContentText(mTicker)
               .setOngoing(true);

        builder.addAction(R.drawable.ic_location, getString(R.string.tracks_open), mOpenActivity);

        mNotificationManager.notify(WALK_NOTIFICATION_ID, builder.build());
        return startLocationForegroundSafely(builder.build(), "recording");
    }

    public static boolean requestCommand(Context context, String id, WalkSessionPolicy.Command command) {
        if (!WalkSessionStore.allows(context, id, command)) return false;
        if (command == WalkSessionPolicy.Command.DISCARD && !isSessionRunning(id))
            return WalkSessionStore.clear(context, id);
        if (command == WalkSessionPolicy.Command.FINISH) {
            if (!WalkSessionStore.setPhase(context, id, WalkSessionPolicy.Phase.FINISHING)) return false;
            if (!isSessionRunning(id))
                return WalkSessionStore.setPhase(context, id, WalkSessionPolicy.Phase.FINISHED);
        }
        if (command == WalkSessionPolicy.Command.RESUME && !isSessionRunning(id)) {
            return resumeFromDraft(context, preferencesActivity(context));
        }
        Intent intent = new Intent(context, WalkEditService.class)
                .setAction(command == WalkSessionPolicy.Command.FINISH ? ACTION_FINISH
                        : command == WalkSessionPolicy.Command.DISCARD ? ACTION_STOP : ACTION_RESUME_GPS)
                .putExtra(WalkSessionStore.KEY_SESSION, id);
        context.startService(intent);
        return true;
    }

    private static String preferencesActivity(Context context) {
        return getDraftPreferences(context).getString(ConstantsUI.TARGET_CLASS, "");
    }

    private boolean startLocationForegroundSafely(
            android.app.Notification notification, String stage) {
        try {
            startForeground(WALK_NOTIFICATION_ID, notification);
            return true;
        } catch (SecurityException ex) {
            HyperLog.w(Constants.TAG, "WalkEditService location foreground rejected stage="
                    + stage + ": " + ex.getMessage(), ex);
            stopWithoutLocationForeground(stage);
            return false;
        }
    }

    private void removeNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            stopForeground(true);
        else
            mNotificationManager.cancel(WALK_NOTIFICATION_ID);
    }

    // intent to open on notification click
    private void initTargetIntent(String targetActivity) {
        Intent intentActivity = new Intent();

        if (!TextUtils.isEmpty(targetActivity)) {
            Class<?> targetClass = null;

            try {
                targetClass = Class.forName(targetActivity);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

            if (targetClass != null) {
                intentActivity = new Intent(this, targetClass);
            }
        }

        intentActivity.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (mTargetExtras != null)
            intentActivity.putExtras(mTargetExtras);
        mOpenActivity = PendingIntent.getActivity(this, 0, intentActivity, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
    }

    /**
     * Manually save a Bundle object to SharedPreferences.
     * http://stackoverflow.com/a/13692248/2088273
     */
    private void saveBundle(SharedPreferences.Editor editor, Bundle bundle) {
        if (bundle == null)
            return;

        for (String key : bundle.keySet()) {
            Object o = bundle.get(key);
            if (o instanceof Integer)
                editor.putInt(EXTRA_HEADER + key, (Integer) o);
            else if (o instanceof Long)
                editor.putLong(EXTRA_HEADER + key, (Long) o);
            else if (o instanceof Boolean)
                editor.putBoolean(EXTRA_HEADER + key, (Boolean) o);
            else if (o instanceof CharSequence)
                editor.putString(EXTRA_HEADER + key, o.toString());
        }

        editor.commit();
    }

    /**
     * Manually load a Bundle from SharedPreferences.
     */
    private Bundle loadBundle(SharedPreferences preferences) {
        Bundle result = new Bundle();
        for (Map.Entry o : preferences.getAll().entrySet()) {
            String key = (String) o.getKey();
            if (key.startsWith(EXTRA_HEADER)) {
                key = key.replace(EXTRA_HEADER, "");
                if (o.getValue() instanceof Integer)
                    result.putInt(key, (Integer) o.getValue());
                else if (o.getValue() instanceof Long)
                    result.putLong(key, (Long) o.getValue());
                else if (o.getValue() instanceof Boolean)
                    result.putBoolean(key, (Boolean) o.getValue());
                else if (o.getValue() instanceof CharSequence)
                    result.putString(key, (String) o.getValue());
            }
        }

        return result;
    }

    public static boolean isServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
            if (WalkEditService.class.getName().equals(service.service.getClassName()))
                return true;

        return false;
    }
}
