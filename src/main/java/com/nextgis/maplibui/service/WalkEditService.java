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
import android.location.LocationListener;
import android.location.LocationManager;
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
import com.nextgis.maplib.util.LocationProviderArbiter;
import com.nextgis.maplib.util.LocationTrackFilter;
import com.nextgis.maplib.util.LocationUtil;
import com.nextgis.maplib.util.PermissionUtil;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.NotificationHelper;

import java.util.List;
import java.util.Map;

import static com.nextgis.maplibui.util.NotificationHelper.createBuilder;

/**
 * Service to gather position data during walking
 */
@SuppressLint("MissingPermission")
public class WalkEditService extends Service implements LocationListener
        //, GpsStatus.Listener
{
    private static final int WALK_NOTIFICATION_ID = 7;
    public static final String TEMP_PREFERENCES = "walkedit_temp";
    public static final String EXTRA_HEADER = "extra_";
    public static final String ACTION_STOP = "com.nextgis.maplibui.WALKEDIT_STOP";
    public static final String ACTION_START = "com.nextgis.maplibui.WALKEDIT_START";
    public static final String WALKEDIT_CHANGE = "com.nextgis.maplibui.WALKEDIT_CHANGE";
    /** Intent extra / prefs: clear durable draft only on explicit Save/Cancel stop. */
    public static final String EXTRA_CLEAR_DRAFT = "clear_draft";
    public static final String KEY_CLEAR_DRAFT_ON_DESTROY = "clear_draft_on_destroy";
    public static final String KEY_UPDATED_AT = "updated_at";
    public static final String KEY_RING_INDEX = "ring_index";

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
    private LocationManager mLocationManager;
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
    protected int mRingIndex;
    protected boolean mShowNotification;
    /** When true, onDestroy clears walkedit_temp (explicit Save/Cancel). */
    private boolean mClearDraftOnDestroy;

    private LocationTrackFilter mWalkLocationFilter;
    private LocationProviderArbiter mWalkProviderArbiter;
    /** Last raw fix after provider gate; used for closing snap when {@code min_dt} dropped it. */
    private Location mLastWalkLocationRaw;
    /** Wall time when the last vertex was appended (flush or live); for closing motion bound. */
    private long mLastVertexWallTimeMs;

    private static final float CLOSING_SNAP_MIN_DIST_M = 0.12f;
    private static final float CLOSING_REF_ACCURACY_M = 25f;
    /** Closing gap: motion bound never below this (covers minDistance 5m × several pending fixes). */
    private static final double CLOSING_SNAP_MIN_MAX_DIST_M = 45.0;

    @Override
    public void onCreate() {
        super.onCreate();

        mWalkLocationFilter = new LocationTrackFilter();
        mWalkProviderArbiter = new LocationProviderArbiter();

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mSharedPreferencesTemp = getSharedPreferences(TEMP_PREFERENCES, MODE_MULTI_PROCESS);

        mTicker = getString(R.string.walkedit_title);
        mSmallIcon = R.drawable.ic_action_maps_directions_walk;

        mLayerId = Constants.NOT_FOUND;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            mGnssCallback = new GnssStatus.Callback() {
//                @Override
//                public void onSatelliteStatusChanged(GnssStatus status) {
//                    super.onSatelliteStatusChanged(status);
//                }
//                @Override                public void onStarted() {                }
//
//                @Override                public void onStopped() {                }
//
//                @Override                public void onFirstFix(int ttffMillis) {                }
//            };
//        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        HyperLog.v(Constants.TAG, "WalkEditService.onStartCommand startId=" + startId);
        if (intent != null) {
            String action = intent.getAction();

            if (action != null && !TextUtils.isEmpty(action)) {
                switch (action) {
                    case ACTION_STOP:
                        // Explicit stop from UI should clear draft; unexpected deaths must not.
                        flushWalkLocationFilterToGeometry();
                        logWalkFilterStats("explicit-stop");
                        mClearDraftOnDestroy = intent.getBooleanExtra(EXTRA_CLEAR_DRAFT, true);
                        // Write the terminal marker after flush: persisting the final geometry
                        // writes the normal "keep draft" marker.
                        mSharedPreferencesTemp.edit()
                                .putBoolean(KEY_CLEAR_DRAFT_ON_DESTROY, mClearDraftOnDestroy)
                                .commit();
                        mGeometry = null;
                        mLayerId = Constants.NOT_FOUND;
                        mFeatureId = Constants.NOT_FOUND;
                        mWalkLocationFilter.reset();
                        mWalkProviderArbiter.reset();
                        removeNotification();
                        stopSelf();
                        break;
                    case ACTION_START:
                        int layerId = intent.getIntExtra(ConstantsUI.KEY_LAYER_ID, Constants.NOT_FOUND);
                        if (mLayerId == layerId && mGeometry != null) { // already recording this layer
                            sendGeometryBroadcast();
                        } else {
                            mLayerId = layerId;
                            mFeatureId = intent.getLongExtra(ConstantsUI.KEY_FEATURE_ID, Constants.NOT_FOUND);
                            mRingIndex = intent.getIntExtra(KEY_RING_INDEX, 0);
                            mGeometry = readWalkGeometryExtra(intent);
                            if (mGeometry instanceof GeoLinearRing) {
                                GeoLinearRing ring = (GeoLinearRing) mGeometry;
                                if (ring.isClosed())
                                    ring.remove(ring.getPointCount() - 1);
                            }

                            mTargetActivity = intent.getStringExtra(ConstantsUI.TARGET_CLASS);
                            mTargetExtras = intent.getBundleExtra(ConstantsUI.TARGET_EXTRAS);
                            mShowNotification = intent.getBooleanExtra(ConstantsUI.KEY_MESSAGE, true);
                            mClearDraftOnDestroy = false;
                            if (mGeometry == null) {
                                Log.e(Constants.TAG, "WalkEditService: KEY_GEOMETRY missing");
                                initTargetIntent(mTargetActivity);
                                foregroundStopMissingLocationPermission();
                                break;
                            }

                            mWalkLocationFilter.reset();
                            mWalkProviderArbiter.reset();
                            mLastWalkLocationRaw = null;
                            mLastVertexWallTimeMs = 0L;
                            startWalkEdit();
                            persistWalkDraftSnapshot(true);
                        }
                        break;
                }
            }
        } else {
            mLayerId = mSharedPreferencesTemp.getInt(ConstantsUI.KEY_LAYER_ID, Constants.NOT_FOUND);
            mFeatureId = mSharedPreferencesTemp.getLong(ConstantsUI.KEY_FEATURE_ID, Constants.NOT_FOUND);
            mRingIndex = mSharedPreferencesTemp.getInt(KEY_RING_INDEX, 0);
            mGeometry = GeoGeometryFactory.fromWKT(mSharedPreferencesTemp.getString(ConstantsUI.KEY_GEOMETRY, ""), GeoConstants.CRS_WEB_MERCATOR);
            mTargetActivity = mSharedPreferencesTemp.getString(ConstantsUI.TARGET_CLASS, "");
            mTargetExtras = loadBundle(mSharedPreferencesTemp);
            mShowNotification = mSharedPreferencesTemp.getBoolean(ConstantsUI.KEY_MESSAGE, true);
            mClearDraftOnDestroy = false;
            mWalkLocationFilter.reset();
            mWalkProviderArbiter.reset();
            mLastWalkLocationRaw = null;
            mLastVertexWallTimeMs = 0L;
            if (mGeometry != null && mLayerId != Constants.NOT_FOUND) {
                startWalkEdit();
            } else {
                HyperLog.w(Constants.TAG, "WalkEditService sticky restart: invalid draft");
                removeNotification();
                stopSelf();
            }
        }

        return START_STICKY;

    }

    private void startWalkEdit() {
        SharedPreferences sharedPreferences = getSharedPreferences(getPackageName() + "_preferences", MODE_MULTI_PROCESS);

        String minTimeStr = sharedPreferences.getString(SettingsConstants.KEY_PREF_LOCATION_MIN_TIME, "2");
        String minDistanceStr = sharedPreferences.getString(SettingsConstants.KEY_PREF_LOCATION_MIN_DISTANCE, "5");
        long minTime = Long.parseLong(minTimeStr) * 1000;
        float minDistance = Float.parseFloat(minDistanceStr);

        initTargetIntent(mTargetActivity);

        if (!PermissionUtil.hasLocationPermissions(this)) {
            foregroundStopMissingLocationPermission();
            return;
        }

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            mLocationManager.registerGnssStatusCallback(mGnssCallback);
//        }else
//            mLocationManager.addGpsStatusListener(this);

        String provider = LocationManager.GPS_PROVIDER;
        if (mLocationManager.getAllProviders().contains(provider)
                && isProviderAllowedForWalk(provider)) {
            mLocationManager.requestLocationUpdates(provider, minTime, minDistance, this);
            HyperLog.v(Constants.TAG, "WalkEditService request location updates provider="
                    + provider + " minTimeMs=" + minTime + " minDistanceM=" + minDistance);
        }

        provider = LocationManager.NETWORK_PROVIDER;
        if (mLocationManager.getAllProviders().contains(provider)
                && isProviderAllowedForWalk(provider)) {
            mLocationManager.requestLocationUpdates(provider, minTime, minDistance, this);
            HyperLog.v(Constants.TAG, "WalkEditService request location updates provider="
                    + provider + " minTimeMs=" + minTime + " minDistanceM=" + minDistance);
        }

        NotificationHelper.showLocationInfo(this);
        addNotification();
    }

    /**
     * startForegroundService requires a timely startForeground; stop if we cannot access location.
     */
    private void foregroundStopMissingLocationPermission() {
        NotificationCompat.Builder builder = createBuilder(this, R.string.title_edit_by_walk);
        builder.setSmallIcon(mSmallIcon)
                .setContentTitle(getString(R.string.title_edit_by_walk))
                .setContentText(getString(R.string.error_no_location))
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setOngoing(false);
        if (mOpenActivity != null)
            builder.setContentIntent(mOpenActivity);
        startForeground(WALK_NOTIFICATION_ID, builder.build());
        stopSelf();
    }

    private void sendGeometryBroadcast() {
        Intent broadcastIntent = new Intent(WALKEDIT_CHANGE);
        broadcastIntent.setPackage(getPackageName());
        broadcastIntent.putExtra(ConstantsUI.KEY_GEOMETRY, mGeometry);
        broadcastIntent.setPackage(getApplicationContext().getPackageName());
        sendBroadcast(broadcastIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        HyperLog.v(Constants.TAG, "WalkEditService.onDestroy");
        try {
            flushWalkLocationFilterToGeometry();
            logWalkFilterStats("destroy");
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

        if (PermissionUtil.hasLocationPermissions(this)) {
            mLocationManager.removeUpdates(this);
        }

        super.onDestroy();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null || mGeometry == null)
            return;

        if (!isProviderAllowedForWalk(location.getProvider())) {
            HyperLog.d(Constants.TAG, "WalkEditService.onLocationChanged ignored provider="
                    + location.getProvider());
            return;
        }
        if (!mWalkProviderArbiter.shouldProcess(location)) {
            HyperLog.d(Constants.TAG,
                    "WalkEditService.onLocationChanged suppressed network fix after usable GPS");
            return;
        }

        mLastWalkLocationRaw = new Location(location);

        long passedBefore = mWalkLocationFilter.getPassedInputFixCount();
        List<Location> accepted = mWalkLocationFilter.onLocation(location);
        if (mWalkLocationFilter.getPassedInputFixCount() > passedBefore) {
            mWalkProviderArbiter.onAccepted(location);
        }
        boolean changed = false;
        for (Location loc : accepted) {
            appendWalkGeometryPoint(loc);
            changed = true;
        }
        if (changed) {
            persistWalkGeometryToTempPrefs();
            sendGeometryBroadcast();
        }
    }

    private boolean isProviderAllowedForWalk(String provider) {
        // Walk digitizing uses the ordinary location source and its min time/distance settings.
        return LocationUtil.isProviderEnabled(this, provider, false);
    }

    private void logWalkFilterStats(String reason) {
        if (mWalkLocationFilter == null) {
            return;
        }
        HyperLog.v(Constants.TAG, "WalkEditService filter stats reason=" + reason
                + " input=" + mWalkLocationFilter.getInputFixCount()
                + " passed=" + mWalkLocationFilter.getPassedInputFixCount()
                + " dropped=" + mWalkLocationFilter.getDroppedInputFixCount()
                + " chordDropped=" + mWalkLocationFilter.getChordDroppedFixCount()
                + " buffered=" + mWalkLocationFilter.getBufferedFixCount()
                + " gaps=" + mWalkLocationFilter.getGapSegmentCount()
                + " networkSuppressed="
                + mWalkProviderArbiter.getSuppressedNetworkFixCount());
    }

    private void appendWalkGeometryPoint(Location location) {
        GeoPoint point = new GeoPoint(location.getLongitude(), location.getLatitude());
        point.setCRS(GeoConstants.CRS_WGS84);
        point.project(GeoConstants.CRS_WEB_MERCATOR);

        switch (mGeometry.getType()) {
            case GeoConstants.GTLineString:
                GeoLineString line = (GeoLineString) mGeometry;
                line.add(point);
                break;
            case GeoConstants.GTLinearRing:
                GeoLinearRing ring = (GeoLinearRing) mGeometry;
                ring.add(point);
                break;
            default:
                HyperLog.w(Constants.TAG, "WalkEditService: unsupported geometry type "
                        + mGeometry.getType() + ", ignoring location update");
                return;
        }
        mLastVertexWallTimeMs = System.currentTimeMillis();
    }

    private void persistWalkGeometryToTempPrefs() {
        persistWalkDraftSnapshot(false);
    }

    private void persistWalkDraftSnapshot(boolean includeMeta) {
        if (mGeometry == null)
            return;
        SharedPreferences.Editor edit = mSharedPreferencesTemp.edit();
        edit.putString(ConstantsUI.KEY_GEOMETRY, mGeometry.toWKT(true));
        edit.putLong(KEY_UPDATED_AT, System.currentTimeMillis());
        edit.putBoolean(KEY_CLEAR_DRAFT_ON_DESTROY, false);
        if (includeMeta) {
            edit.putInt(ConstantsUI.KEY_LAYER_ID, mLayerId);
            edit.putLong(ConstantsUI.KEY_FEATURE_ID, mFeatureId);
            edit.putInt(KEY_RING_INDEX, mRingIndex);
            edit.putString(ConstantsUI.TARGET_CLASS, mTargetActivity);
            edit.putBoolean(ConstantsUI.KEY_MESSAGE, mShowNotification);
            saveBundle(edit, mTargetExtras);
        }
        edit.commit();
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
        if (isServiceRunning(context))
            return true;
        SharedPreferences prefs = getDraftPreferences(context);
        int layerId = prefs.getInt(ConstantsUI.KEY_LAYER_ID, Constants.NOT_FOUND);
        long featureId = prefs.getLong(ConstantsUI.KEY_FEATURE_ID, Constants.NOT_FOUND);
        int ringIndex = prefs.getInt(KEY_RING_INDEX, 0);
        GeoGeometry geometry = GeoGeometryFactory.fromWKT(
                prefs.getString(ConstantsUI.KEY_GEOMETRY, ""), GeoConstants.CRS_WEB_MERCATOR);
        if (geometry == null || layerId == Constants.NOT_FOUND)
            return false;

        Intent intent = new Intent(context, WalkEditService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(ConstantsUI.KEY_LAYER_ID, layerId);
        intent.putExtra(ConstantsUI.KEY_FEATURE_ID, featureId);
        intent.putExtra(KEY_RING_INDEX, ringIndex);
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
        Intent intent = new Intent(context, WalkEditService.class);
        intent.setAction(ACTION_STOP);
        intent.putExtra(EXTRA_CLEAR_DRAFT, false);
        context.startService(intent);
    }

    private void flushWalkLocationFilterToGeometry() {
        if (mWalkLocationFilter == null || mGeometry == null)
            return;
        boolean changed = false;
        for (Location loc : mWalkLocationFilter.flushRemaining()) {
            appendWalkGeometryPoint(loc);
            changed = true;
        }
        if (appendClosingWalkSnapIfNeeded(pickBestClosingLocation())) {
            changed = true;
        }
        if (changed) {
            persistWalkGeometryToTempPrefs();
            sendGeometryBroadcast();
        }
    }

    /**
     * Freshest fix between this service's last callback and app {@link com.nextgis.maplib.location.GpsEventSource}
     * (updates more often than {@code requestLocationUpdates} minDistance).
     */
    private Location pickBestClosingLocation() {
        Location a = mLastWalkLocationRaw;
        Location b = null;
        try {
            Context appCtx = getApplicationContext();
            if (appCtx instanceof IGISApplication) {
                b = ((IGISApplication) appCtx).getGpsEventSource().getLastKnownLocation();
            }
        } catch (Exception ignored) {
        }
        return fresherLocation(a, b);
    }

    private static Location fresherLocation(Location a, Location b) {
        if (a == null) {
            return b != null ? new Location(b) : null;
        }
        if (b == null) {
            return new Location(a);
        }
        long fa = locationFixMonotonicNanos(a);
        long fb = locationFixMonotonicNanos(b);
        return fb >= fa ? new Location(b) : new Location(a);
    }

    private static long locationFixMonotonicNanos(Location loc) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            long n = loc.getElapsedRealtimeNanos();
            if (n > 0L) {
                return n;
            }
        }
        return loc.getTime() * 1_000_000L;
    }

    /**
     * Append the last raw fix if it never entered the filter (e.g. {@code min_dt}) but is still plausible.
     */
    private boolean appendClosingWalkSnapIfNeeded(Location lastRaw) {
        if (lastRaw == null || mGeometry == null) {
            return false;
        }
        if (!LocationTrackFilter.passesBasicIntegrity(lastRaw)) {
            return false;
        }
        int n = getWalkGeometryVertexCount();
        if (n <= 0) {
            appendWalkGeometryPoint(lastRaw);
            return true;
        }
        Location refLoc = buildLocationFromLastVertex();
        if (refLoc == null) {
            return false;
        }
        float dist = refLoc.distanceTo(lastRaw);
        if (dist < CLOSING_SNAP_MIN_DIST_M) {
            return false;
        }
        long dtMs = System.currentTimeMillis() - mLastVertexWallTimeMs;
        if (dtMs < 1L) {
            dtMs = 1L;
        }
        double dtSec = dtMs / 1000d;
        double maxDist = LocationTrackFilter.DEFAULT_MAX_SPEED_MPS * dtSec
                + LocationTrackFilter.DEFAULT_ACCURACY_MARGIN_K
                * (CLOSING_REF_ACCURACY_M + lastRaw.getAccuracy());
        maxDist = Math.max(maxDist, CLOSING_SNAP_MIN_MAX_DIST_M);
        if (dist > maxDist) {
            return false;
        }
        appendWalkGeometryPoint(lastRaw);
        return true;
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

    private Location buildLocationFromLastVertex() {
        GeoPoint p = null;
        switch (mGeometry.getType()) {
            case GeoConstants.GTLineString: {
                GeoLineString line = (GeoLineString) mGeometry;
                int c = line.getPointCount();
                if (c < 1) {
                    return null;
                }
                p = line.getPoint(c - 1);
                break;
            }
            case GeoConstants.GTLinearRing: {
                GeoLinearRing ring = (GeoLinearRing) mGeometry;
                int c = ring.getPointCount();
                if (c < 1) {
                    return null;
                }
                p = ring.getPoint(c - 1);
                break;
            }
            default:
                return null;
        }
        GeoPoint wgs = (GeoPoint) p.copy();
        wgs.project(GeoConstants.CRS_WGS84);
        Location l = new Location("walk_vertex");
        l.setLatitude(wgs.getY());
        l.setLongitude(wgs.getX());
        l.setAccuracy(CLOSING_REF_ACCURACY_M);
        return l;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

//    @Override
//    public void onGpsStatusChanged(int event) {
//    }

    private void addNotification() {
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
            startForeground(WALK_NOTIFICATION_ID, minimal.build());
            return;
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
        startForeground(WALK_NOTIFICATION_ID, builder.build());
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
