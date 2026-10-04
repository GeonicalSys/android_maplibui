/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2015-2016, 2018-2019, 2021 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.maplibui.service;

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import com.nextgis.maplib.api.IProgressor;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.activity.VectorLayerSettingsActivity;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.NotificationHelper;

import java.util.LinkedList;
import java.util.List;

import static com.nextgis.maplib.util.Constants.TAG;
import static com.nextgis.maplibui.util.NotificationHelper.createBuilder;

public class RebuildCacheService extends IntentService implements IProgressor

{
    public static final String ACTION_ADD_TASK = "REBUILD_CACHE_ADD_TASK";
    public static final String ACTION_REMOVE_TASK = "REBUILD_CACHE_REMOVE_TASK";
    public static final String ACTION_STOP = "REBUILD_CACHE_STOP";
    public static final String ACTION_SHOW = "REBUILD_CACHE_SHOW";
    public static final String ACTION_UPDATE = "REBUILD_CACHE_UPDATE_PROGRESS";
    public static final String KEY_PROGRESS = "progress";
    public static final String KEY_MAX = "max";

    protected NotificationManager mNotifyManager;
    protected final java.util.Queue<Integer> mQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    protected int mTotalTasks;
    protected static final int NOTIFICATION_ID = 99;
    protected NotificationCompat.Builder mBuilder;
    protected Intent mProgressIntent;

    protected VectorLayer mLayer;
    protected int mProgressMax, mCurrentTasks;
    protected long mLastUpdate = 0;
    protected volatile boolean mIsRunning, mIsCanceled, mRemoveCurrent;
    private boolean mStopped;
    private final android.os.Handler mWorkHandler = new android.os.Handler(Looper.getMainLooper());

    MyCustomThread thread = null;


    public static class MyCustomThread extends Thread {

        private Looper mLooper;

        public MyCustomThread(Runnable runnable) {
            super(runnable);
        }

        public void setLooper(Looper looper) {
            mLooper = looper;
        }

        @Override
        public void run() {
            mLooper = Looper.getMainLooper();
            super.run();
        }
    }

