/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2015-2019, 2021 NextGIS, info@nextgis.com
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

import android.accounts.Account;
import android.accounts.AccountsException;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.text.Html;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.api.IProgressor;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.datasource.GeoGeometryFactory;
import com.nextgis.maplib.map.Layer;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.NGWLookupTable;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.map.TMSLayer;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.util.AccountUtil;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.LayerConfigUtil;
import com.nextgis.maplib.util.ProdLogUtil;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplib.util.HttpResponse;
import com.nextgis.maplib.util.GeoJSONUtil;
import com.nextgis.maplib.util.NGException;
import com.nextgis.maplib.util.NGWUtil;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.mapui.LocalTMSLayerUI;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.LayerUtil;
import com.hypertrack.hyperlog.HyperLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HttpsURLConnection;

import static com.nextgis.maplib.util.Constants.MESSAGE_ALERT_INTENT;
import static com.nextgis.maplib.util.Constants.MESSAGE_EXTRA;
import static com.nextgis.maplib.util.Constants.MESSAGE_EXTRA_IS_PARENTFILL;
import static com.nextgis.maplib.util.Constants.MESSAGE_TITLE_EXTRA;
import static com.nextgis.maplib.util.NetworkUtil.configureSSLdefault;
import static com.nextgis.maplib.util.NetworkUtil.getUserAgent;
import static com.nextgis.maplibui.util.ConstantsUI.FILE_FORM;

/**
 * Service for filling layers with data
 */
public class LayerFillService extends Service implements IProgressor {
    /** Substring to search in Logcat together with tag {@code nextgismobile}. */
    public static final String LOG_LAYER_CONFIG = "NGWLayerConfig";

    /**
     * Low-importance channel for mandatory {@link #startForeground(int, android.app.Notification)} only;
     * progress is shown in-app, not in the status bar.
     */
    private static final String LAYER_FILL_FGS_CHANNEL_ID = "layer_fill_fgs_min";

    protected NotificationManager mNotifyManager;
    protected List<LayerFillTask> mQueue;
    protected static final int FILL_NOTIFICATION_ID = 9;
    protected NotificationCompat.Builder mBuilder;
    protected String mNotifyTitle;

    public final static int VECTOR_LAYER           = 1;
    public final static int VECTOR_LAYER_WITH_FORM = 2;
    public final static int TMS_LAYER              = 3;
    public final static int NGW_LAYER              = 4;

    public static final String ACTION_STOP = "com.nextgis.maplibui.FILL_LAYER_STOP";
    public static final String ACTION_ADD_TASK = "com.nextgis.maplibui.ADD_FILL_LAYER_TASK";
    /**
     * One {@link #startForegroundService} for the whole import/fill batch (collector or standalone),
     * so a single FGS start is paired with the single stopSelf() at drain end. Previously each task
     * was a separate startService delivery, which raced with stopSelf and could trip the
     * foreground-service lifecycle (ForegroundServiceDidNotStopInTimeException).
     */
    public static final String ACTION_ADD_BATCH = "com.nextgis.maplibui.ADD_FILL_BATCH";
    /**
     * One {@link #startForegroundService} for many repair tasks (avoids FGS race when finalize posts
     * multiple starts before the worker observes the queue).
     */
    public static final String ACTION_ADD_REPAIR_BATCH = "com.nextgis.maplibui.ADD_FILL_REPAIR_BATCH";
    public static final String ACTION_SHOW = "com.nextgis.maplibui.SHOW_PROGRESS_DIALOG";
    public static final String ACTION_UPDATE = "com.nextgis.maplibui.UPDATE_FILL_LAYER_PROGRESS";
    public static final String KEY_STATUS = "status";
    public static final String KEY_PROGRESS = "progress";
    public static final String KEY_TOTAL = "count";
    public static final String KEY_TITLE = "title";
    public static final String KEY_MESSAGE = "message";
    /** Snapshot for {@link #ACTION_SHOW} / reopening progress UI while fill is running. */
    public static final String KEY_INDETERMINATE = "indeterminate";
    public static final String IS_POINTS = "is_points";
    public static final String KEY_CANCELLED = "cancel";
    public static final String KEY_RESULT = "result";
    public static final String KEY_SYNC = "sync";
    /** True when mobile/NGW layer JSON (description) was applied via {@link NGWVectorLayer#fromJSON}; do not force {@code SYNC_ALL} in UI. */
    public static final String KEY_MOBILE_LAYER_CONFIG_APPLIED = "mobile_layer_config_applied";
    public static final String KEY_URI = "uri";
    public static final String KEY_DEFAULT_FORM_IDS = "default_form_ids"; // id of form to donwload
    public static final String KEY_START_LAYER_FILL = "start_layer_fill";
    public static final String KEY_PATH = "path";
    public static final String KEY_LAYER_PATH = "layer_path";
    public static final String KEY_MIN_ZOOM = "min_zoom";
    public static final String KEY_MAX_ZOOM = "max_zoom";
    public static final String KEY_VISIBLE = "visible";
    public static final String KEY_REMOTE_ID = "remote_id";
    /**
     * Map {@link com.nextgis.maplib.api.ILayer#getId()} after a successful local vector fill; used for post-drain verify/repair.
     */
    public static final String KEY_STANDALONE_VERIFY_LAYER_ID = "standalone_verify_layer_id";
    public static final String KEY_LOOKUP_ID = "lookup_id";
    public static final String KEY_ACCOUNT = "account";
    public static final String KEY_NAME = "name";
    public static final String KEY_INPUT_TYPE = "input_type";
    public static final String KEY_DELETE_SRC_FILE = "delete_source_file";
    public static final String KEY_LAYER_GROUP_ID = "layer_group_id";
    /**
     * When true on an {@link #ACTION_ADD_TASK} intent, heavy MapLibre reload after each layer is
     * deferred until the fill queue drains (see {@link IGISApplication#requestMapReloadAfterLayerFillBatch()}).
     */
    public static final String KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY = "defer_map_reload_until_queue_empty";
    /**
     * After a destructive rebuild (e.g. NGW schema mismatch), insert the filled layer at this index in the
     * target {@link #KEY_LAYER_GROUP_ID} group instead of appending. Captured before the old layer is removed.
     */
    public static final String KEY_LAYER_RESTORE_INSERT_INDEX = "layer_restore_insert_index";
    /**
     * On {@link #STATUS_STOP}: do not dismiss blocking progress UI yet; collector verify/repair may enqueue more tasks.
     */
    public static final String KEY_KEEP_PROGRESS_UI_BLOCKING = "keep_progress_ui_blocking";
    /**
     * On {@link #STATUS_STOP}: session fully finished (queue drained and finalize ran); dismiss blocking UI without per-layer toast.
     */
    public static final String KEY_COLLECTOR_SESSION_UI_COMPLETE = "collector_session_ui_complete";
    public static final String KEY_SUPPRESS_STOP_TOAST = "suppress_stop_toast";
    /** Full layer config.json text (e.g. from NGW resource description) applied after NGW fill. */
    public static final String KEY_LAYER_CONFIG_JSON = "layer_config_json";

    /** Collector layer id for batch verification (stable across UnzipForm → NGW fill when meta overrides resource id). */
    public static final String KEY_COLLECTOR_TRACKING_REMOTE_ID = "collector_tracking_remote_id";
    /** Index in full collector project vector list (all layers, not only this download batch). */
    public static final String KEY_COLLECTOR_ORDER_INDEX = "collector_order_index";
    /** All collector vector remote ids in project order (same on each task). */
    public static final String KEY_COLLECTOR_PROJECT_REMOTE_IDS = "collector_project_remote_ids";
    /** Collector project item «Редактируемый» (not layer description {@code is_editable}). */
    public static final String KEY_COLLECTOR_LAYER_EDITABLE = "collector_layer_editable";
    public static final String KEY_TMS_TYPE   = "tms_type";
    public static final String KEY_TMS_CACHE   = "tms_cache";

