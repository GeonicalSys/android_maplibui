package com.nextgis.maplibui.mapui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.provider.Settings;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.maplib.util.TrackSendSettings;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import com.nextgis.maplibui.util.SharedTrackProjects;
import com.hypertrack.hyperlog.HyperLog;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** One durable upload queue for the installation, independent of project lifecycles. */
public class TrackWorker extends Worker {
    public static final String TEMP_PREFERENCES = "tracks_temp";
    public static final String ACTION_SYNC = "com.nextgis.maplibui.TRACK_SYNC";
    public static final String ACTION_STOP = "com.nextgis.maplibui.TRACK_STOP";
    public static final String HOST = "https://track.nextgis.com";
    public static final String URL = "/ng-mobile";
    public static final String WORK_TAG = "track-upload";
    private static final String WORK_NAME = "track-upload-shared-v1";
    private static final String MAP_PATH = "track_upload_map";
    private static final String PERIODIC = "track_upload_periodic";

    public TrackWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    public static void schedule(Context context) {
        if (!TrackSendSettings.isEnabled(PreferenceManager.getDefaultSharedPreferences(context))) return;
        Constraints network = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager work = WorkManager.getInstance(context);
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, new OneTimeWorkRequest.Builder(TrackWorker.class)
                .setConstraints(network).setInitialDelay(1, TimeUnit.SECONDS).addTag(WORK_TAG).build());
        work.enqueueUniquePeriodicWork(WORK_NAME + "-periodic", ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(TrackWorker.class, 15, TimeUnit.MINUTES)
                        .setInputData(new Data.Builder().putBoolean(PERIODIC, true).build())
                        .setConstraints(network).addTag(WORK_TAG).build());
    }

    /** Kept for callers compiled against the former project scheduler. */
    public static void schedule(Context context, MapBase ignored) { schedule(context); }

    public static void scheduleAll(Context context) {
        for (CollectorProjectRegistry.ProjectInfo project : CollectorProjectRegistry.listProjects(context)) {
            try {
                String path = new File(project.getMapPath()).getCanonicalPath();
                String oldName = WORK_TAG + "-" + UUID.nameUUIDFromBytes(path.getBytes(StandardCharsets.UTF_8));
                WorkManager.getInstance(context).cancelUniqueWork(oldName);
                WorkManager.getInstance(context).cancelUniqueWork(oldName + "-periodic");
            } catch (Exception error) { HyperLog.w(Constants.TAG, "Legacy track job cleanup deferred", error); }
        }
        schedule(context);
    }

    public static void cancel(Context context) { WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG); }

    @NonNull @Override public Result doWork() {
        // Persisted jobs from the old APK retire without reopening or acknowledging a project DB.
        if (getInputData().getString(MAP_PATH) != null) {
            schedule(getApplicationContext());
            // Success alone reschedules a legacy periodic request forever, including
            // paths no longer present in the project registry.
            WorkManager.getInstance(getApplicationContext()).cancelWorkById(getId());
            return Result.success();
        }
        if (!TrackSendSettings.isEnabled(PreferenceManager.getDefaultSharedPreferences(getApplicationContext())))
            return Result.success();
        boolean complete = uploadShared(getApplicationContext());
        return complete || getInputData().getBoolean(PERIODIC, false) ? Result.success() : Result.retry();
    }

    public static boolean uploadShared(Context context) {
        if (!TrackSendSettings.isEnabled(PreferenceManager.getDefaultSharedPreferences(context))) return true;
        if (!new NetworkUtil(context).isNetworkAvailable()) return false;
        try {
            SharedTrackProjects.prepare(context);
            return TrackUploader.upload(context);
        } catch (Exception error) {
            HyperLog.w(Constants.TAG, "Shared track upload deferred; source data retained", error);
            return false;
        }
    }

    /** Compatibility entry point: an explicitly owned legacy request now drains the shared queue. */
    public static boolean uploadProject(Context context, String path) {
        return path == null || uploadShared(context);
    }

    @SuppressLint("HardwareIds") public static String getUid(Context context) {
        String uuid = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return String.format("%X", uuid.hashCode());
    }
}
