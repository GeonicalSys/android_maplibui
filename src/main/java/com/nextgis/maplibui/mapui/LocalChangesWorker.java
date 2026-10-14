package com.nextgis.maplibui.mapui;

import android.content.Context;
import android.content.SyncResult;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.*;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Durable, push-only delivery. Account full-sync schedules and completion clocks are independent. */
public final class LocalChangesWorker extends Worker {
    private static final String PATH = "local_changes_map";
    private static final String TAG = "local-changes-upload";
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    public LocalChangesWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    public static void schedule(Context context, MapBase map) {
        if (map != null) schedule(context, map.getPath());
    }
    public static void scheduleAll(Context context) {
        for (CollectorProjectRegistry.ProjectInfo project : CollectorProjectRegistry.listProjects(context))
            schedule(context, new File(project.getMapPath()));
        schedule(context, ((IGISApplication) context.getApplicationContext()).getMap());
    }
    private static void schedule(Context context, File file) {
        if (file == null || !file.isDirectory()) return;
        try {
            String path = file.getCanonicalPath();
            if (!PENDING.add(path)) return;
            Context application = context.getApplicationContext();
            HANDLER.postDelayed(() -> { PENDING.remove(path); enqueue(application, path); }, 1_000);
        } catch (Exception error) { HyperLog.w(Constants.TAG, "Local changes scheduling deferred", error); }
    }
    private static void enqueue(Context context, String path) {
        try {
            String name = TAG + "-" + UUID.nameUUIDFromBytes(path.getBytes(StandardCharsets.UTF_8));
            Data data = new Data.Builder().putString(PATH, path).build();
            Constraints network = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
            OneTimeWorkRequest.Builder request = new OneTimeWorkRequest.Builder(LocalChangesWorker.class)
                    .setInputData(data).setConstraints(network).addTag(TAG)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS);
            if (Build.VERSION.SDK_INT >= 31) request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
            WorkManager work = WorkManager.getInstance(context);
            // A commit during the running pass must create a following pass, rather than be lost by KEEP.
            work.enqueueUniqueWork(name, ExistingWorkPolicy.APPEND_OR_REPLACE, request.build());
            work.enqueueUniquePeriodicWork(name + "-recovery", ExistingPeriodicWorkPolicy.KEEP,
                    new PeriodicWorkRequest.Builder(LocalChangesWorker.class, 15, TimeUnit.MINUTES)
                            .setInputData(data).setConstraints(network).setInitialDelay(15, TimeUnit.MINUTES)
                            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).addTag(TAG).build());
        } catch (RuntimeException error) { HyperLog.w(Constants.TAG, "Local changes scheduling deferred", error); }
    }

    @NonNull @Override public Result doWork() {
        return uploadProject(getApplicationContext(), getInputData().getString(PATH)) ? Result.success() : Result.retry();
    }

    public static boolean outboundEnabled(NGWVectorLayer layer) {
        return !layer.isRemoteReadOnly() && layer.isRemoteSendAllowed()
                && (layer.getSyncType() & Constants.SYNC_NONE) == 0;
    }
    public static Set<String> projectAccounts(LayerGroup group) {
        Set<String> accounts = new HashSet<>();
        for (int i = 0; i < group.getLayerCount(); i++) {
            ILayer layer = group.getLayer(i);
            if (layer instanceof LayerGroup) accounts.addAll(projectAccounts((LayerGroup) layer));
            else if (layer instanceof NGWVectorLayer)
                accounts.add(((NGWVectorLayer) layer).getAccountName());
        }
        return accounts;
    }
    public static boolean hasPending(LayerGroup group, String account) {
        for (int i = 0; i < group.getLayerCount(); i++) {
            ILayer layer = group.getLayer(i);
            if (layer instanceof LayerGroup && hasPending((LayerGroup) layer, account)) return true;
            if (layer instanceof NGWVectorLayer) {
                NGWVectorLayer vector = (NGWVectorLayer) layer;
                if (outboundEnabled(vector) && (account == null || account.equals(vector.getAccountName()))
                        && FeatureChanges.getChangeCount(DatabaseContext.getDatabaseForLayer(vector, false),
                        vector.getChangeTableName()) > 0) return true;
            }
        }
        return false;
    }

    public static boolean uploadProject(Context context, String path) {
        if (path == null) return true;
        if (!new NetworkUtil(context).isNetworkAvailable()) return false;
        ProjectOperationCoordinator.Lease lease = ProjectOperationCoordinator.tryBegin(context, ProjectOperationCoordinator.Kind.DATA_SYNC);
        if (lease == null) return false;
        MapContentProviderHelper map = null;
        SyncWorkspaceSession session = null;
        boolean background = false, interrupted = false;
        try {
            File directory = new File(path).getCanonicalFile();
            File mapFile = null;
            for (CollectorProjectRegistry.ProjectInfo project : CollectorProjectRegistry.listProjects(context))
                if (directory.equals(new File(project.getMapPath()).getCanonicalFile()))
                    mapFile = new File(directory, project.getMapName() + Constants.MAP_EXT);
            IGISApplication app = (IGISApplication) context.getApplicationContext();
            MapBase active = app.getMap();
            background = active == null || !directory.equals(active.getPath().getCanonicalFile());
            if (!directory.isDirectory() || background && (mapFile == null || !mapFile.isFile())) return true;
            ProjectOperationCoordinator.requireScopedSyncDependents();
            map = background ? new MapDrawable(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                    context, mapFile, app.getLayerFactory(), false) : (MapContentProviderHelper) active;
            session = new SyncWorkspaceSession(map);
            try (SyncWorkspaceSession.Scope ignored = session.enter()) {
                if (background && !map.load()) return false;
                boolean complete = push(map);
                interrupted |= awaitChildren(session, lease);
                return complete && !session.hasFailed() && !session.isCancelled() && !interrupted && !hasPending(map, null);
            }
        } catch (Exception error) {
            HyperLog.w(Constants.TAG, "Local changes upload deferred; edits retained", error);
            return false;
        } finally {
            try {
                if (session != null) { interrupted |= awaitChildren(session, lease); session.close(); }
                if (background && map != null) map.closeSyncWorkspace();
            } finally { lease.close(); if (interrupted) Thread.currentThread().interrupt(); }
        }
    }
    private static boolean push(LayerGroup group) {
        boolean complete = true;
        for (int i = 0; i < group.getLayerCount(); i++) {
            ILayer layer = group.getLayer(i);
            if (layer instanceof LayerGroup) complete &= push((LayerGroup) layer);
            else if (layer instanceof NGWVectorLayer) {
                NGWVectorLayer vector = (NGWVectorLayer) layer;
                if (!outboundEnabled(vector) || FeatureChanges.getChangeCount(
                        DatabaseContext.getDatabaseForLayer(vector, false), vector.getChangeTableName()) == 0) continue;
                SyncResult result = new SyncResult();
                boolean sent = vector.sendLocalChanges(result);
                complete &= sent && !result.hasError();
            }
        }
        return complete;
    }
    private static boolean awaitChildren(SyncWorkspaceSession session, ProjectOperationCoordinator.Lease lease) {
        boolean interrupted = Thread.interrupted();
        try (AutoCloseable ignored = ProjectOperationCoordinator.allowSyncDependents(session.getToken())) {
            while (!session.isQuiet()) {
                lease.heartbeat(); session.expireUndelivered();
                try { session.waitForProgress(); } catch (InterruptedException error) { interrupted = true; }
            }
        } catch (Exception error) { throw new IllegalStateException(error); }
        return interrupted;
    }
}
