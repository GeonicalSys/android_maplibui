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
import android.os.PowerManager;
import android.os.SystemClock;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.util.Constants;

import java.util.List;

/**
 * Plays a short confirmation after a point was really persisted while the app UI is hidden.
 * The absence of confirmations is intentional health feedback if Android stops the recorder.
 */
public final class BackgroundRecordingSoundMonitor {
    private static final int TONE_VOLUME_PERCENT = 45;
    private static final int HEARTBEAT_DURATION_MS = 90;
    private static final int ERROR_DURATION_MS = 350;

    private final Context mContext;
    private final SharedPreferences mPreferences;
    private final BackgroundRecordingSoundPolicy mPolicy = new BackgroundRecordingSoundPolicy();
    private ToneGenerator mToneGenerator;

    public BackgroundRecordingSoundMonitor(Context context, SharedPreferences preferences) {
        mContext = context.getApplicationContext();
        mPreferences = preferences;
    }

    public synchronized void onPointPersisted() {
        long nowMs = SystemClock.elapsedRealtime();
        boolean enabled = isEnabled();
        boolean background = isAppUiHidden();
        if (!mPolicy.shouldPlayHeartbeat(enabled, background, nowMs)) {
            return;
        }
        if (playTone(ToneGenerator.TONE_PROP_BEEP, HEARTBEAT_DURATION_MS)) {
            mPolicy.recordHeartbeat(nowMs);
        }
    }

    public synchronized void onPersistenceFailed() {
        long nowMs = SystemClock.elapsedRealtime();
        boolean enabled = isEnabled();
        boolean background = isAppUiHidden();
        if (!mPolicy.shouldPlayError(enabled, background, nowMs)) {
            return;
        }
        if (playTone(ToneGenerator.TONE_PROP_NACK, ERROR_DURATION_MS)) {
            mPolicy.recordError(nowMs);
        }
    }

    public synchronized void release() {
        if (mToneGenerator != null) {
            mToneGenerator.release();
            mToneGenerator = null;
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

    private boolean playTone(int tone, int durationMs) {
        try {
            if (mToneGenerator == null) {
                mToneGenerator = new ToneGenerator(
                        AudioManager.STREAM_NOTIFICATION, TONE_VOLUME_PERCENT);
            }
            return mToneGenerator.startTone(tone, durationMs);
        } catch (RuntimeException ex) {
            HyperLog.w(Constants.TAG,
                    "BackgroundRecordingSoundMonitor audio failure: " + ex.getMessage(), ex);
            release();
            return false;
        }
    }
}
