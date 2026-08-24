/*
 * Project: NextGIS Mobile
 * Purpose: Audible health feedback for background geometry recording.
 */
package com.nextgis.maplibui.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.LocationTrackFilter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plays a fixed-rate heartbeat while recording receives fresh usable coordinates and the app UI
 * is hidden. A separate zero-distance location subscription distinguishes a stationary device
 * from Android no longer delivering fixes; it never feeds points into the recorded geometry.
 */
public final class BackgroundRecordingSoundMonitor implements LocationListener {
    private static final int TONE_VOLUME_PERCENT = 45;
    private static final int HEARTBEAT_DURATION_MS = 90;
    private static final int ERROR_DURATION_MS = 350;
    private static final long HEARTBEAT_VIBRATION_MS = 80L;
    private static final long[] ERROR_VIBRATION_PATTERN = {0L, 80L, 70L, 80L};
    private static final long HEALTH_LOCATION_INTERVAL_MS = 2_000L;

    private final Context mContext;
    private final SharedPreferences mPreferences;
    private final BackgroundRecordingSoundPolicy mPolicy = new BackgroundRecordingSoundPolicy();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHeartbeatRunnable = this::runHeartbeat;
    private final Set<String> mRegisteredProviders = new HashSet<>();
    private LocationManager mLocationManager;
    private PowerManager.WakeLock mWakeLock;
    private ToneGenerator mToneGenerator;
    private boolean mStarted;
    private long mLastUsableLocationAtMs = Long.MIN_VALUE;
    private long mNextHeartbeatAtMs = Long.MIN_VALUE;

    public BackgroundRecordingSoundMonitor(Context context, SharedPreferences preferences) {
        mContext = context.getApplicationContext();
        mPreferences = preferences;
    }

    /**
     * Starts the health-only location stream and fixed 10-second heartbeat schedule.
     * Providers must be the same providers that the owning recording service accepted.
     */
    public synchronized void start(LocationManager locationManager, String... providers) {
        if (mStarted) {
            return;
        }
        mLocationManager = locationManager;
        if (mLocationManager == null) {
            return;
        }

        if (providers != null) {
            for (String provider : providers) {
                registerHealthProvider(provider);
            }
        }
        if (mRegisteredProviders.isEmpty()) {
            return;
        }

        mStarted = true;
        long nowMs = SystemClock.elapsedRealtime();
        mNextHeartbeatAtMs = nowMs + BackgroundRecordingSoundPolicy.HEARTBEAT_INTERVAL_MS;
        syncWakeLock();
        scheduleNextHeartbeat(nowMs);
    }

    public synchronized void onPersistenceFailed() {
        long nowMs = SystemClock.elapsedRealtime();
        boolean enabled = isEnabled();
        boolean background = isAppUiHidden();
        if (!mPolicy.shouldPlayError(enabled, background, nowMs)) {
            return;
        }
        if (emitSignal(ToneGenerator.TONE_PROP_NACK, ERROR_DURATION_MS, true)) {
            mPolicy.recordError(nowMs);
        }
    }

    public synchronized void release() {
        mStarted = false;
        mHandler.removeCallbacks(mHeartbeatRunnable);
        if (mLocationManager != null) {
            try {
                mLocationManager.removeUpdates(this);
            } catch (RuntimeException ex) {
                HyperLog.w(Constants.TAG,
                        "BackgroundRecordingSoundMonitor removeUpdates failure: "
                                + ex.getMessage(), ex);
            }
        }
        mRegisteredProviders.clear();
        mLocationManager = null;
        mLastUsableLocationAtMs = Long.MIN_VALUE;
        mNextHeartbeatAtMs = Long.MIN_VALUE;
        releaseWakeLock();
        if (mToneGenerator != null) {
            mToneGenerator.release();
            mToneGenerator = null;
        }
    }

