package com.nextgis.maplibui.mapui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.TrackLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.maplib.util.SyncWorkspaceSession;
import com.nextgis.maplib.util.TrackSendSettings;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;
import com.hypertrack.hyperlog.HyperLog;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TrackWorker extends Worker {
    public static final String TEMP_PREFERENCES = "tracks_temp";
    public static final String ACTION_SYNC = "com.nextgis.maplibui.TRACK_SYNC";
    public static final String ACTION_STOP = "com.nextgis.maplibui.TRACK_STOP";
    public static final String HOST = "https://track.nextgis.com";
    public static final String URL = "/ng-mobile";
    public static final String WORK_TAG = "track-upload";
    private static final String MAP_PATH = "track_upload_map";
    private static final String PERIODIC = "track_upload_periodic";

    public TrackWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    public static void schedule(Context context) {
        schedule(context, ((IGISApplication) context.getApplicationContext()).getMap());
    }

    public static void schedule(Context context, MapBase map) {
        if (map != null) schedulePath(context, map.getPath());
    }

    public static void scheduleAll(Context context) {
        if (!TrackSendSettings.isEnabled(PreferenceManager.getDefaultSharedPreferences(context))) return;
        for (CollectorProjectRegistry.ProjectInfo project : CollectorProjectRegistry.listProjects(context))
            schedulePath(context, new File(project.getMapPath()));
        schedule(context);
    }

    public static void cancel(Context context) { WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG); }

    private static void schedulePath(Context context, File file) {
        if (!TrackSendSettings.isEnabled(PreferenceManager.getDefaultSharedPreferences(context)) || !file.isDirectory()) return;
        try {
            String path = file.getCanonicalPath();
            String name = WORK_TAG + "-" + UUID.nameUUIDFromBytes(path.getBytes(StandardCharsets.UTF_8));
            Constraints network = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
            Data immediate = new Data.Builder().putString(MAP_PATH, path).build();
            WorkManager work = WorkManager.getInstance(context);
            work.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, new OneTimeWorkRequest.Builder(TrackWorker.class)
                    .setInputData(immediate).setConstraints(network).setInitialDelay(1, TimeUnit.SECONDS)
                    .addTag(WORK_TAG).build());
            Data periodic = new Data.Builder().putString(MAP_PATH, path).putBoolean(PERIODIC, true).build();
            work.enqueueUniquePeriodicWork(name + "-periodic", ExistingPeriodicWorkPolicy.KEEP,
                    new PeriodicWorkRequest.Builder(TrackWorker.class, 15, TimeUnit.MINUTES)
                            .setInputData(periodic).setConstraints(network).addTag(WORK_TAG).build());
        } catch (Exception error) { HyperLog.w(Constants.TAG, "Track upload scheduling deferred", error); }
    }

    @NonNull @Override public Result doWork() {
        if (!TrackSendSettings.isEnabled(PreferenceManager.getDefaultSharedPreferences(getApplicationContext())))
            return Result.success();
        if (!new NetworkUtil(getApplicationContext()).isNetworkAvailable()) return Result.retry();
        boolean complete = uploadProject(getApplicationContext(), getInputData().getString(MAP_PATH));
        // A periodic pass keeps its fifteen-minute cadence while awaiting registration/network.
        return complete || getInputData().getBoolean(PERIODIC, false) ? Result.success() : Result.retry();
    }

    public static boolean uploadProject(Context context, String path) {
        if (!TrackSendSettings.isEnabled(PreferenceManager.getDefaultSharedPreferences(context))) return true;
        if (path == null) return true; // Old unowned jobs must not fall through to the active map.
        if (!new NetworkUtil(context).isNetworkAvailable()) return false;
        MapContentProviderHelper map = null;
        SyncWorkspaceSession session = null;
        boolean background = false;
        ProjectOperationCoordinator.Lease lease = ProjectOperationCoordinator.tryBegin(context, ProjectOperationCoordinator.Kind.DATA_SYNC);
        if (lease == null) { android.util.Log.d(Constants.TAG, "Track upload waiting for a project operation"); return false; }
        try {
            File file = new File(path).getCanonicalFile();
            IGISApplication app = (IGISApplication) context.getApplicationContext();
            MapBase active = app.getMap();
            background = active == null || !file.equals(active.getPath().getCanonicalFile());
            File mapFile = null;
            for (CollectorProjectRegistry.ProjectInfo project : CollectorProjectRegistry.listProjects(context))
                if (file.equals(new File(project.getMapPath()).getCanonicalFile()))
                    mapFile = new File(file, project.getMapName() + Constants.MAP_EXT);
            if (!file.isDirectory() || background && mapFile == null) return true;
            if (background && !mapFile.isFile()) return true;
            map = background ? new MapDrawable(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), context,
                    mapFile, app.getLayerFactory(), false) : (MapContentProviderHelper) active;
            session = new SyncWorkspaceSession(map);
            try (SyncWorkspaceSession.Scope scope = session.enter()) {
                if (background && !map.load()) { android.util.Log.w(Constants.TAG, "Track upload cannot load its registered workspace"); return false; }
                TrackLayer layer = (TrackLayer) MapContentProviderHelper.getVectorLayerByPath(map, TrackLayer.TABLE_TRACKS);
                return layer == null || TrackUploader.upload(context, layer);
            }
        } catch (Exception error) {
            HyperLog.w(Constants.TAG, "Track upload deferred; unsent points retained", error);
            android.util.Log.w(Constants.TAG, "Track upload deferred; unsent points retained", error);
            return false;
        } finally {
            try {
                if (session != null) session.close();
                if (background && map != null) map.closeSyncWorkspace();
            } finally { lease.close(); }
        }
    }

    @SuppressLint("HardwareIds") public static String getUid(Context context) {
        String uuid = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return String.format("%X", uuid.hashCode());
    }
}