    /** {@link ArrayList}{@code <}{@link Bundle}{@code >} — each bundle matches {@link #ACTION_ADD_TASK} extras for one task. */
    public static final String KEY_REPAIR_BATCH_EXTRAS = "repair_batch_extras";
    /** {@link ArrayList}{@code <}{@link Bundle}{@code >} for {@link #ACTION_ADD_BATCH} — one bundle per task. */
    public static final String KEY_BATCH_EXTRAS = "fill_batch_extras";

    public static final String NGFP_META = "ngfp_meta.json";
    protected final static String NGFP_FILE_META = "meta.json";
    protected final static String NGFP_FILE_DATA = "data.geojson";


    public static final short STATUS_START = 0;
    public static final short STATUS_UPDATE = 1;
    public static final short STATUS_STOP = 2;
    public static final short STATUS_SHOW = 3;

    protected int mProgressMax;
    protected int mProgressValue;
    protected LayerGroup mLayerGroup;
    protected long mLastUpdate = 0;
    protected String mProgressMessage;
    protected boolean isPointz = false;
    protected boolean mIndeterminate;
    protected boolean mIsCanceled;
    protected Handler mHandler;
    protected Intent mProgressIntent;

    protected static final String BUNDLE_MSG_KEY = "error_message";

    private final Object mQueueLock = new Object();
    private ExecutorService mWorkerExecutor;
    private volatile boolean mDrainRunning;

    /**
     * Set in {@link #onCreate}, cleared in {@link #onDestroy} when still this instance — used to enqueue repair
     * synchronously from {@link IGISApplication#finalizeCollectorImportVerifyAndRepairIfNeeded()} without a second FGS start.
     */
    private static volatile LayerFillService sActiveInstance;
    /** Keeps CPU running while the screen is off so HTTP download + SQLite fill are not stalled by device sleep. */
    private PowerManager.WakeLock mFillWakeLock;