    public RebuildCacheService() {
        super("RebuildCacheService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.d(TAG, "onHandleIntent");
        startNextTask();

        }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mProgressIntent = new Intent(ACTION_UPDATE);
        mBuilder = createBuilder(this, com.nextgis.maplib.R.string.rebuild_cache);
        mBuilder.setAutoCancel(false).setOngoing(true);

        int icon = R.drawable.ic_notification_rebuild_cache;
        Bitmap largeIcon = NotificationHelper.getLargeIcon(icon, getResources());
        mBuilder.setSmallIcon(icon).setLargeIcon(largeIcon);

        if (isMobileApplication()) {
            Intent intent = new Intent(this, RebuildCacheService.class);
            intent.setAction(ACTION_STOP);
            int flag = PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE;
            PendingIntent stop = PendingIntent.getService(this, 0, intent, flag);
            intent.setAction(ACTION_SHOW);
            PendingIntent show = PendingIntent.getService(this, 0, intent, flag);
            icon = R.drawable.ic_action_cancel_dark;
            mBuilder.setContentIntent(show).addAction(icon, getString(android.R.string.cancel), stop);
        } else {
            mBuilder.setContentTitle(getString(com.nextgis.maplib.R.string.updating_data)).setContentText(getString(com.nextgis.maplib.R.string.updating_data));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String title = getString(com.nextgis.maplib.R.string.rebuild_cache);
            mBuilder.setWhen(System.currentTimeMillis()).setContentTitle(title).setTicker(title);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    startForeground(NOTIFICATION_ID, mBuilder.build(),
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                else startForeground(NOTIFICATION_ID, mBuilder.build());
            } catch (RuntimeException error) {
                Log.w(TAG, "Cache rebuild foreground start rejected", error);
                stopService();
            }
        }


    }




    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mStopped) return START_NOT_STICKY;
        if (intent != null) {
            String action = intent.getAction();
            if (action != null && !TextUtils.isEmpty(action)) {
                String key = ConstantsUI.KEY_LAYER_ID;
                switch (action) {
                    case ACTION_ADD_TASK:
                        int layerIdAdd = intent.getIntExtra(key, Constants.NOT_FOUND);
                        mQueue.add(layerIdAdd);
                        mTotalTasks += 1;

                        if (!mIsRunning)
                            startNextTask();

                        return START_STICKY;
                    case ACTION_REMOVE_TASK:
                        int layerIdRemove = intent.getIntExtra(key, Constants.NOT_FOUND);
                        mCurrentTasks--;

                        if (mLayer != null && mLayer.getId() == layerIdRemove)
                            mRemoveCurrent = true;
                        else mQueue.remove(layerIdRemove);
                        return START_STICKY;
                    case ACTION_STOP:
                        stopService();
                        break;
                    case ACTION_SHOW:
                        Intent settings = new Intent(this, VectorLayerSettingsActivity.class);
                        settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        settings.putExtra(key, mLayer != null ? mLayer.getId() : Constants.NOT_FOUND);
                        startActivity(settings);
                        break;
                }
            }
        }
        return START_NOT_STICKY;
    }

    protected void stopService() {
        mStopped = true;
        mIsCanceled = true;
        mIsRunning = false;
        mQueue.clear();
        mCurrentTasks = 0;
        mProgressIntent.putExtra(KEY_PROGRESS, 0).setPackage(getPackageName());
        sendBroadcast(mProgressIntent);
        MyCustomThread worker = thread;
        thread = null;
        if (worker != null && worker != Thread.currentThread()) worker.interrupt();
        // Never join a worker on Android's main thread, or join the current worker itself.
        try { stopForeground(true); }
        finally { stopSelf(); }
    }

    @Override public void onTimeout(int startId, int foregroundServiceType) {
        Log.w(TAG, "Cache rebuild reached foreground time limit");
        stopService();
    }

    @Override public void onDestroy() {
        stopService();
        mWorkHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    protected void startNextTask() {
        if (mStopped || mIsRunning) return;
        Integer layerId = mQueue.poll();
        if (layerId == null) { stopService(); return; }
        mIsCanceled = mRemoveCurrent = false;
        mIsRunning = true; // reserve the worker before another start intent can enqueue work
        try {
            MapBase owner = ((IGISApplication) getApplication()).getMap();
            mLayer = (VectorLayer) owner.getLayerById(layerId);
        } catch (RuntimeException error) {
            mLayer = null;
            Log.w(TAG, "Cache rebuild layer is unavailable", error);
        }
        final VectorLayer layer = mLayer;
        mCurrentTasks++;
        String title = getString(com.nextgis.maplib.R.string.rebuild_cache);
        if (isMobileApplication()) title += ": " + mCurrentTasks + "/" + mTotalTasks;
        mBuilder.setWhen(System.currentTimeMillis()).setContentTitle(title).setTicker(title);
        mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
        thread = new MyCustomThread(() -> {
            try {
                Process.setThreadPriority(Constants.DEFAULT_DOWNLOAD_THREAD_PRIORITY);
                if (layer != null) layer.rebuildCache(this);
            } catch (RuntimeException error) { Log.w(TAG, "Cache rebuild failed", error); }
            finally {
                mWorkHandler.post(() -> {
                    mIsRunning = false;
                    if (!mStopped) startNextTask();
                });
            }
        });
        thread.start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected boolean isMobileApplication() {
        return getApplication() instanceof IGISApplication
                && !((IGISApplication) getApplication()).isCollectorApplication();
    }



    @Override
    public void setMax(int maxValue) {
        mProgressMax = maxValue;
    }

    @Override
    public boolean isCanceled() {
        return mIsCanceled || mRemoveCurrent;
    }

    @Override
    public void setValue(int value) {
        if (mIsCanceled) return;
        if (mLastUpdate + ConstantsUI.NOTIFICATION_DELAY < System.currentTimeMillis()) {
            mLastUpdate = System.currentTimeMillis();
            mBuilder.setProgress(mProgressMax, value, false);
            mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
        }

        int id = mLayer != null ? mLayer.getId() : Constants.NOT_FOUND;
        try {
            mProgressIntent.putExtra(KEY_PROGRESS, value)
                    .putExtra(KEY_MAX, mProgressMax)
                    .putExtra(ConstantsUI.KEY_LAYER_ID, id);
        } catch (Exception ex){
            Log.e(TAG, "error on setIntentValue:" + ex.getMessage());

        }
        mProgressIntent.setPackage(getPackageName());
        sendBroadcast(mProgressIntent);
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {

    }

    @Override
    public void setMessage(String message) {

    }
}