    @Override
    public synchronized void onLocationChanged(Location location) {
        if (mStarted && LocationTrackFilter.passesBasicIntegrity(location)) {
            // Callback receipt time, rather than coordinate change, is the health signal.
            mLastUsableLocationAtMs = SystemClock.elapsedRealtime();
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Fresh location callbacks are the source of truth for health.
    }

    @Override
    public void onProviderEnabled(String provider) {
        // Wait for a fresh usable callback before confirming health.
    }

    @Override
    public void onProviderDisabled(String provider) {
        // The last callback naturally expires after LOCATION_FRESHNESS_MS.
    }

    private synchronized void runHeartbeat() {
        if (!mStarted) {
            return;
        }
        long nowMs = SystemClock.elapsedRealtime();
        syncWakeLock();
        if (mPolicy.shouldPlayHeartbeat(
                isEnabled(), isAppUiHidden(), mLastUsableLocationAtMs, nowMs)
                && emitSignal(ToneGenerator.TONE_PROP_BEEP, HEARTBEAT_DURATION_MS, false)) {
            mPolicy.recordHeartbeat(nowMs);
        }

        // Keep the cadence anchored to the original schedule and skip missed slots without a burst.
        do {
            mNextHeartbeatAtMs += BackgroundRecordingSoundPolicy.HEARTBEAT_INTERVAL_MS;
        } while (mNextHeartbeatAtMs <= nowMs);
        scheduleNextHeartbeat(nowMs);
    }

    private void scheduleNextHeartbeat(long nowMs) {
        mHandler.removeCallbacks(mHeartbeatRunnable);
        mHandler.postDelayed(mHeartbeatRunnable, Math.max(1L, mNextHeartbeatAtMs - nowMs));
    }

    private void registerHealthProvider(String provider) {
        if (provider == null || mRegisteredProviders.contains(provider)) {
            return;
        }
        try {
            if (!mLocationManager.getAllProviders().contains(provider)) {
                return;
            }
            mLocationManager.requestLocationUpdates(
                    provider, HEALTH_LOCATION_INTERVAL_MS, 0f, this);
            mRegisteredProviders.add(provider);
            HyperLog.v(Constants.TAG,
                    "BackgroundRecordingSoundMonitor health updates provider=" + provider
                            + " minTimeMs=" + HEALTH_LOCATION_INTERVAL_MS
                            + " minDistanceM=0");
        } catch (RuntimeException ex) {
            HyperLog.w(Constants.TAG,
                    "BackgroundRecordingSoundMonitor request updates " + provider + ": "
                            + ex.getMessage(), ex);
        }
    }

    private void syncWakeLock() {
        if (!isEnabled()) {
            releaseWakeLock();
            return;
        }
        if (mWakeLock != null && mWakeLock.isHeld()) {
            return;
        }
        PowerManager powerManager =
                (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return;
        }
        try {
            mWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    mContext.getPackageName() + ":BackgroundRecordingSound");
            mWakeLock.setReferenceCounted(false);
            mWakeLock.acquire();
        } catch (RuntimeException ex) {
            HyperLog.w(Constants.TAG,
                    "BackgroundRecordingSoundMonitor wake lock failure: " + ex.getMessage(), ex);
            mWakeLock = null;
        }
    }

    private void releaseWakeLock() {
        if (mWakeLock == null) {
            return;
        }
        try {
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        } catch (RuntimeException ex) {
            HyperLog.w(Constants.TAG,
                    "BackgroundRecordingSoundMonitor wake lock release failure: "
                            + ex.getMessage(), ex);
        } finally {
            mWakeLock = null;
        }
    }

    private boolean isEnabled() {
        return mPreferences.getBoolean(
                SettingsConstantsUI.KEY_PREF_BACKGROUND_RECORDING_SOUND, true);
    }

    private boolean isAppUiHidden() {
        PowerManager powerManager =
                (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null && !powerManager.isInteractive()) {
            return true;
        }

        ActivityManager activityManager =
                (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return true;
        }
        List<ActivityManager.RunningAppProcessInfo> processes =
                activityManager.getRunningAppProcesses();
        if (processes == null) {
            return true;
        }
        String mainProcessName = mContext.getPackageName();
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (!mainProcessName.equals(process.processName)) {
                continue;
            }
            // IMPORTANCE_FOREGROUND_SERVICE means recording is alive but the UI is hidden.
            return process.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && process.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
        }
        return true;
    }

    private boolean emitSignal(int tone, int durationMs, boolean error) {
        AudioManager audioManager =
                (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        boolean alarmMuted = true;
        int alarmVolume = 0;
        if (audioManager != null) {
            try {
                alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
                alarmMuted = audioManager.isStreamMute(AudioManager.STREAM_ALARM);
            } catch (RuntimeException ex) {
                HyperLog.w(Constants.TAG,
                        "BackgroundRecordingSoundMonitor alarm volume failure: "
                                + ex.getMessage(), ex);
            }
        }
        if (BackgroundRecordingSoundPolicy.shouldVibrate(alarmVolume, alarmMuted)) {
            return vibrate(error);
        }
        if (playAlarmTone(tone, durationMs)) {
            return true;
        }
        return vibrate(error);
    }

    private boolean playAlarmTone(int tone, int durationMs) {
        try {
            if (mToneGenerator == null) {
                mToneGenerator = new ToneGenerator(
                        AudioManager.STREAM_ALARM, TONE_VOLUME_PERCENT);
            }
            return mToneGenerator.startTone(tone, durationMs);
        } catch (RuntimeException ex) {
            HyperLog.w(Constants.TAG,
                    "BackgroundRecordingSoundMonitor audio failure: " + ex.getMessage(), ex);
            if (mToneGenerator != null) {
                mToneGenerator.release();
                mToneGenerator = null;
            }
            return false;
        }
    }

    private boolean vibrate(boolean error) {
        Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return false;
        }
        try {
            VibrationEffect effect = error
                    ? VibrationEffect.createWaveform(ERROR_VIBRATION_PATTERN, -1)
                    : VibrationEffect.createOneShot(
                    HEARTBEAT_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE);
            vibrator.vibrate(effect);
            return true;
        } catch (RuntimeException ex) {
            HyperLog.w(Constants.TAG,
                    "BackgroundRecordingSoundMonitor vibration failure: "
                            + ex.getMessage(), ex);
            return false;
        }
    }
}
