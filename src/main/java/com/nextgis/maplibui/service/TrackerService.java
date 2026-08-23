/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2015-2021 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.maplibui.service;

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.location.GnssStatus;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.map.TrackLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.HttpResponse;
import com.nextgis.maplib.util.LocationProviderArbiter;
import com.nextgis.maplib.util.LocationTrackFilter;
import com.nextgis.maplib.util.LocationUtil;
import com.nextgis.maplib.util.MapUtil;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.maplib.util.PermissionUtil;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.BackgroundRecordingSoundMonitor;
import com.nextgis.maplibui.util.NotificationHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import static com.nextgis.maplibui.util.ConstantsUI.VALUE_TRACK_START;
import static com.nextgis.maplibui.util.ConstantsUI.VALUE_TRACK_POINT;
import static com.nextgis.maplibui.util.ConstantsUI.VALUE_TRACK_STOP;
import static com.nextgis.maplibui.util.NotificationHelper.createBuilder;

@SuppressLint("MissingPermission")
public class TrackerService extends Service
        implements LocationListener, GpsStatus.Listener {

    public final static int PERMISSIONS_REQUEST_ZERO_LOCATION_POSPONDED = 777;
    public final static int LOCATION_BACKGROUND_REQUEST = 5;

    public static final  String TEMP_PREFERENCES      = "tracks_temp";
    private static final String TRACK_URI             = "track_uri";
    public static final String ACTION_SYNC            = "com.nextgis.maplibui.TRACK_SYNC";
    public static final String ACTION_STOP            = "com.nextgis.maplibui.TRACK_STOP";
    private static final String ACTION_SPLIT          = "com.nextgis.maplibui.TRACK_SPLIT";
    private static final int    TRACK_NOTIFICATION_ID = 1;
//    public static final String HOST = "http://dev.nextgis.com/tracker-dev1-hub";
    public static final String HOST = "https://track.nextgis.com";
    public static final String URL = "/ng-mobile";
    private static final float CLOSING_SNAP_MIN_DIST_M = 0.12f;
    private static final float CLOSING_REF_ACCURACY_M = 25f;
    private static final double CLOSING_SNAP_MIN_MAX_DIST_M = 45.0;

    private boolean         mIsRunning;
    private LocationManager mLocationManager;

    protected GnssStatus.Callback mGnssCallback;

    private Thread mLocationSenderThread;

    private SharedPreferences mSharedPreferencesTemp;
    private SharedPreferences mSharedPreferences;
    private String            mTrackId;
    private Uri mContentUriTracks, mContentUriTrackPoints;
    private ContentValues mValues;
    private GeoPoint mPoint;

    private NotificationManager mNotificationManager;
    private AlarmManager        mAlarmManager;
    private PendingIntent       mSplitService, mOpenActivity;
    private String              mTicker;
    private int                 mSmallIcon, mSatellitesCount;
    private Bitmap              mLargeIcon;
    private boolean             mHasGPSFix;
    int counter = 0;

    private LocationTrackFilter mTrackLocationFilter;
    private LocationProviderArbiter mTrackProviderArbiter;
    private Location mLastTrackLocationRaw;
    private Location mLastInsertedTrackLocation;
    private boolean mStopBroadcastSent;
    private long mRawFixCount;
    private long mAcceptedFixCount;
    private long mBufferedOrDroppedFixCount;
    private long mInsertedPointCount;
    private long mInsertFailCount;
    private BackgroundRecordingSoundMonitor mRecordingSoundMonitor;

    @Override
    public void onCreate() {
        super.onCreate();
        HyperLog.v(Constants.TAG, "TrackerService.onCreate");

        mHasGPSFix = false;

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        IGISApplication application = (IGISApplication) getApplication();
        String authority = application.getAuthority();
        String tracks = TrackLayer.TABLE_TRACKS;
        mContentUriTracks = Uri.parse("content://" + authority + "/" + tracks);
        String points = TrackLayer.TABLE_TRACKPOINTS;
        mContentUriTrackPoints = Uri.parse("content://" + authority + "/" + points);

        mPoint = new GeoPoint();
        mValues = new ContentValues();
        mTrackLocationFilter = new LocationTrackFilter();
        mTrackProviderArbiter = new LocationProviderArbiter();

        String name = getPackageName() + "_preferences";
        mSharedPreferences = getSharedPreferences(name, MODE_MULTI_PROCESS);
        mSharedPreferencesTemp = getSharedPreferences(TEMP_PREFERENCES, MODE_PRIVATE);
        mRecordingSoundMonitor = new BackgroundRecordingSoundMonitor(this, mSharedPreferences);

        mTicker = getString(R.string.tracks_running);
        mSmallIcon = R.drawable.ic_action_maps_directions_walk;
        mLargeIcon = NotificationHelper.getLargeIcon(mSmallIcon, getResources());

        Intent intentSplit = new Intent(this, TrackerService.class);
        intentSplit.setAction(ACTION_SPLIT);
        int flag = PendingIntent.FLAG_UPDATE_CURRENT  | FLAG_IMMUTABLE;
        mSplitService = PendingIntent.getService(this, 0, intentSplit, flag);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            mGnssCallback = new GnssStatus.Callback() {
                @Override
                public void onStarted() {
                    mHasGPSFix = false;
                }

                @Override
                public void onStopped() {
                    mHasGPSFix = false;
                }

                @Override
                public void onFirstFix(int ttffMillis) {
                    mHasGPSFix = true;
                }

                @Override
                public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                    mSatellitesCount = 0;
                    mSatellitesCount =status.getSatelliteCount();
                //                    for (GpsSatellite sat : mLocationManager.getGpsStatus(null).getSatellites()) {
//                        if (sat.usedInFix()) {
//                            mSatellitesCount++;
//                        }
//                    }
                }

            };
        }
    }


    public static Pair<Integer, Integer> start_stop_tracking_GetIconWithTitle(Context context) {
        Intent trackerServiceIntent = new Intent(context, TrackerService.class);
        trackerServiceIntent.putExtra(ConstantsUI.TARGET_CLASS, context.getClass().getName());

        int title = R.string.track_start, icon = R.drawable.ic_action_maps_directions_walk;
        if (isTrackRecordingEnabled(context)) {
            /*
             * The durable user intent is authoritative.  The service may be temporarily dead
             * after a crash or because permission was revoked; pressing the menu's Stop item
             * must still stop, not accidentally start a new track.
             */
            setTrackRecordingEnabled(context, false);
            if (isTrackerServiceRunning(context)) {
                trackerServiceIntent.setAction(TrackerService.ACTION_STOP);
                context.startService(trackerServiceIntent);
            } else if (hasUnfinishedTracks(context)) {
                closeUnfinishedTracksBeforeRestart(context);
            }
        } else if (hasUnfinishedTracks(context)) {
            // Crash recovery path: keep points, close unfinished session, start a new track.
            closeUnfinishedTracksBeforeRestart(context);
            setTrackRecordingEnabled(context, true);
            ContextCompat.startForegroundService(context, trackerServiceIntent);
            title = R.string.track_stop;
            icon = R.drawable.ic_action_maps_directions_walk_rec;
        } else {
            setTrackRecordingEnabled(context, true);
            ContextCompat.startForegroundService(context, trackerServiceIntent);
            title = R.string.track_stop;
            icon = R.drawable.ic_action_maps_directions_walk_rec;
        }
        return new Pair<>(icon, title);
    }

    /**
     * Whether the user has an active track-recording session that must survive crashes/reboots
     * until they explicitly finish recording from the menu.
     */
    public static boolean isTrackRecordingEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(SettingsConstants.KEY_PREF_TRACK_RECORDING_ENABLED, false);
    }

    public static void setTrackRecordingEnabled(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(SettingsConstants.KEY_PREF_TRACK_RECORDING_ENABLED, enabled)
                .commit();
    }

    /**
     * Start foreground track recording if the durable recording flag is set and the service is
     * not already running. Used after reboot and cold app start. Closing unfinished tracks and
     * opening a new track id is allowed; points already in SQLite are preserved.
     */
    public static void ensureRecordingRunningIfEnabled(Context context) {
        if (context == null) {
            return;
        }
        // Migrate pre-flag sessions: unfinished open track implies recording was on.
        if (!isTrackRecordingEnabled(context) && hasUnfinishedTracks(context)) {
            setTrackRecordingEnabled(context, true);
        }
        if (!isTrackRecordingEnabled(context)) {
            return;
        }
        if (isTrackerServiceRunning(context)) {
            return;
        }
        if (!PermissionUtil.hasLocationPermissions(context)) {
            HyperLog.w(Constants.TAG, "TrackerService.ensureRecordingRunningIfEnabled: no location permission");
            return;
        }
        if (hasUnfinishedTracks(context)) {
            closeUnfinishedTracksBeforeRestart(context);
        }
        Intent trackerService = new Intent(context, TrackerService.class);
        trackerService.putExtra(ConstantsUI.TARGET_CLASS, context.getClass().getName());
        ContextCompat.startForegroundService(context, trackerService);
        HyperLog.v(Constants.TAG, "TrackerService.ensureRecordingRunningIfEnabled: started");
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String targetActivity = "";
        String actionForLog = intent != null ? intent.getAction() : "null";
        HyperLog.v(Constants.TAG, "TrackerService.onStartCommand startId=" + startId
                + " action=" + actionForLog + " running=" + mIsRunning);

        if (intent != null) {
            targetActivity = intent.getStringExtra(ConstantsUI.TARGET_CLASS);
            String action = intent.getAction();

            if (action != null && !TextUtils.isEmpty(action)) {
                switch (action) {
                    case ACTION_SYNC:
                        if (mIsRunning || mLocationSenderThread != null)
                            return START_STICKY;

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            int res = com.nextgis.maplib.R.string.sync_started;
                            String title = getString(res);
                            NotificationCompat.Builder builder = createBuilder(this, res);
                            builder.setSmallIcon(mSmallIcon)
                                    .setLargeIcon(mLargeIcon)
                                    .setTicker(title)
                                    .setWhen(System.currentTimeMillis())
                                    .setAutoCancel(false)
                                    .setContentTitle(title)
                                    .setContentText(title)
                                    .setOngoing(true);

                            startForeground(TRACK_NOTIFICATION_ID, builder.build());
                        }

                        mLocationSenderThread = createLocationSenderThread(500L);
                        mLocationSenderThread.start();
                        return START_NOT_STICKY;
                    case ACTION_STOP:
                        stopTrack("ACTION_STOP");
                        removeNotification();
                        stopSelf();
                        return START_NOT_STICKY;
                    case ACTION_SPLIT:
                        HyperLog.v(Constants.TAG, "TrackerService.ACTION_SPLIT trackId=" + mTrackId);
                        stopTrack("ACTION_SPLIT");
                        initTargetIntent(targetActivity);
                        addStartingNotification();
                        if (!startTrack()) {
                            removeNotification();
                            stopSelf();
                            return START_NOT_STICKY;
                        }
                        addNotification();
                        return START_STICKY;
                }
            }
        }

        if (!mIsRunning) {
            if (!PermissionUtil.hasLocationPermissions(this)) {
                HyperLog.w(Constants.TAG, "TrackerService: missing location permission, stop startId=" + startId);
                foregroundStopMissingLocationPermission();
                stopSelf();
                return START_NOT_STICKY;
            }

            initTargetIntent(targetActivity);
            addStartingNotification();

            registerGpsStatusListenerSafely();

            String time = SettingsConstants.KEY_PREF_TRACKS_MIN_TIME;
            String distance = SettingsConstants.KEY_PREF_TRACKS_MIN_DISTANCE;
            String minTimeStr = mSharedPreferences.getString(time, "5");

            // remove code after   1 - 2 year - after all old vesions gone
            if (minTimeStr.equals("0") || minTimeStr.equals("1")){
                mSharedPreferences.edit().putString(time, "2").apply();
                minTimeStr = "2";
            }
            String minDistanceStr = mSharedPreferences.getString(distance, "5");
            long minTime = Long.parseLong(minTimeStr) * 1000;
            float minDistance = Float.parseFloat(minDistanceStr);

            String provider = LocationManager.GPS_PROVIDER;
            requestTrackLocationUpdates(provider, minTime, minDistance);

            provider = LocationManager.NETWORK_PROVIDER;
            requestTrackLocationUpdates(provider, minTime, minDistance);

            NotificationHelper.showLocationInfo(this);

            // there are no tracks or last track correctly ended
            if (mSharedPreferencesTemp.getString(TRACK_URI, null) == null || !restoreData()) {
                if (!startTrack()) {
                    removeNotification();
                    stopSelf();
                    return START_NOT_STICKY;
                }
                mSharedPreferencesTemp.edit().putString(ConstantsUI.TARGET_CLASS, targetActivity).apply();
            } else {
                targetActivity = mSharedPreferencesTemp.getString(ConstantsUI.TARGET_CLASS, "");
            }

            mLocationSenderThread = createLocationSenderThread(minTime);
            mLocationSenderThread.start();

            addNotification();
        }

        return START_STICKY;
    }


    private boolean restoreData() {
        String trackUriString = mSharedPreferencesTemp.getString(TRACK_URI, null);
        if (TextUtils.isEmpty(trackUriString))
            return false;

        Uri mNewTrack = Uri.parse(trackUriString);
        String trackId = mNewTrack.getLastPathSegment();
        if (TextUtils.isEmpty(trackId) || !isUnfinishedTrack(trackId)) {
            HyperLog.w(Constants.TAG, "TrackerService.restoreData skipped stale trackUri="
                    + trackUriString + " trackId=" + trackId);
            clearTempTrackState();
            return false;
        }

        mTrackId = trackId;
        mIsRunning = true;
        mStopBroadcastSent = false;
        setTrackRecordingEnabled(this, true);
        HyperLog.v(Constants.TAG, "TrackerService.restoreData trackId=" + mTrackId);
        addSplitter();
        sendTrackStartBroadcast(checkIsBatteryPermOK(this));
        ((GISApplication)getApplication()).setIsTrackInProgress(true);
        return true;
    }


    private boolean startTrack() {
        mTrackLocationFilter.reset();
        mTrackProviderArbiter.reset();
        mLastTrackLocationRaw = null;
        mLastInsertedTrackLocation = null;
        mStopBroadcastSent = false;
        mRawFixCount = 0L;
        mAcceptedFixCount = 0L;
        mBufferedOrDroppedFixCount = 0L;
        mInsertedPointCount = 0L;
        mInsertFailCount = 0L;

        // get track name date unique appendix
        String pattern = "yyyy-MM-dd--HH-mm-ss";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern, Locale.getDefault());

        // insert DB row
        final long started = System.currentTimeMillis();
        String mTrackName = simpleDateFormat.format(started);
        mValues.clear();
        mValues.put(TrackLayer.FIELD_NAME, mTrackName);
        mValues.put(TrackLayer.FIELD_START, started);
        mValues.put(TrackLayer.FIELD_VISIBLE, true);

        try {
            Uri newTrack = getContentResolver().insert(mContentUriTracks, mValues);
            if (null != newTrack) {
                // save vars
                mTrackId = newTrack.getLastPathSegment();
                mSharedPreferencesTemp.edit().putString(TRACK_URI, newTrack.toString()).commit();
                setTrackRecordingEnabled(this, true);
                HyperLog.v(Constants.TAG, "TrackerService.startTrack trackId=" + mTrackId
                        + " name=" + mTrackName);
            } else {
                HyperLog.w(Constants.TAG, "TrackerService.startTrack failed: insert returned null");
                return false;
            }

            mIsRunning = true;
            addSplitter();
        } catch (SQLiteException ex) {
            HyperLog.w(Constants.TAG, "TrackerService.startTrack SQLiteException: " + ex.getMessage(), ex);
            mIsRunning = false;
            return false;
        }

        sendTrackStartBroadcast(checkIsBatteryPermOK(this));
        ((GISApplication)getApplication()).setIsTrackInProgress(true);
        return true;
    }

    private void sendTrackStartBroadcast(boolean batteryOK) {
        Intent msg = new Intent(ConstantsUI.MESSAGE_INTENT_TRACK);
        msg.setPackage(this.getPackageName());
        msg.putExtra(ConstantsUI.KEY_MESSAGE_TRACK, true);
        if (!batteryOK)
            msg.putExtra(ConstantsUI.KEY_BATTERY, false);
        msg.putExtra(ConstantsUI.KEY_TRACK_ACTION, VALUE_TRACK_START);
        msg.setPackage(getPackageName());
        sendBroadcast(msg);
    }

    private boolean isUnfinishedTrack(String trackId) {
        String selection = TrackLayer.FIELD_ID + " = ? AND ("
                + TrackLayer.FIELD_END + " IS NULL OR " + TrackLayer.FIELD_END + " = '')";
        String[] projection = new String[]{TrackLayer.FIELD_ID};
        String[] args = new String[]{trackId};
        Cursor data = null;
        try {
            data = getContentResolver().query(mContentUriTracks, projection, selection, args, null);
            return data != null && data.moveToFirst();
        } catch (RuntimeException ex) {
            HyperLog.w(Constants.TAG, "TrackerService.isUnfinishedTrack: " + ex.getMessage(), ex);
            return false;
        } finally {
            if (data != null)
                data.close();
        }
    }

    private void clearTempTrackState() {
        mSharedPreferencesTemp.edit()
                .remove(TRACK_URI)
                .remove(ConstantsUI.TARGET_CLASS)
                .apply();
    }

    public static boolean checkIsBatteryPermOK(Context context){
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return  pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    private void stopTrack(String reason) {
        if (!mIsRunning && mTrackId == null && !ACTION_STOP.equals(reason)) {
            HyperLog.v(Constants.TAG, "TrackerService.stopTrack ignored empty state reason=" + reason);
            return;
        }
        if (!mIsRunning && mTrackId == null && mStopBroadcastSent) {
            HyperLog.v(Constants.TAG, "TrackerService.stopTrack ignored reason=" + reason);
            return;
        }

        int flushed = flushTrackFilterPointsToDb();
        boolean closingSnap = appendClosingTrackSnapIfNeeded(pickBestClosingLocation());

        // update unclosed tracks in DB
        int closed = closeTracks(this, (IGISApplication) getApplication());

        mIsRunning = false;

        // cancel midnight splitter
        mAlarmManager.cancel(mSplitService);
        mSharedPreferencesTemp.edit().remove(ConstantsUI.TARGET_CLASS).apply();
        mSharedPreferencesTemp.edit().remove(TRACK_URI).apply();

        HyperLog.v(Constants.TAG, "TrackerService.stopTrack reason=" + reason
                + " trackId=" + mTrackId
                + " raw=" + mRawFixCount
                + " accepted=" + mAcceptedFixCount
                + " bufferedOrDropped=" + mBufferedOrDroppedFixCount
                + " inserted=" + mInsertedPointCount
                + " insertFail=" + mInsertFailCount
                + " flushed=" + flushed
                + " closingSnap=" + closingSnap
                + " filterInput=" + mTrackLocationFilter.getInputFixCount()
                + " filterPassed=" + mTrackLocationFilter.getPassedInputFixCount()
                + " filterDropped=" + mTrackLocationFilter.getDroppedInputFixCount()
                + " filterChordDropped=" + mTrackLocationFilter.getChordDroppedFixCount()
                + " filterBuffered=" + mTrackLocationFilter.getBufferedFixCount()
                + " filterGaps=" + mTrackLocationFilter.getGapSegmentCount()
                + " networkSuppressed=" + mTrackProviderArbiter.getSuppressedNetworkFixCount()
                + " closedTracks=" + closed);

        if (!mStopBroadcastSent) {
            Intent msgT = new Intent(ConstantsUI.MESSAGE_INTENT_TRACK);
            msgT.putExtra(ConstantsUI.KEY_MESSAGE_TRACK, false);
            msgT.putExtra(ConstantsUI.KEY_TRACK_ACTION, VALUE_TRACK_STOP);
            msgT.setPackage(this.getPackageName());
            sendBroadcast(msgT);
            mStopBroadcastSent = true;
        }

        ((GISApplication)getApplication()).setIsTrackInProgress(false);
        mTrackId = null;

        // Only explicit menu finish clears the durable recording flag.
        // onDestroy / crash / reboot must leave it set so recording auto-resumes.
        if (ACTION_STOP.equals(reason)) {
            setTrackRecordingEnabled(this, false);
        }
    }



    public static int closeTracks(Context context, IGISApplication app) {
        ContentValues cv = new ContentValues();
        cv.put(TrackLayer.FIELD_END, System.currentTimeMillis());
        String selection = TrackLayer.FIELD_END + " IS NULL OR " + TrackLayer.FIELD_END + " = ''";
        Uri tracksUri = Uri.parse("content://" + app.getAuthority() + "/" + TrackLayer.TABLE_TRACKS);
        try {
            return context.getContentResolver().update(tracksUri, cv, selection, null);
        } catch (IllegalArgumentException | SQLiteException ex) {
            HyperLog.w(Constants.TAG, "TrackerService.closeTracks: " + ex.getMessage(), ex);
        }
        return 0;
    }

    private void addSplitter() {
        // set midnight track splitter
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        today.add(Calendar.DATE, 1);
        mAlarmManager.set(AlarmManager.RTC, today.getTimeInMillis(), mSplitService);
    }

    private void addNotification() {
        String name = "";
        String selection = TrackLayer.FIELD_ID + " = ?";
        String[] proj = new String[]{TrackLayer.FIELD_NAME};
        String[] args = new String[]{mTrackId};
        try {
            Cursor currentTrack = getContentResolver().query(mContentUriTracks, proj, selection, args, null);
            if (null != currentTrack) {
                if (currentTrack.moveToFirst())
                    name = currentTrack.getString(0);
                currentTrack.close();
            }
        } catch (Exception ignored){
        }

        String title = String.format(getString(R.string.tracks_title), name);
        Intent intentStop = new Intent(this, TrackerService.class);
        intentStop.setAction(ACTION_STOP);
        int flag = PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE;
        PendingIntent stopService = PendingIntent.getService(this, 0, intentStop, flag);

        NotificationCompat.Builder builder = createBuilder(this, R.string.title_edit_by_walk);
        builder.setContentIntent(mOpenActivity)
                .setSmallIcon(mSmallIcon)
                .setLargeIcon(mLargeIcon)
                .setTicker(mTicker)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .setContentTitle(title)
                .setContentText(mTicker)
                .setOngoing(true);

        int resource = R.drawable.ic_location;
        builder.addAction(resource, getString(R.string.tracks_open), mOpenActivity);
        resource = R.drawable.ic_action_cancel_dark;
        builder.addAction(resource, getString(R.string.tracks_stop), stopService);

        mNotificationManager.notify(TRACK_NOTIFICATION_ID, builder.build());
        startForeground(TRACK_NOTIFICATION_ID, builder.build());
        Toast.makeText(this, title, Toast.LENGTH_SHORT).show();
    }


    private void removeNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            stopForeground(true);
        else
            mNotificationManager.cancel(TRACK_NOTIFICATION_ID);
    }

    private void addStartingNotification() {
        NotificationCompat.Builder builder = createBuilder(this, R.string.tracks_running);
        builder.setSmallIcon(mSmallIcon)
                .setLargeIcon(mLargeIcon)
                .setTicker(mTicker)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .setContentTitle(getString(R.string.tracks_running))
                .setContentText(getString(R.string.tracks_running))
                .setOngoing(true);
        if (mOpenActivity != null)
            builder.setContentIntent(mOpenActivity);
        startForeground(TRACK_NOTIFICATION_ID, builder.build());
    }

    private void foregroundStopMissingLocationPermission() {
        NotificationCompat.Builder builder = createBuilder(this, R.string.tracks_running);
        builder.setSmallIcon(mSmallIcon)
                .setContentTitle(getString(R.string.tracks_running))
                .setContentText(getString(R.string.error_no_location))
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setOngoing(false);
        startForeground(TRACK_NOTIFICATION_ID, builder.build());
        stopForeground(true);
    }


    // intent to open on notification click
    private void initTargetIntent(String targetActivity) {
        Intent intentActivity = getPackageManager().getLaunchIntentForPackage(getPackageName());

        if (!TextUtils.isEmpty(targetActivity)) {
            Class<?> targetClass = null;

            try {
                targetClass = Class.forName(targetActivity);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

            if (targetClass != null && Activity.class.isAssignableFrom(targetClass)) {
                intentActivity = new Intent(this, targetClass);
            }
        }

        if (intentActivity == null) {
            mOpenActivity = null;
            return;
        }
        intentActivity.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flag = PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE;
        mOpenActivity = PendingIntent.getActivity(this, 0, intentActivity, flag);
    }

    public void onDestroy() {
        HyperLog.v(Constants.TAG, "TrackerService.onDestroy running=" + mIsRunning + " trackId=" + mTrackId);
        stopTrack("onDestroy");

        if (PermissionUtil.hasLocationPermissions(this)) {
            try {
                mLocationManager.removeUpdates(this);
            } catch (Exception ex) {
                HyperLog.w(Constants.TAG, "TrackerService.removeUpdates: " + ex.getMessage(), ex);
            }

            unregisterGpsStatusListenerSafely();
        }

        if (mLocationSenderThread != null)
            mLocationSenderThread.interrupt();

        if (mRecordingSoundMonitor != null)
            mRecordingSoundMonitor.release();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(Constants.TAG, "tracker - onLocationChanged");
        mRawFixCount++;

        if (!mIsRunning) {
            HyperLog.d(Constants.TAG, "TrackerService.onLocationChanged ignored: not running");
            return;
        }
        boolean update = isProviderAllowedForTrack(location.getProvider());
        if (!update) {
            mBufferedOrDroppedFixCount++;
            HyperLog.d(Constants.TAG, "TrackerService.onLocationChanged ignored provider="
                    + location.getProvider());
            return;
        }
        if (!mTrackProviderArbiter.shouldProcess(location)) {
            mBufferedOrDroppedFixCount++;
            HyperLog.d(Constants.TAG,
                    "TrackerService.onLocationChanged suppressed network fix after usable GPS");
            return;
        }

        mLastTrackLocationRaw = new Location(location);

        long passedBefore = mTrackLocationFilter.getPassedInputFixCount();
        List<Location> toSave = mTrackLocationFilter.onLocation(location);
        if (mTrackLocationFilter.getPassedInputFixCount() > passedBefore) {
            mTrackProviderArbiter.onAccepted(location);
        }
        if (toSave.isEmpty()) {
            mBufferedOrDroppedFixCount++;
            return;
        }
        mAcceptedFixCount += toSave.size();
        for (Location loc : toSave) {
            insertTrackPoint(loc);
        }
    }

    private boolean insertTrackPoint(Location location) {
        if (location == null || mTrackId == null) {
            HyperLog.w(Constants.TAG, "TrackerService.insertTrackPoint skipped: location="
                    + (location != null) + " trackId=" + mTrackId);
            return false;
        }

        String fixType = location.hasAltitude() ? "3d" : "2d";

        mValues.clear();
        mValues.put(TrackLayer.FIELD_SESSION, mTrackId);

        mPoint.setCoordinates(location.getLongitude(), location.getLatitude());
        mPoint.setCRS(GeoConstants.CRS_WGS84);
        mPoint.project(GeoConstants.CRS_WEB_MERCATOR);
        mValues.put(TrackLayer.FIELD_LON, mPoint.getX());
        mValues.put(TrackLayer.FIELD_LAT, mPoint.getY());
        mValues.put(TrackLayer.FIELD_ELE, location.getAltitude());
        mValues.put(TrackLayer.FIELD_FIX, fixType);
        mValues.put(TrackLayer.FIELD_SAT, mSatellitesCount);
        mValues.put(TrackLayer.FIELD_SPEED, location.getSpeed());
        mValues.put(TrackLayer.FIELD_ACCURACY, location.getAccuracy());
        mValues.put(TrackLayer.FIELD_BEARING, location.getBearing());
        mValues.put(TrackLayer.FIELD_SENT, 0);
        mValues.put(TrackLayer.FIELD_TIMESTAMP, location.getTime());
        try {
            Uri inserted = getContentResolver().insert(mContentUriTrackPoints, mValues);
            if (inserted != null) {
                mInsertedPointCount++;
                mLastInsertedTrackLocation = new Location(location);
                sendTrackPointBroadcast();
                mRecordingSoundMonitor.onPointPersisted();
                return true;
            }
            mInsertFailCount++;
            HyperLog.w(Constants.TAG, "TrackerService.insertTrackPoint returned null trackId=" + mTrackId);
            mRecordingSoundMonitor.onPersistenceFailed();
        } catch (Exception ex) {
            mInsertFailCount++;
            Log.e(TrackerService.class.getName(), "onLocation EXCEPTION!!" + ex.getMessage());
            HyperLog.w(Constants.TAG, "TrackerService.insertTrackPoint: " + ex.getMessage(), ex);
            mRecordingSoundMonitor.onPersistenceFailed();
        }
        return false;
    }

    private int flushTrackFilterPointsToDb() {
        if (mTrackLocationFilter == null || mTrackId == null)
            return 0;
        int flushed = 0;
        for (Location loc : mTrackLocationFilter.flushRemaining()) {
            if (insertTrackPoint(loc))
                flushed++;
        }
        return flushed;
    }

    private void sendTrackPointBroadcast() {
        Intent msg = new Intent(ConstantsUI.MESSAGE_INTENT_TRACK);
        msg.putExtra(ConstantsUI.KEY_MESSAGE_TRACK, true);
        msg.putExtra(ConstantsUI.KEY_TRACK_ACTION, VALUE_TRACK_POINT);
        msg.setPackage(getPackageName());
        sendBroadcast(msg);
    }

    private boolean isProviderAllowedForTrack(String provider) {
        // Track recording has its own source preference. The former OR with the map-location
        // preference silently re-enabled providers explicitly disabled for tracks.
        return LocationUtil.isProviderEnabled(this, provider, true);
    }

    private void requestTrackLocationUpdates(String provider, long minTime, float minDistance) {
        try {
            if (!mLocationManager.getAllProviders().contains(provider)) {
                HyperLog.d(Constants.TAG, "TrackerService provider unavailable: " + provider);
                return;
            }
            if (!isProviderAllowedForTrack(provider)) {
                HyperLog.d(Constants.TAG, "TrackerService provider disabled by prefs: " + provider);
                return;
            }
            mLocationManager.requestLocationUpdates(provider, minTime, minDistance, this);
            HyperLog.v(Constants.TAG, "TrackerService request location updates provider=" + provider
                    + " minTimeMs=" + minTime + " minDistanceM=" + minDistance);
            if (Constants.DEBUG_MODE)
                Log.d(Constants.TAG, "Tracker service request location updates for " + provider);
        } catch (Exception ex) {
            HyperLog.w(Constants.TAG, "TrackerService.requestLocationUpdates " + provider + ": "
                    + ex.getMessage(), ex);
        }
    }

    private void registerGpsStatusListenerSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mLocationManager.registerGnssStatusCallback(mGnssCallback);
            } else {
                mLocationManager.addGpsStatusListener(this);
            }
        } catch (Exception ex) {
            HyperLog.w(Constants.TAG, "TrackerService.registerGpsStatus: " + ex.getMessage(), ex);
        }
    }

    private void unregisterGpsStatusListenerSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mLocationManager.unregisterGnssStatusCallback(mGnssCallback);
            } else {
                mLocationManager.removeGpsStatusListener(this);
            }
        } catch (Exception ex) {
            HyperLog.w(Constants.TAG, "TrackerService.unregisterGpsStatus: " + ex.getMessage(), ex);
        }
    }

    private Location pickBestClosingLocation() {
        Location appLast = null;
        try {
            Context appCtx = getApplicationContext();
            if (appCtx instanceof IGISApplication) {
                appLast = ((IGISApplication) appCtx).getGpsEventSource().getLastKnownLocation();
            }
        } catch (Exception ex) {
            HyperLog.w(Constants.TAG, "TrackerService.pickBestClosingLocation: " + ex.getMessage(), ex);
        }
        return fresherLocation(mLastTrackLocationRaw, appLast);
    }

    private static Location fresherLocation(Location a, Location b) {
        if (a == null)
            return b != null ? new Location(b) : null;
        if (b == null)
            return new Location(a);
        return locationFixMonotonicNanos(b) >= locationFixMonotonicNanos(a)
                ? new Location(b) : new Location(a);
    }

    private static long locationFixMonotonicNanos(Location loc) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            long n = loc.getElapsedRealtimeNanos();
            if (n > 0L)
                return n;
        }
        return loc.getTime() * 1_000_000L;
    }

    private boolean appendClosingTrackSnapIfNeeded(Location lastRaw) {
        if (lastRaw == null || mTrackId == null)
            return false;
        if (!LocationTrackFilter.passesBasicIntegrity(lastRaw))
            return false;
        if (mLastInsertedTrackLocation == null)
            return insertTrackPoint(lastRaw);
        float dist = mLastInsertedTrackLocation.distanceTo(lastRaw);
        if (dist < CLOSING_SNAP_MIN_DIST_M)
            return false;
        double dtSec = Math.max(0.001d,
                (locationFixMonotonicNanos(lastRaw) - locationFixMonotonicNanos(mLastInsertedTrackLocation))
                        / 1_000_000_000d);
        double maxDist = LocationTrackFilter.DEFAULT_MAX_SPEED_MPS * dtSec
                + LocationTrackFilter.DEFAULT_ACCURACY_MARGIN_K
                * (CLOSING_REF_ACCURACY_M + lastRaw.getAccuracy());
        maxDist = Math.max(maxDist, CLOSING_SNAP_MIN_MAX_DIST_M);
        if (dist > maxDist) {
            HyperLog.d(Constants.TAG, "TrackerService closing snap skipped dist=" + dist
                    + " maxDist=" + (float) maxDist);
            return false;
        }
        return insertTrackPoint(lastRaw);
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

    @Override
    public void onGpsStatusChanged(int event) {
        switch (event) {
            case GpsStatus.GPS_EVENT_STARTED:
            case GpsStatus.GPS_EVENT_STOPPED:
                mHasGPSFix = false;
                break;
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                mHasGPSFix = true;
                break;

            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                mSatellitesCount = 0;

                for (GpsSatellite sat : mLocationManager.getGpsStatus(null).getSatellites()) {
                    if (sat.usedInFix()) {
                        mSatellitesCount++;
                    }
                }
                break;
        }
    }

    public static boolean hasUnfinishedTracks(Context context) {
        IGISApplication app = (IGISApplication) context.getApplicationContext();
        Uri tracksUri = Uri.parse("content://" + app.getAuthority() + "/" + TrackLayer.TABLE_TRACKS);
        String selection = TrackLayer.FIELD_END + " IS NULL OR " + TrackLayer.FIELD_END + " = ''";
        String[] projection = new String[]{TrackLayer.FIELD_ID};
        boolean hasUnfinishedTracks = false;
        try {
            Cursor data = context.getContentResolver().query(tracksUri, projection, selection, null, null);
            if (data != null) {
                hasUnfinishedTracks = data.moveToFirst();
                data.close();
            }
        } catch (SQLiteException ignored) {}
        return hasUnfinishedTracks;
    }

    private static void closeUnfinishedTracksBeforeRestart(Context context) {
        try {
            IGISApplication app = (IGISApplication) context.getApplicationContext();
            int closed = closeTracks(context, app);
            context.getSharedPreferences(TEMP_PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .remove(TRACK_URI)
                    .remove(ConstantsUI.TARGET_CLASS)
                    .apply();
            HyperLog.v(Constants.TAG, "TrackerService closed unfinished tracks before restart count="
                    + closed);
        } catch (RuntimeException ex) {
            HyperLog.w(Constants.TAG, "TrackerService.closeUnfinishedTracksBeforeRestart: "
                    + ex.getMessage(), ex);
        }
    }

    public static boolean isTrackerServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (TrackerService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }

        return false;
    }

    private Thread createLocationSenderThread(final Long delay) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
//                Log.d(Constants.TAG, "Entering sync thread");
                while (!Thread.currentThread().isInterrupted()) {
                    try {
//                        Log.d(Constants.TAG, "Sleep sync thread");
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    try {
                        sync();
                    } catch (SQLiteException ignored) {
                    }

                    if (!mIsRunning) {
                        removeNotification();
                        stopSelf();
                    }
                }
            }
        });
    }

    private void sync() throws SQLiteException {
//        Log.d(Constants.TAG, "Syncing trackpoints");
        if (mSharedPreferences.getBoolean(SettingsConstants.KEY_PREF_TRACK_SEND, false)) {
            ContentResolver resolver = getContentResolver();
            String selection = TrackLayer.FIELD_SENT + " = 0";
            String sort = TrackLayer.FIELD_TIMESTAMP + " ASC";
            Cursor points = null;
            try {
                points = resolver.query(mContentUriTrackPoints, null, selection, null, sort);
            } catch (Exception ignored) {

            }
            if (points != null) {
//                int crashes  = 0;
                List<String> ids = new ArrayList<>();
                if (points.moveToFirst()) {
                    GeoPoint point = new GeoPoint();
                    int lon = points.getColumnIndex(TrackLayer.FIELD_LON);
                    int lat = points.getColumnIndex(TrackLayer.FIELD_LAT);
                    int ele = points.getColumnIndex(TrackLayer.FIELD_ELE);
                    int fix = points.getColumnIndex(TrackLayer.FIELD_FIX);
                    int sat = points.getColumnIndex(TrackLayer.FIELD_SAT);
                    int acc = points.getColumnIndex(TrackLayer.FIELD_ACCURACY);
                    int bearing = points.getColumnIndex(TrackLayer.FIELD_BEARING);
                    int speed = points.getColumnIndex(TrackLayer.FIELD_SPEED);
                    int time = points.getColumnIndex(TrackLayer.FIELD_TIMESTAMP);
                    JSONArray payload = new JSONArray();

                    int counter = 0;
                    do {
                        JSONObject item = new JSONObject();
                        try {
                            point.setCoordinates(points.getDouble(lon), points.getDouble(lat));
                            point.setCRS(GeoConstants.CRS_WEB_MERCATOR);
                            point.project(GeoConstants.CRS_WGS84);
                            item.put("lt", point.getY());
                            item.put("ln", point.getX());
                            item.put("ts", points.getLong(time)/1000);
                            item.put("a", points.getDouble(ele));
                            item.put("s", points.getInt(sat));
                            item.put("ft", points.getString(fix).equals("3d") ? 3 : 2);
                            item.put("sp", points.getDouble(speed) * 18 / 5);
                            item.put("ha", points.getDouble(acc));
                            item.put("c", points.getDouble(bearing));
                            payload.put(item);
                            ids.add(points.getString(time));
                            counter++;

                            if (counter >= 100) {
                                post(payload.toString(), this, ids);
                                payload = new JSONArray();
                                ids.clear();
                                counter = 0;
                            }
                        } catch (Exception ignored) {
//                            crashes++;

                        }
                    } while (points.moveToNext() );

                    if (counter > 0) {
                        try {
                            post(payload.toString(), this, ids);
                        } catch (Exception ignored) {

                        }
                    }
                }
                points.close();
            }
        }
    }

    private boolean post(String payload, Context context, List<String> ids) throws IOException {
        String base = mSharedPreferences.getString("tracker_hub_url", HOST);
        String url = String.format("%s/%s/packet", base + URL, getUid(context));
//        Log.d(Constants.TAG, "Post to " + url);
        HttpResponse response = NetworkUtil.post(url, payload, null, null, false);
//        Log.d(Constants.TAG, "Response is " + response.getResponseCode());
        if (!response.isOk())
            return  false;

        ContentValues cv = new ContentValues();
        cv.put(TrackLayer.FIELD_SENT, 1);
        String where = TrackLayer.FIELD_TIMESTAMP + " in (" + MapUtil.makePlaceholders(ids.size()) + ")";
        String[] timestamps = ids.toArray(new String[0]);
        try {
            context.getContentResolver().update(mContentUriTrackPoints, cv, where, timestamps);
        } catch (SQLiteException ignored) {

        }
        return true;
    }

    @SuppressLint("HardwareIds")
    public static String getUid(Context context) {
        String uuid = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return String.format("%X", uuid.hashCode());
    }



    public static void showBackgroundDialog(final Activity context, final BackgroundPermissionCallback listener) {
        int okButton = R.string.ok;
        CharSequence backgroundPermissionTitle = context.getString(R.string.background_location_always);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            backgroundPermissionTitle = context.getPackageManager().getBackgroundPermissionOptionLabel();
        }
        String message = context.getString(R.string.background_location_message, backgroundPermissionTitle);
        boolean hasBackground = PermissionUtil.hasBackgroundLocationPermissions(context);
        boolean hasLocation = PermissionUtil.hasLocationPermissions(context);

        if (!hasLocation) {
            // location first
            List<String> permslist = new ArrayList<>();
            permslist.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            permslist.add(Manifest.permission.ACCESS_FINE_LOCATION);
            requestPermissions(context, R.string.permissions, R.string.location_permissions, LOCATION_BACKGROUND_REQUEST,
                    permslist.toArray(new String[permslist.size()])); // list.toArray(new Foo[list.size()])
            return;
        }


        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            if (hasBackground) {
                listener.onAndroid10(true);
                return;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (hasBackground) {
                listener.afterAndroid10(true);
                return;
            }
            okButton = R.string.action_settings;
        } else {
            listener.beforeAndroid10(hasLocation);
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.background_location)
               .setMessage(message)
               .setPositiveButton(okButton, new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialogInterface, int i) {
                       if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                           listener.onAndroid10(false);
                       } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                           listener.afterAndroid10(false);
                       }
                   }
               })
               .setNegativeButton(R.string.cancel, null)
               .show();
    }

    public static void requestPermissions(final Activity activity1, int title, int message, final int requestCode,
                                   final String... permissions) {
        final Activity activity = activity1;
        if (true) {
            androidx.appcompat.app.AlertDialog builder = new androidx.appcompat.app.AlertDialog.Builder(activity).setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(com.nextgis.maplibui.R.string.allow, (dialog, which) -> {
                        ActivityCompat.requestPermissions(activity, permissions, requestCode);})
                    .setNegativeButton(com.nextgis.maplibui.R.string.deny, null)
                    .create();
            builder.setCanceledOnTouchOutside(false);
            builder.show();
        }
    }

    public interface BackgroundPermissionCallback {
        void beforeAndroid10(boolean hasBackgroundPermission);
        void onAndroid10(boolean hasBackgroundPermission);
        void afterAndroid10(boolean hasBackgroundPermission);
    }

}