    private void acquireFillWakeLock() {
        synchronized (mQueueLock) {
            if (mFillWakeLock != null && mFillWakeLock.isHeld()) {
                return;
            }
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
            return;
        }
        PowerManager.WakeLock lock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "nextgis:LayerFillService");
        lock.setReferenceCounted(false);
        lock.acquire();
        synchronized (mQueueLock) {
            mFillWakeLock = lock;
        }
    }

    private void releaseFillWakeLock() {
        PowerManager.WakeLock lock;
        synchronized (mQueueLock) {
            lock = mFillWakeLock;
            mFillWakeLock = null;
        }
        if (lock != null && lock.isHeld()) {
            try {
                lock.release();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void enqueueToQueue(LayerFillTask task) {
        synchronized (mQueueLock) {
            mQueue.add(task);
        }
    }

    /**
     * Enqueue a single fill task from the same extras shape as {@link #ACTION_ADD_TASK}.
     *
     * @return {@code false} only if {@link UnzipForm} construction failed (legacy: {@code START_NOT_STICKY}, no drain).
     */
    /**
     * Enqueue repair tasks built during finalize while the drain worker awaits the main-thread latch.
     * Must run on the main thread; see {@link #tryEnqueueRepairBatchOnActiveInstance}.
     */
    public static boolean tryEnqueueRepairBatchOnActiveInstance(
            Context appContext, ArrayList<Bundle> repairBundles, boolean deferMapReload) {
        if (repairBundles == null || repairBundles.isEmpty()) {
            return false;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            HyperLog.w(Constants.TAG, "tryEnqueueRepairBatchOnActiveInstance: not on main thread");
            return false;
        }
        LayerFillService svc = sActiveInstance;
        if (svc == null) {
            return false;
        }
        synchronized (svc.mQueueLock) {
            if (svc.mIsCanceled) {
                return false;
            }
        }
        if (svc.mWorkerExecutor == null || svc.mWorkerExecutor.isShutdown()) {
            return false;
        }
        IGISApplication app = (IGISApplication) appContext.getApplicationContext();
        if (deferMapReload) {
            app.setLayerFillBatchDeferringHeavyMapReload(true);
        }
        boolean anyEnqueued = false;
        for (Bundle b : repairBundles) {
            if (svc.enqueueOneTaskFromExtras(b)) {
                anyEnqueued = true;
            }
        }
        if (!anyEnqueued) {
            return false;
        }
        svc.startForegroundWithSessionAwareNotification();
        /* Do not call scheduleDrainIfNeeded(): the drain worker is blocked in finalize await and will
         * re-check mQueue and submit exactly one continuation of drainLoop (see finally after await). */
        return true;
    }

    /**
     * Start the whole import/fill batch with a SINGLE {@link #startForegroundService}, instead of one
     * startForegroundService + N-1 startService deliveries. One FGS start paired with the single
     * stopSelf() at drain end avoids the foreground-service lifecycle race.
     *
     * @param taskIntents per-task intents (only their extras are used; action/component ignored).
     */
    public static void startFillBatch(Context context, ArrayList<Intent> taskIntents) {
        if (context == null || taskIntents == null || taskIntents.isEmpty()) {
            return;
        }
        ArrayList<Bundle> batch = new ArrayList<>(taskIntents.size());
        boolean deferMapReload = false;
        for (Intent it : taskIntents) {
            if (it == null) {
                continue;
            }
            Bundle extras = it.getExtras();
            if (extras != null) {
                batch.add(extras);
                if (extras.getBoolean(KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, false)) {
                    deferMapReload = true;
                }
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        Intent batchIntent = new Intent(context, LayerFillService.class);
        batchIntent.setAction(ACTION_ADD_BATCH);
        if (deferMapReload) {
            batchIntent.putExtra(KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, true);
        }
        batchIntent.putParcelableArrayListExtra(KEY_BATCH_EXTRAS, batch);
        ContextCompat.startForegroundService(context, batchIntent);
    }

    private boolean enqueueOneTaskFromExtras(Bundle extra) {
        if (extra == null) {
            return true;
        }
        Bundle work = new Bundle(extra);
        int layerGroupId = work.getInt(KEY_LAYER_GROUP_ID, Constants.NOT_FOUND);
        MapBase mapBase = MapBase.getInstance();
        ILayer groupLayer = mapBase != null ? mapBase.getLayerById(layerGroupId) : null;
        if (!(groupLayer instanceof LayerGroup)) {
            // Layer group gone (map reset / corrupt config). Skip this task instead of letting the
            // worker NPE on a null mLayerGroup and stall the whole drain.
            HyperLog.w(Constants.TAG, "LayerFillService: layer group not found id=" + layerGroupId
                    + ", skipping fill task");
            return true;
        }
        mLayerGroup = (LayerGroup) groupLayer;
        int layerType = work.getInt(KEY_INPUT_TYPE, Constants.NOT_FOUND);
        switch (layerType) {
            case VECTOR_LAYER:
                enqueueToQueue(new VectorLayerFillTask(work));
                return true;
            case VECTOR_LAYER_WITH_FORM:
                try {
                    enqueueToQueue(new UnzipForm(work));
                    return true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Intent msg = new Intent(MESSAGE_ALERT_INTENT);
                    msg.putExtra(MESSAGE_EXTRA, getString(R.string.error_load_parent));
                    msg.putExtra(MESSAGE_EXTRA_IS_PARENTFILL, true);
                    msg.putExtra(MESSAGE_TITLE_EXTRA, getResources().getString(R.string.error));
                    msg.setPackage(getPackageName());
                    sendBroadcast(msg);
                    return false;
                }
            case TMS_LAYER:
                enqueueToQueue(new LocalTMSFillTask(work));
                return true;
            case NGW_LAYER:
                enqueueToQueue(new NGWVectorLayerFillTask(work));
                return true;
            default:
                HyperLog.w(Constants.TAG, "LayerFillService: unknown KEY_INPUT_TYPE=" + layerType);
                return true;
        }
    }

    private void scheduleDrainIfNeeded() {
        synchronized (mQueueLock) {
            if (mDrainRunning || mQueue.isEmpty()) {
                return;
            }
            mDrainRunning = true;
            ((IGISApplication) getApplicationContext()).setLayerFillServiceBusy(true);
        }
        mWorkerExecutor.execute(this::drainLoop);
    }

    private void drainLoop() {
        acquireFillWakeLock();
        try {
            while (true) {
                final LayerFillTask task;
                synchronized (mQueueLock) {
                    if (mIsCanceled) {
                        mQueue.clear();
                    }
                    if (mQueue.isEmpty()) {
                        break;
                    }
                    task = mQueue.remove(0);
                }
                runSingleFillTask(task);
            }
        } finally {
            synchronized (mQueueLock) {
                mDrainRunning = false;
                if (!mQueue.isEmpty()) {
                    mDrainRunning = true;
                    mWorkerExecutor.execute(this::drainLoop);
                    return;
                }
            }

            IGISApplication app = (IGISApplication) getApplicationContext();

            if (mIsCanceled) {
                releaseFillWakeLock();
                app.setLayerFillServiceBusy(false);
                app.clearCollectorImportBatch();
                app.setLayerFillBatchDeferringHeavyMapReload(false);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    stopForeground(true);
                } else {
                    mNotifyManager.cancel(FILL_NOTIFICATION_ID);
                }
                stopSelf();
                return;
            }

            final boolean deferReloadPending = app.isLayerFillBatchDeferringHeavyMapReload();
            final boolean hadCollectorBatchBeforeFinalize = app.hasCollectorImportBatchRegistered();

            final CountDownLatch finalizeLatch = new CountDownLatch(1);
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    app.finalizeCollectorImportVerifyAndRepairIfNeeded();
                    app.finalizeStandaloneLayerFillVerifyIfNeeded();
                } finally {
                    finalizeLatch.countDown();
                }
            });

            boolean finalizedInTime = false;
            try {
                finalizedInTime = finalizeLatch.await(120, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!finalizedInTime) {
                HyperLog.w(Constants.TAG, "LayerFillService: finalizeCollector/standalone await timeout");
            }

            synchronized (mQueueLock) {
                if (!mQueue.isEmpty()) {
                    mDrainRunning = true;
                    mWorkerExecutor.execute(this::drainLoop);
                    return;
                }
            }

            boolean reload;
            synchronized (mQueueLock) {
                reload = mQueue.isEmpty() && app.isLayerFillBatchDeferringHeavyMapReload();
            }
            if (reload) {
                app.setLayerFillBatchDeferringHeavyMapReload(false);
                app.requestMapReloadAfterLayerFillBatch();
            }

            releaseFillWakeLock();
            app.setLayerFillServiceBusy(false);

            boolean collectorBatchEnded = hadCollectorBatchBeforeFinalize
                    && !app.hasCollectorImportBatchRegistered();
            if (deferReloadPending || collectorBatchEnded) {
                Intent sessionDone = new Intent(ACTION_UPDATE);
                sessionDone.putExtra(KEY_STATUS, STATUS_STOP);
                sessionDone.putExtra(KEY_TOTAL, 0);
                sessionDone.putExtra(KEY_COLLECTOR_SESSION_UI_COMPLETE, true);
                sessionDone.putExtra(KEY_SUPPRESS_STOP_TOAST, true);
                final Context appContext = getApplicationContext();
                final String pkg = getPackageName();
                sessionDone.setPackage(pkg);
                new Handler(Looper.getMainLooper()).post(() -> appContext.sendBroadcast(sessionDone));
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                stopForeground(true);
            } else {
                mNotifyManager.cancel(FILL_NOTIFICATION_ID);
            }
            stopSelf();
        }
    }

    private void runSingleFillTask(LayerFillTask task) {
        if (mIsCanceled) {
            return;
        }

        mNotifyTitle = task.getDescription();

        if (mProgressIntent.getExtras() != null) {
            mProgressIntent.getExtras().clear();
        }

        mProgressIntent.putExtra(KEY_STATUS, STATUS_START)
                .putExtra(KEY_TITLE, mNotifyTitle)
                .setPackage(getPackageName());
        sendBroadcast(mProgressIntent);

        Process.setThreadPriority(Constants.DEFAULT_DOWNLOAD_THREAD_PRIORITY);
        final IProgressor progressor = this;
        /* Next progress update must not be skipped (throttle carries mLastUpdate across tasks). */
        mLastUpdate = 0L;
        progressor.setValue(0);
        boolean result = task.execute(progressor);
        if (!result && !mIsCanceled) {
            HyperLog.w(Constants.TAG, "LayerFillService: fill task failed "
                    + task.getClass().getSimpleName() + " — " + mProgressMessage);
        }

        if ((!(task instanceof UnzipForm)) || !task.subTaskWasRunned) {
            mProgressIntent.putExtra(KEY_MESSAGE, mProgressMessage);
        }

        mProgressIntent.putExtra(KEY_STATUS, STATUS_STOP);
        mProgressIntent.putExtra(KEY_CANCELLED, mIsCanceled);
        mProgressIntent.putExtra(KEY_RESULT, result && !mIsCanceled);
        IGISApplication appCtx = (IGISApplication) getApplicationContext();
        int remainingQueue;
        synchronized (mQueueLock) {
            remainingQueue = mQueue.size();
            mProgressIntent.putExtra(KEY_TOTAL, remainingQueue);
        }
        mProgressIntent.putExtra(KEY_KEEP_PROGRESS_UI_BLOCKING,
                remainingQueue == 0
                        && !mIsCanceled
                        && appCtx.isLayerFillBatchDeferringHeavyMapReload()
                        && appCtx.hasCollectorImportBatchRegistered());
        boolean suppressPerLayerToast = remainingQueue > 0
                || (!mIsCanceled
                        && appCtx.isLayerFillBatchDeferringHeavyMapReload()
                        && appCtx.hasCollectorImportBatchRegistered());
        mProgressIntent.putExtra(KEY_SUPPRESS_STOP_TOAST, suppressPerLayerToast);

        if (result) {
            ILayer filled = task.getLayer();
            if (task instanceof LocalTMSFillTask && ((LocalTMSFillTask) task).mIsNgrc) {
                /* Above OSM when present (LayerGroup index 0 = bottom of stack). No OSM → index 0. */
                final String osmPathName = "osm";
                ILayer osm = mLayerGroup.getLayerByPathName(osmPathName);
                int insertAt = 0;
                if (osm != null) {
                    int osmIdx = mLayerGroup.getChildLayerIndex(osm);
                    insertAt = osmIdx >= 0 ? osmIdx + 1 : 0;
                }
                mLayerGroup.insertLayer(insertAt, filled);
            } else if (task.mCollectorOrderIndex >= 0 && task.mCollectorProjectRemoteIds != null
                    && filled instanceof NGWVectorLayer) {
                NGWVectorLayer nv = (NGWVectorLayer) filled;
                int insertAt = LayerGroup.computeCollectorOrderedInsertIndex(
                        mLayerGroup,
                        nv.getAccountName(),
                        task.mCollectorProjectRemoteIds,
                        task.mCollectorOrderIndex);
                mLayerGroup.insertLayer(insertAt, filled);
            } else if (task.mLayerRestoreInsertIndex >= 0) {
                int insertAt = Math.min(task.mLayerRestoreInsertIndex, mLayerGroup.getLayerCount());
                mLayerGroup.insertLayer(insertAt, filled);
            } else {
                mLayerGroup.addLayer(filled);
            }
            mLayerGroup.save();
            registerStandaloneLayerFillVerifyIfNeeded(task, filled);
        } else {
            task.cancel();
        }

        if (task instanceof NGWVectorLayerFillTask) {
            NGWVectorLayerFillTask ngwTask = (NGWVectorLayerFillTask) task;
            appCtx.notifyCollectorLayerFillResult(ngwTask.getTrackingRemoteId(), result && !mIsCanceled);
        } else if (task instanceof UnzipForm) {
            UnzipForm uf = (UnzipForm) task;
            if (!result || mIsCanceled) {
                appCtx.notifyCollectorLayerFillResult(uf.mRemoteId, false);
            }
        }

        if (task instanceof NGWVectorLayerFillTask) {
            NGWVectorLayerFillTask ngwTask = (NGWVectorLayerFillTask) task;
            mProgressIntent.putExtra(KEY_SYNC, ngwTask.showSyncDialog());
            mProgressIntent.putExtra(KEY_ACCOUNT, ngwTask.getAccountName());
            mProgressIntent.putExtra(KEY_REMOTE_ID, task.getLayer().getId());
            mProgressIntent.putExtra(KEY_MOBILE_LAYER_CONFIG_APPLIED, ngwTask.wasMobileLayerConfigApplied());
        }
        mProgressIntent.setPackage(getPackageName());

        sendBroadcast(mProgressIntent);
    }

    /**
     * Standalone fills (not collector batch) register for the same post-drain verify/repair pass as collector projects.
     */
    private void registerStandaloneLayerFillVerifyIfNeeded(LayerFillTask task, ILayer filled) {
        if (mIsCanceled
                || (task.mCollectorOrderIndex >= 0 && task.mCollectorProjectRemoteIds != null)) {
            return;
        }
        if (!(filled instanceof VectorLayer)) {
            return;
        }
        IGISApplication app = (IGISApplication) getApplicationContext();
        Bundle copy = new Bundle(task.mEnqueueBundle);
        if (filled instanceof NGWVectorLayer) {
            app.registerStandaloneLayerFillVerifyAfterSuccess(copy);
            return;
        }
        if (task instanceof VectorLayerFillTask) {
            copy.putInt(KEY_STANDALONE_VERIFY_LAYER_ID, filled.getId());
            app.registerStandaloneLayerFillVerifyAfterSuccess(copy);
        }
    }

    @Override
    public void onCreate() {
        mNotifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        int icon = R.drawable.ic_notification_download;

        mProgressIntent = new Intent(ACTION_UPDATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    LAYER_FILL_FGS_CHANNEL_ID,
                    getString(com.nextgis.maplib.R.string.start_fill_layer),
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableLights(false);
            ch.enableVibration(false);
            mNotifyManager.createNotificationChannel(ch);
            mBuilder = new NotificationCompat.Builder(this, LAYER_FILL_FGS_CHANNEL_ID);
        } else {
            mBuilder = new NotificationCompat.Builder(this);
        }
        mBuilder.setSmallIcon(icon)
                .setContentTitle(getString(com.nextgis.maplib.R.string.start_fill_layer))
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mBuilder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }
        mIsCanceled = false;

        mQueue = new LinkedList<>();
        mWorkerExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "LayerFillWorker"));
        mHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                Bundle resultData = msg.getData();
                String err = resultData.getString(BUNDLE_MSG_KEY);
                HyperLog.w(Constants.TAG, "LayerFillService user error toast: "
                        + ProdLogUtil.truncateForLog(err, 800));
                Toast.makeText(LayerFillService.this, err, Toast.LENGTH_LONG).show();
            }
        };

        startForegroundWithSessionAwareNotification();

        sActiveInstance = this;
    }

    /**
     * During collector batch import (modal progress in app), use a minimal FGS title so the system
     * notification does not repeat long “start fill” strings; progress stays in the dialog.
     */
    private void applyForegroundNotificationTitleForSession() {
        if (mBuilder == null) {
            return;
        }
        IGISApplication app = (IGISApplication) getApplicationContext();
        if (app.isLayerFillBatchDeferringHeavyMapReload() && app.hasCollectorImportBatchRegistered()) {
            mBuilder.setContentTitle(getString(R.string.layer_fill_fgs_minimal)).setContentText(null);
        } else {
            mBuilder.setContentTitle(getString(com.nextgis.maplib.R.string.start_fill_layer))
                    .setContentText(null);
        }
    }

    private void startForegroundWithSessionAwareNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applyForegroundNotificationTitleForSession();
            startForegroundDataSync();
        }
    }

    /**
     * Calls {@code startForeground} with the explicit {@code dataSync} type on Q+ (matches the
     * manifest declaration and {@link TileDownloadService}); falls back to the untyped form on O.
     */
    private void startForegroundDataSync() {
        if (mBuilder == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FILL_NOTIFICATION_ID, mBuilder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(FILL_NOTIFICATION_ID, mBuilder.build());
        }
    }

    /** After progress updates, refresh FGS text when collector batch uses quiet title. */
    private void refreshForegroundNotificationIfCollectorBatch() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || mBuilder == null) {
            return;
        }
        IGISApplication app = (IGISApplication) getApplicationContext();
        if (app.isLayerFillBatchDeferringHeavyMapReload() && app.hasCollectorImportBatchRegistered()) {
            applyForegroundNotificationTitleForSession();
            startForegroundDataSync();
        }
    }

    @Override
    public void onDestroy() {
        HyperLog.v(Constants.TAG, "LayerFillService.onDestroy");
        if (sActiveInstance == this) {
            sActiveInstance = null;
        }
        releaseFillWakeLock();
        ((IGISApplication) getApplicationContext()).setLayerFillServiceBusy(false);
        if (mWorkerExecutor != null) {
            mWorkerExecutor.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        HyperLog.v(Constants.TAG, "LayerFillService.onStartCommand startId=" + startId);
        if (Constants.DEBUG_MODE)
            Log.i("LayerFillService", "Received start id " + startId + ": " + intent);

        /*
         * Android 8+: every startForegroundService() delivery must call startForeground() quickly,
         * including ACTION_SHOW, null intent redelivery, or a race right after stopForeground/stopSelf.
         * Apply intent-specific notification text after we process defer flags in each branch.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mBuilder != null) {
            startForegroundDataSync();
        }

        if (intent != null) {
            String action = intent.getAction();
            if (action != null && !TextUtils.isEmpty(action)) {
                switch (action) {
                    case ACTION_ADD_TASK:
                        if (intent.getBooleanExtra(KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, false)) {
                            ((IGISApplication) getApplicationContext())
                                    .setLayerFillBatchDeferringHeavyMapReload(true);
                        }
                        startForegroundWithSessionAwareNotification();
                        if (!enqueueOneTaskFromExtras(intent.getExtras())) {
                            return START_NOT_STICKY;
                        }
                        scheduleDrainIfNeeded();
                        return START_STICKY;
                    case ACTION_ADD_BATCH:
                        if (intent.getBooleanExtra(KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, false)) {
                            ((IGISApplication) getApplicationContext())
                                    .setLayerFillBatchDeferringHeavyMapReload(true);
                        }
                        startForegroundWithSessionAwareNotification();
                        ArrayList<Bundle> fillBatch = intent.getParcelableArrayListExtra(KEY_BATCH_EXTRAS);
                        if (fillBatch != null) {
                            for (Bundle b : fillBatch) {
                                enqueueOneTaskFromExtras(b);
                            }
                        }
                        scheduleDrainIfNeeded();
                        return START_STICKY;
                    case ACTION_ADD_REPAIR_BATCH:
                        if (intent.getBooleanExtra(KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, false)) {
                            ((IGISApplication) getApplicationContext())
                                    .setLayerFillBatchDeferringHeavyMapReload(true);
                        }
                        startForegroundWithSessionAwareNotification();
                        ArrayList<Bundle> repairBatch = intent.getParcelableArrayListExtra(KEY_REPAIR_BATCH_EXTRAS);
                        if (repairBatch != null) {
                            for (Bundle b : repairBatch) {
                                enqueueOneTaskFromExtras(b);
                            }
                        }
                        scheduleDrainIfNeeded();
                        return START_STICKY;
                    case ACTION_STOP:
                        synchronized (mQueueLock) {
                            mQueue.clear();
                            mIsCanceled = true;
                            if (!mDrainRunning) {
                                IGISApplication appStop = (IGISApplication) getApplicationContext();
                                appStop.clearCollectorImportBatch();
                                appStop.setLayerFillServiceBusy(false);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    stopForeground(true);
                                } else {
                                    mNotifyManager.cancel(FILL_NOTIFICATION_ID);
                                }
                                stopSelf();
                            }
                        }
                        break;
                    case ACTION_SHOW:
                        if (mProgressIntent.getExtras() != null) {
                            mProgressIntent.getExtras().clear();
                        }
                        mProgressIntent.putExtra(KEY_STATUS, STATUS_SHOW)
                                .putExtra(KEY_TITLE, mNotifyTitle != null ? mNotifyTitle : "")
                                .putExtra(KEY_INDETERMINATE, mIndeterminate)
                                .putExtra(KEY_TOTAL, mProgressMax)
                                .putExtra(KEY_PROGRESS, mProgressValue);
                        if (mProgressMessage != null) {
                            mProgressIntent.putExtra(KEY_MESSAGE, mProgressMessage);
                        }
                        if (isPointz) {
                            mProgressIntent.putExtra(IS_POINTS, true);
                        }
                        mProgressIntent.setPackage(getPackageName());
                        sendBroadcast(mProgressIntent);
                        break;
                }
            }
        }
        return START_STICKY;
    }

    /**
     * Config JSON from intent extra, or from NGW resource {@code description} (full GET).
     */
    private String resolveImportedLayerConfigJson(
            NGWVectorLayer layer,
            String fromIntentExtra)
    {
        if (!TextUtils.isEmpty(fromIntentExtra)) {
            String t = fromIntentExtra.trim();
            if ("null".equalsIgnoreCase(t)) {
                Log.w(Constants.TAG, LOG_LAYER_CONFIG + " intent extra is literal \"null\" — skipped");
                HyperLog.w(Constants.TAG, LOG_LAYER_CONFIG + " intent extra literal null — skipped");
                return null;
            }
            Log.i(Constants.TAG, LOG_LAYER_CONFIG + " using intent extra, chars=" + t.length());
            HyperLog.d(Constants.TAG, LOG_LAYER_CONFIG + " intent extra length=" + t.length());
            return t;
        }
        try {
            AccountUtil.AccountData ad =
                    AccountUtil.getAccountData(getApplicationContext(), layer.getAccountName());
            String url = NGWUtil.getResourceUrl(ad.url, layer.getRemoteId());
            Log.i(Constants.TAG, LOG_LAYER_CONFIG + " fetching description GET resource id="
                    + layer.getRemoteId() + " account=" + layer.getAccountName());
            HyperLog.d(Constants.TAG, LOG_LAYER_CONFIG + " GET " + url);
            HttpResponse response = NetworkUtil.get(url, ad.login, ad.password, false);
            if (!response.isOk()) {
                Log.w(Constants.TAG, LOG_LAYER_CONFIG + " HTTP " + response.getResponseCode());
                HyperLog.w(Constants.TAG, LOG_LAYER_CONFIG + " HTTP " + response.getResponseCode());
                return null;
            }
            JSONObject root = new JSONObject(response.getResponseBody());
            String d = extractNgwResourceDescriptionJson(root);
            if (TextUtils.isEmpty(d)) {
                Log.w(Constants.TAG, LOG_LAYER_CONFIG + " empty description in API response (check NGW resource → Description)");
                HyperLog.w(Constants.TAG, LOG_LAYER_CONFIG + " description empty after GET");
                return null;
            }
            Log.i(Constants.TAG, LOG_LAYER_CONFIG + " from NGW description, chars=" + d.length());
            HyperLog.d(Constants.TAG, LOG_LAYER_CONFIG + " NGW description length=" + d.length());
            return d.trim();
        } catch (Exception e) {
            Log.e(Constants.TAG, LOG_LAYER_CONFIG + " resolve failed", e);
            HyperLog.exception(Constants.TAG, e);
            return null;
        }
    }

    private static String extractNgwResourceDescriptionJson(JSONObject root) {
        return LayerConfigUtil.extractNgwResourceDescriptionJson(root);
    }

    private static JSONObject parseLayerConfigObject(String raw) throws JSONException {
        return LayerConfigUtil.parseLayerConfigObject(raw);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void setMax(int maxValue) {
        mProgressMax = maxValue;
    }

    @Override
    public boolean isCanceled() {
        return mIsCanceled;
    }

    @Override
    public void setValue(int value) {
        mProgressValue = value;
        updateNotify();
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {
        mIndeterminate = indeterminate;
        updateNotify();
    }

    @Override
    public void setMessage(String message) {
        mProgressMessage = message;
        updateNotify();
    }

    /** Throttles progress broadcast to the UI (same interval as {@link ConstantsUI#NOTIFICATION_DELAY}). */
    protected void updateNotify(){
        final long now = System.currentTimeMillis();
        if (mLastUpdate + ConstantsUI.NOTIFICATION_DELAY >= now) {
            return;
        }
        mLastUpdate = now;

        if (mProgressIntent.getExtras() != null)
            mProgressIntent.getExtras().clear();
        if (mProgressMessage != null)
            mProgressIntent.putExtra(KEY_MESSAGE, mProgressMessage);
        else
            mProgressIntent.removeExtra(KEY_MESSAGE);

        mProgressIntent.putExtra(KEY_STATUS, STATUS_UPDATE).putExtra(KEY_TOTAL, mProgressMax)
                .putExtra(KEY_PROGRESS, mProgressValue);

        if (isPointz)
            mProgressIntent.putExtra(IS_POINTS, true);

        mProgressIntent.setPackage(getPackageName());
        sendBroadcast(mProgressIntent);
        refreshForegroundNotificationIfCollectorBatch();
    }

    private void notifyError(String error) {
        Bundle bundle = new Bundle();
        bundle.putString(BUNDLE_MSG_KEY, error);

        Message msg = new Message();
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * task classes
     */

    private abstract class LayerFillTask{
        String mLayerName;
        File mLayerPath;
        float mMinZoom, mMaxZoom;
        boolean mVisible;
        Uri mUri;
        protected Layer mLayer;
        /** Copy of {@link #ACTION_ADD_TASK} extras for verify/repair re-queue. */
        protected final Bundle mEnqueueBundle;
        public boolean subTaskWasRunned = true;
        public long[] defaultFormIDArray = null;
        protected String mLayerConfigJson;
        protected int mCollectorOrderIndex = -1;
        protected long[] mCollectorProjectRemoteIds;
        /** {@code >= 0}: insert filled layer at this index in {@link #mLayerGroup}; {@code -1}: append via {@link LayerGroup#addLayer}. */
        protected int mLayerRestoreInsertIndex = -1;

        LayerFillTask(Bundle bundle) {
            mEnqueueBundle = new Bundle(bundle);
            mUri = bundle.getParcelable(KEY_URI);
            mLayerName = bundle.getString(KEY_NAME);
            mLayerPath = bundle.containsKey(KEY_LAYER_PATH) ?
                    (File) bundle.getSerializable(KEY_LAYER_PATH) :
                    mLayerGroup.createLayerStorage();
            mMinZoom = bundle.getFloat(KEY_MIN_ZOOM, GeoConstants.DEFAULT_MIN_ZOOM);
            mMaxZoom = bundle.getFloat(KEY_MAX_ZOOM, GeoConstants.DEFAULT_MAX_ZOOM);
            mVisible = bundle.getBoolean(KEY_VISIBLE, true);
            mLayerConfigJson = bundle.getString(KEY_LAYER_CONFIG_JSON);

            if (bundle.containsKey(KEY_COLLECTOR_ORDER_INDEX)) {
                mCollectorOrderIndex = bundle.getInt(KEY_COLLECTOR_ORDER_INDEX);
                mCollectorProjectRemoteIds = bundle.getLongArray(KEY_COLLECTOR_PROJECT_REMOTE_IDS);
            }
            if (bundle.containsKey(KEY_LAYER_RESTORE_INSERT_INDEX)) {
                mLayerRestoreInsertIndex = bundle.getInt(KEY_LAYER_RESTORE_INSERT_INDEX, -1);
            }

            Serializable serializable = bundle.getSerializable(KEY_DEFAULT_FORM_IDS);
            if (serializable instanceof ArrayList<?>) {

                ArrayList<Long> idsList = (ArrayList<Long>) serializable;

                long[] idsArray = new long[idsList.size()];
                for (int i = 0; i < idsList.size(); i++) {
                    idsArray[i] = idsList.get(i);
                }

                defaultFormIDArray = idsArray;
            }

            //defaultFormIDArray = bundle.getSerializable(KEY_DEFAULT_FORM_IDS);
        }

        void initLayer() {
            mLayer.setName(mLayerName);
            mLayer.setVisible(mVisible);
            mLayer.setMinZoom(mMinZoom);
            mLayer.setMaxZoom(mMaxZoom);
        }

        public abstract boolean execute(IProgressor progressor);

        public String getDescription(){
            String name = mLayer != null ? mLayer.getName() : null;
            if (TextUtils.isEmpty(name)) {
                name = mLayerName;
            }
            if (TextUtils.isEmpty(name)) {
                return getString(R.string.processing);
            }
            return getString(R.string.processing) + " " + name;
        }

        public ILayer getLayer() {
            return mLayer;
        }

        public void cancel() {
            if (mLayer != null)
                mLayer.delete(true);
            else if (mLayerPath != null)
                FileUtil.deleteRecursive(mLayerPath);
        }

        void setError(String logMsg, IProgressor progressor) {
            if (null != logMsg) {
                if (null != progressor)
                    progressor.setMessage(logMsg);
                setMessage(logMsg);
            }
        }
    }

    private class VectorLayerFillTask extends LayerFillTask{
        VectorLayerFillTask(Bundle bundle) {
            super(bundle);
            mLayer = new VectorLayerUI(mLayerGroup.getContext(), mLayerPath);
            initLayer();
        }

        @Override
        public boolean execute(IProgressor progressor) {
            try {
                VectorLayer vectorLayer = (VectorLayer) mLayer;
                if(null == vectorLayer)
                    return false;

                vectorLayer.createFromGeoJson(mUri, progressor);
            } catch (IOException | JSONException | SQLiteException | NGException | ClassCastException e) {
                e.printStackTrace();
                setError(e.getLocalizedMessage(), progressor);
                notifyError(mProgressMessage);
                return false;
            }

            return true;
        }
    }

    private class UnzipForm extends LayerFillTask {
        boolean mSync;
        long mRemoteId;
        String mAccount;
        boolean startLayerFill; // false if fill second form from layer - no need to create layer

        UnzipForm(Bundle bundle) {
            super(bundle);
            mSync = bundle.getBoolean(KEY_SYNC, true);
            mRemoteId = bundle.getLong(KEY_REMOTE_ID, -1);
            mAccount = bundle.getString(KEY_ACCOUNT, "");
            startLayerFill = bundle.getBoolean(KEY_START_LAYER_FILL, true);
        }

        @Override
        public boolean execute(IProgressor progressor) {
            try {
                InputStream inputStream;
                String url = mUri.toString();
                if (NetworkUtil.isValidUri(url)) {
                    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

                    if (connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM && mUri.getScheme().equals("http")) {
                        url = url.replace("http", "https");
                        configureSSLdefault();
                        connection = (HttpsURLConnection) new URL(url).openConnection();
                    } else {
                        connection = (HttpURLConnection) new URL(url).openConnection();; // we need this due to getResponseCode() makes actual connection and it becomes immutable
                    }

                    connection.setRequestProperty("User-Agent",getUserAgent(Constants.MAPLIB_USER_AGENT_PART));

                    connection.setRequestProperty("connection", "keep-alive");
                    try {
                        AccountUtil.AccountData accountData = AccountUtil.getAccountData(getApplicationContext(), mAccount);
                        String basicAuth = NetworkUtil.getHTTPBaseAuth(accountData.login, accountData.password);
                        if (null != basicAuth)
                            connection.setRequestProperty ("Authorization", basicAuth);
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                        setError(e.getLocalizedMessage(), progressor);
                        notifyError(mProgressMessage);
                        return false;
                    }
//                    inputStream = new URL(url).openStream();
                    inputStream = connection.getInputStream();
                } else
                    inputStream = getContentResolver().openInputStream(mUri);

                if (inputStream != null) {
                    int nSize = inputStream.available();
                    int nIncrement = 0;
                    byte[] buffer = new byte[Constants.IO_BUFFER_SIZE];
                    progressor.setMax(nSize);
                    progressor.setMessage(getString(com.nextgis.maplib.R.string.message_loading));

                    ZipInputStream zis = new ZipInputStream(inputStream);
                    ZipEntry ze;
                    while ((ze = zis.getNextEntry()) != null) {
                        if (isCanceled())
                            return false;

                        FileUtil.unzipEntry(zis, ze, buffer, mLayerPath);
                        nIncrement += ze.getSize();
                        zis.closeEntry();
                        progressor.setValue(nIncrement);
                    }
                    zis.close();
                    progressor.setMessage(null);

                    //read meta.json

                    long defaultFormID = -1;
                    if (defaultFormIDArray != null && defaultFormIDArray.length > 0)
                        defaultFormID = defaultFormIDArray[0];

                    String formPrefix =  defaultFormID + "_";

                    File formFile = new File(mLayerPath, FILE_FORM); // rename formfile
                    formFile.renameTo(formFile = new File(formFile.getParentFile(), formPrefix + FILE_FORM));
                    File meta = new File(mLayerPath, NGFP_FILE_META);
                    // prevent overwrite meta.json by layer save routine
                    //noinspection ResultOfMethodCallIgnored
                    meta.renameTo(meta = new File(meta.getParentFile(), formPrefix + LayerFillService.NGFP_META));
                    String jsonText = FileUtil.readFromFile(meta);
                    JSONObject metaJson = new JSONObject(jsonText);
                    File dataFile = new File(mLayerPath, NGFP_FILE_DATA);
                    Bundle extra = new Bundle();
                    extra.putSerializable(KEY_LAYER_PATH, mLayerPath);
                    extra.putString(KEY_NAME, mLayerName);

                    long resourceId = mRemoteId;
                    String accountName = mAccount;
                    //read if this local or remote source
                    boolean isNgwConnection = !metaJson.isNull(ConstantsUI.JSON_NGW_CONNECTION_KEY);
                    if (isNgwConnection) {
                        JSONObject connection = metaJson.getJSONObject(ConstantsUI.JSON_NGW_CONNECTION_KEY);
                        //read url
                        url = connection.getString("url");
                        url = NGWUtil.getServerUrl(url);
                        //read login
                        String login = connection.getString("login");
                        //read password
                        String password = connection.getString("password");
                        //read id
                        resourceId = connection.getLong("id");

                        metaJson.remove(ConstantsUI.JSON_NGW_CONNECTION_KEY);
                        FileUtil.writeToFile(meta, metaJson.toString());

                        //check account exist and try to create
                        URI uri = new URI(url);
                        if (uri.getHost() != null && uri.getHost().length() > 0) {
                            accountName += uri.getHost();
                        }
                        if (uri.getPort() != 80 && uri.getPort() > 0) {
                            accountName += ":" + uri.getPort();
                        }
                        if (uri.getPath() != null && uri.getPath().length() > 0) {
                            accountName += uri.getPath();
                        }

                        IGISApplication app = (IGISApplication) getApplicationContext();
                        Account account = app.getAccount(accountName);
                        if (null == account) {
                            //create account
                            if (!app.addAccount(accountName, url, login, password, "ngw")) {
                                throw new AccountsException(getString(R.string.ngw_account_already_exists));
                            }
                        } else {
                            //compare login/password and report differences
                            String savedPassword = app.getAccountPassword(account);
                            String savedLogin = app.getAccountLogin(account);
                            boolean same = false;
                            if (savedPassword != null && savedLogin != null)
                                same = savedPassword.equals(password) && savedLogin.equals(login);
                            else {
                                if (savedLogin == null)
                                    same = login == null;
                                if (savedPassword == null)
                                    same = password == null;
                            }

                            if (!same) {
                                Intent msg = new Intent(ConstantsUI.MESSAGE_INTENT);
                                msg.putExtra(ConstantsUI.KEY_MESSAGE, getString(R.string.ngw_different_credentials));
                                msg.setPackage(getPackageName());
                                sendBroadcast(msg);
                                //Log.e("FFRRMM", "unzipform send broadcast " + msg.toString());

                            }
                        }
                    }

                    isNgwConnection = isNgwConnection || mRemoteId > -1;
                    if (isNgwConnection) {
                        FileUtil.deleteRecursive(dataFile);
                        File form = new File(mLayerPath, formPrefix + FILE_FORM);
                        ArrayList<String> lookupTableIds = LayerUtil.fillLookupTableIds(form);

                        extra.putStringArrayList(KEY_LOOKUP_ID, lookupTableIds);
                        extra.putLong(KEY_REMOTE_ID, resourceId);
                        extra.putLong(KEY_COLLECTOR_TRACKING_REMOTE_ID, mRemoteId);
                        extra.putString(KEY_ACCOUNT, accountName);
                        extra.putBoolean(KEY_SYNC, mSync);
                        extra.putLongArray(KEY_DEFAULT_FORM_IDS, defaultFormIDArray);
                        if (!TextUtils.isEmpty(mLayerConfigJson)) {
                            extra.putString(KEY_LAYER_CONFIG_JSON, mLayerConfigJson);
                        }
                        if (mCollectorOrderIndex >= 0 && mCollectorProjectRemoteIds != null) {
                            extra.putInt(KEY_COLLECTOR_ORDER_INDEX, mCollectorOrderIndex);
                            extra.putLongArray(KEY_COLLECTOR_PROJECT_REMOTE_IDS, mCollectorProjectRemoteIds);
                        }
                        if (!isCanceled() && startLayerFill) {
                            enqueueToQueue(new NGWVectorLayerFillTask(extra));
                        }
                        if (!startLayerFill) {
//                            if (getLayer() instanceof  NGWVectorLayer)
//                                ((NGWVectorLayer)getLayer()).saveWithNewFormId(defaultFormID);
                            //addFormToLayer(mLayerPath, mRemoteId, defaultFormID);
                            extra.putString(LayerFillService.KEY_MESSAGE, "form proccesed");
                            subTaskWasRunned = false;
                        }
                    } else {
                        extra.putSerializable(LayerFillService.KEY_PATH, dataFile);
                        extra.putBoolean(LayerFillService.KEY_DELETE_SRC_FILE, true);
                        extra.putLongArray(KEY_DEFAULT_FORM_IDS, defaultFormIDArray);

                        if (!isCanceled() && startLayerFill) {
                            enqueueToQueue(new VectorLayerFormFillTask(extra));
                        }
                        if (!startLayerFill) {
//                            if (getLayer() instanceof  NGWVectorLayer)
//                                ((NGWVectorLayer)getLayer()).saveWithNewFormId(defaultFormID);
                            //addFormToLayer(mLayerPath, mRemoteId, defaultFormID);
                            extra.putString(LayerFillService.KEY_MESSAGE, "form proccesed");
                            subTaskWasRunned = false;
                        }
                    }
                }
            } catch (AccountsException | JSONException | IOException | URISyntaxException | RuntimeException e) {
                e.printStackTrace();
                setError(e.getLocalizedMessage(), progressor);
                notifyError(mProgressMessage);
                return false;
            }

            return true;
        }
    }

    private class VectorLayerFormFillTask extends LayerFillTask {
        File mPath;
        boolean mDeletePath;

        VectorLayerFormFillTask(Bundle bundle) {
            super(bundle);
            mPath = (File) bundle.getSerializable(KEY_PATH);
            mDeletePath = bundle.getBoolean(KEY_DELETE_SRC_FILE, false);
            mLayer = new VectorLayerUI(mLayerGroup.getContext(), mLayerPath);
            initLayer();
        }

        @Override
        public boolean execute(IProgressor progressor) {
            try {
                VectorLayer vectorLayer = (VectorLayer) mLayer;
                if (null == vectorLayer)
                    return false;
                String formPrefix = vectorLayer.getId() + "_";
                File meta = new File(mPath.getParentFile(),formPrefix +  NGFP_META);

                if (meta.exists()) {
                    String jsonText = FileUtil.readFromFile(meta);
                    JSONObject metaJson = new JSONObject(jsonText);
                    //read fields
                    List<Field> fields = NGWUtil.getFieldsFromJson(metaJson.getJSONArray(NGWUtil.NGWKEY_FIELDS));
                    //read geometry type
                    String geomTypeString = metaJson.getString("geometry_type");
                    int geomType = GeoGeometryFactory.typeFromString(geomTypeString);
                    vectorLayer.create(geomType, fields);

                    if (GeoJSONUtil.isGeoJsonHasFeatures(mPath)) {
                        //read SRS -- not need as we will be fill layer with 3857
                        JSONObject srs = metaJson.getJSONObject(NGWUtil.NGWKEY_SRS);
                        int nSRS = srs.getInt(NGWUtil.NGWKEY_ID);
                        vectorLayer.fillFromGeoJson(mPath, nSRS, progressor);
                    }
                } else
                    vectorLayer.createFromGeoJson(mPath, progressor); // should never get there
            } catch (IOException | JSONException | SQLiteException | NGException | ClassCastException e) {
                e.printStackTrace();
                setError(e.getLocalizedMessage(), progressor);
                notifyError(mProgressMessage);
                return false;
            }

            if (mDeletePath)
                FileUtil.deleteRecursive(mPath);

            return true;
        }
    }

    private class LocalTMSFillTask extends LayerFillTask{
        boolean mIsNgrc;

        LocalTMSFillTask(Bundle bundle) {
            super(bundle);
            mLayer = new LocalTMSLayerUI(mLayerGroup.getContext(), mLayerPath);
            mIsNgrc = !bundle.containsKey(KEY_TMS_TYPE);
            ((LocalTMSLayerUI) mLayer).setCacheSizeMultiply(bundle.getInt(KEY_TMS_CACHE));

            if (!mIsNgrc) { // it's zip
                ((LocalTMSLayerUI) mLayer).setTMSType(bundle.getInt(KEY_TMS_TYPE));
                initLayer();
            } else
                mLayerName = mUri.getLastPathSegment();
        }

        @Override
        public boolean execute(IProgressor progressor) {
            try {
                TMSLayer tmsLayer = (TMSLayer) mLayer;
                if (null == tmsLayer)
                    return false;

                if (mIsNgrc)
                    tmsLayer.fillFromNgrc(mUri, progressor);
                else
                    tmsLayer.fillFromZip(mUri, progressor);
            } catch (IOException | NGException | RuntimeException e) {
                e.printStackTrace();
                setError(e.getLocalizedMessage(), progressor);
                notifyError(mProgressMessage);
                return false;
            }

            return true;
        }

        @Override
        public String getDescription() {
            return mIsNgrc ? mLayerName : super.getDescription();
        }
    }

    private class NGWVectorLayerFillTask extends LayerFillTask{
        private static final int NGW_FILL_MAX_ATTEMPTS = 3;

        private ArrayList<String> mLookupIds = new ArrayList<>();
        private boolean mShowSyncDialog;
        private final long mRemoteIdInit;
        /** Collector batch correlation id (original NGW resource id from import UI). */
        private final long mTrackingRemoteId;
        private final String mAccountNameInit;
        private boolean mMobileLayerConfigApplied;

        boolean wasMobileLayerConfigApplied() {
            return mMobileLayerConfigApplied;
        }

        long getTrackingRemoteId() {
            return mTrackingRemoteId;
        }

        NGWVectorLayerFillTask(Bundle bundle) {
            super(bundle);
            isPointz = false;
            mRemoteIdInit = bundle.getLong(KEY_REMOTE_ID);
            mTrackingRemoteId = bundle.containsKey(KEY_COLLECTOR_TRACKING_REMOTE_ID)
                    ? bundle.getLong(KEY_COLLECTOR_TRACKING_REMOTE_ID)
                    : mRemoteIdInit;
            mAccountNameInit = bundle.getString(KEY_ACCOUNT);
            mLayer = new NGWVectorLayerUI(mLayerGroup.getContext(), mLayerPath);
            ((NGWVectorLayerUI) mLayer).setRemoteId(mRemoteIdInit);
            ((NGWVectorLayerUI) mLayer).setAccountName(mAccountNameInit);
            initLayer();

            if (bundle.containsKey(KEY_LOOKUP_ID))
                mLookupIds = bundle.getStringArrayList(KEY_LOOKUP_ID);

            mShowSyncDialog = bundle.getBoolean(KEY_SYNC, true);

            for (int i = 0; i < mLayerGroup.getLayerCount(); i++) {
                if (mLayerGroup.getLayer(i) instanceof NGWLookupTable) {
                    NGWLookupTable table = (NGWLookupTable) mLayerGroup.getLayer(i);
                    String id = table.getRemoteId() + "";
                    if (table.getAccountName().equals(bundle.getString(KEY_ACCOUNT)) && mLookupIds.contains(id))
                        mLookupIds.remove(id);
                }
            }
        }

        private void rebuildNgwLayerAfterTransientFailure() {
            if (mLayer != null) {
                mLayer.delete(true);
            }
            mLayerPath = mLayerGroup.createLayerStorage();
            mLayer = new NGWVectorLayerUI(mLayerGroup.getContext(), mLayerPath);
            ((NGWVectorLayerUI) mLayer).setRemoteId(mRemoteIdInit);
            ((NGWVectorLayerUI) mLayer).setAccountName(mAccountNameInit);
            initLayer();
        }

        private boolean handleNgwExecuteError(Exception e, IProgressor progressor) {
            String error = e.getLocalizedMessage();
            if (e instanceof JSONException && e.getMessage().equals("No value for fields")){
                error = getResources().getString(com.nextgis.maplib.R.string.error_forbidden);
            }
            if ("POINTZ".equals(e.getMessage())){
                error = getBaseContext().getString(R.string.pointz_alert);
                isPointz = true;
            }

            setError(error, progressor);
            if ("POINTZ".equals(e.getMessage())){
            } else notifyError(mProgressMessage);
            return false;
        }

        @Override
        public boolean execute(IProgressor progressor) {
            boolean lookupsFilled = false;
            for (int attempt = 1; attempt <= NGW_FILL_MAX_ATTEMPTS; attempt++) {
                try {
                    NGWVectorLayer ngwVectorLayer = (NGWVectorLayer) mLayer;
                    if (null == ngwVectorLayer)
                        return false;

                    if (!lookupsFilled) {
                        for (String id : mLookupIds) {
                            NGWLookupTable table = new NGWLookupTable(mLayer.getContext(), mLayerGroup.createLayerStorage());
                            table.setAccountName(((NGWVectorLayer) mLayer).getAccountName());
                            table.setRemoteId(Long.parseLong(id));
                            table.setSyncType(Constants.SYNC_ALL);
                            table.setName(getText(R.string.layer_lookuptable) + " #" + id);
                            table.fillFromNGW(null);
                            mLayerGroup.addLayer(table);
                        }
                        lookupsFilled = true;
                    }

                    ngwVectorLayer.setCollectorDistrictOverride(mLayerGroup.getCollectorDistrict());
                    HyperLog.d(Constants.TAG, NGWVectorLayer.LOG_DISTRICT_FILTER + " fill group=\""
                            + mLayerGroup.getName() + "\" collector_district="
                            + (TextUtils.isEmpty(mLayerGroup.getCollectorDistrict())
                            ? "<empty>" : mLayerGroup.getCollectorDistrict())
                            + " layer remoteId=" + ngwVectorLayer.getRemoteId());

                    ngwVectorLayer.createFromNGW(progressor);
                    String rawConfig = resolveImportedLayerConfigJson(ngwVectorLayer, mLayerConfigJson);
                    if (!TextUtils.isEmpty(rawConfig)) {
                        try {
                            JSONObject cfg = parseLayerConfigObject(rawConfig);
                            ngwVectorLayer.fromJSON(cfg);
                            ngwVectorLayer.save();
                            mMobileLayerConfigApplied = true;
                            String hash = LayerConfigUtil.md5(rawConfig.trim());
                            ngwVectorLayer.getPreferences().edit()
                                    .putString(SettingsConstants.KEY_PREF_LAST_CONFIG_HASH, hash)
                                    .apply();
                            Log.i(Constants.TAG, LOG_LAYER_CONFIG + " applied+saved OK name="
                                    + ngwVectorLayer.getName());
                            HyperLog.d(Constants.TAG, LOG_LAYER_CONFIG + " applied OK " + ngwVectorLayer.getName());
                        } catch (Exception e) {
                            Log.e(Constants.TAG, LOG_LAYER_CONFIG + " fromJSON/save failed: " + e.getMessage(), e);
                            HyperLog.exception(Constants.TAG, e);
                        }
                    } else {
                        Log.w(Constants.TAG, LOG_LAYER_CONFIG + " skipped (no text): name="
                                + ngwVectorLayer.getName() + " remoteId=" + ngwVectorLayer.getRemoteId());
                        HyperLog.w(Constants.TAG, LOG_LAYER_CONFIG + " skipped no config text for "
                                + ngwVectorLayer.getName());
                    }
                    applyCollectorEditableFromIntent(ngwVectorLayer);
                    return true;
                } catch (IOException e) {
                    if (!NetworkUtil.isTransientNetworkFailure(e) || attempt >= NGW_FILL_MAX_ATTEMPTS) {
                        return handleNgwExecuteError(e, progressor);
                    }
                    HyperLog.d(Constants.TAG, "NGW fill transient error, attempt " + attempt + "/" + NGW_FILL_MAX_ATTEMPTS + ": " + e.getMessage());
                    if (progressor != null) {
                        progressor.setMessage(getString(R.string.layer_fill_network_retry, attempt + 1, NGW_FILL_MAX_ATTEMPTS));
                    }
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        setError(ie.getLocalizedMessage(), progressor);
                        notifyError(mProgressMessage);
                        return false;
                    }
                    rebuildNgwLayerAfterTransientFailure();
                } catch (JSONException | SQLiteException | NGException | ClassCastException e) {
                    return handleNgwExecuteError(e, progressor);
                }
            }
            return false;
        }

        boolean showSyncDialog() {
            return mShowSyncDialog;
        }

        private void applyCollectorEditableFromIntent(NGWVectorLayer ngwVectorLayer) {
            if (mEnqueueBundle.containsKey(KEY_COLLECTOR_LAYER_EDITABLE)) {
                ngwVectorLayer.setCollectorEditable(
                        mEnqueueBundle.getBoolean(KEY_COLLECTOR_LAYER_EDITABLE, true));
                try {
                    ngwVectorLayer.save();
                } catch (Exception e) {
                    Log.w(Constants.TAG, "applyCollectorEditableFromIntent save failed: " + e.getMessage());
                }
            }
        }

        String getAccountName() {
            return ((NGWVectorLayerUI) mLayer).getAccountName();
        }
    }
}
