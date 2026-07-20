/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * Author:   NikitaFeodonit, nfeodonit@yandex.com
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2012-2017, 2019 NextGIS, info@nextgis.com
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

package com.nextgis.maplibui.fragment;

import static com.nextgis.maplibui.service.LayerFillService.IS_POINTS;

import android.accounts.Account;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.widget.Toast;

import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.activity.NGActivity;
import com.nextgis.maplibui.service.LayerFillService;

import java.lang.ref.WeakReference;

// http://www.androiddesignpatterns.com/2013/04/retaining-objects-across-config-changes.html
public class LayerFillProgressDialogFragment extends Fragment {
    private static WeakReference<Activity> mActivity;
    private static BroadcastReceiver mLayerFillReceiver;
    private static ProgressDialog mProgressDialog;
    /** Same context used with {@link #mLayerFillReceiver} (application); survives activity destroy. */
    private static Context sFillReceiverAppContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mActivity = new WeakReference<>(getActivity());
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mProgressDialog = null;
    }

    /**
     * When the map activity resumes while {@link IGISApplication#isLayerFillServiceBusy()} is true,
     * re-bind the host for the progress dialog and ask the service for a UI snapshot.
     */
    public static void onMainMapActivityResume(Activity activity) {
        if (activity == null) {
            return;
        }
        mActivity = new WeakReference<>(activity);
        if (!((IGISApplication) activity.getApplicationContext()).isLayerFillServiceBusy()) {
            return;
        }
        synchronized (LayerFillProgressDialogFragment.class) {
            if (mLayerFillReceiver == null) {
                startBatchFillProgress(activity);
                return;
            }
        }
        activity.startService(new Intent(activity, LayerFillService.class).setAction(LayerFillService.ACTION_SHOW));
    }

    private static void unregisterFillProgressReceiver() {
        if (sFillReceiverAppContext != null && mLayerFillReceiver != null) {
            try {
                sFillReceiverAppContext.unregisterReceiver(mLayerFillReceiver);
            } catch (IllegalArgumentException ignored) {
            }
        }
        mLayerFillReceiver = null;
        sFillReceiverAppContext = null;
    }

    public static void startFill(Intent intent) {
        new LayerFillProgressDialog(false).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, intent);
    }


    /**
     * Map / host activity that retains {@link LayerFillProgressDialogFragment} — same as used by
     * {@link #startFill(Intent)}. Use for collector batch fill so the progress dialog is not tied to
     * {@link android.app.Activity} that calls {@code finish()} immediately (e.g. resource picker).
     */
    public static Activity getProgressHostActivity() {
        return mActivity != null ? mActivity.get() : null;
    }


    /**
     * After {@link ContextCompat#startForegroundService(Context, Intent)} for the first queued task
     * and {@link Context#startService(Intent)} for the rest, show progress for the whole batch
     * (e.g. collector project). Does not start the service again.
     */
    public static void startBatchFillProgress(Activity activity) {
        if (activity == null) {
            return;
        }
        synchronized (LayerFillProgressDialogFragment.class) {
            if (mLayerFillReceiver != null) {
                mActivity = new WeakReference<>(activity);
                activity.startService(
                        new Intent(activity, LayerFillService.class).setAction(LayerFillService.ACTION_SHOW));
                return;
            }
        }
        new LayerFillProgressDialog(true).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, activity);
    }

    private static void createProgressDialog(Activity host) {
        if (host == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            mProgressDialog = new ProgressDialog(host, android.R.style.Theme_Material_Light_Dialog_Alert);
        else
            mProgressDialog = new ProgressDialog(host);

        mProgressDialog.setProgressNumberFormat(null);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCanceledOnTouchOutside(false);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                host.getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intentStop = new Intent(host, LayerFillService.class);
                        intentStop.setAction(LayerFillService.ACTION_STOP);
                        host.startService(intentStop);
                    }
                });
    }

    private static class LayerFillProgressDialog extends AsyncTask<Object, Intent, Boolean> {
        private final boolean mSkipForegroundStart;
        private boolean mIsFinished;
        private WeakReference<Activity> mHostRef;

        LayerFillProgressDialog(boolean skipForegroundStart) {
            mSkipForegroundStart = skipForegroundStart;
        }

        private Activity getHost() {
            if (mActivity != null) {
                Activity cur = mActivity.get();
                if (cur != null) {
                    return cur;
                }
            }
            if (mHostRef != null) {
                return mHostRef.get();
            }
            return null;
        }

        private void completeFillProgressUi() {
            if (mProgressDialog != null) {
                try {
                    mProgressDialog.dismiss();
                } catch (Exception ignored) {
                }
                mProgressDialog = null;
            }
            unregisterFillProgressReceiver();
            mIsFinished = true;
        }

        @Override
        protected Boolean doInBackground(Object[] params) {
            if (params.length != 1) {
                return false;
            }
            final Activity host;
            if (mSkipForegroundStart) {
                if (!(params[0] instanceof Activity)) {
                    return false;
                }
                host = (Activity) params[0];
            } else {
                if (!(params[0] instanceof Intent)) {
                    return false;
                }
                host = mActivity.get();
                if (host == null) {
                    return false;
                }
            }

            synchronized (LayerFillProgressDialogFragment.class) {
                if (mLayerFillReceiver != null) {
                    mHostRef = new WeakReference<>(host);
                    mActivity = new WeakReference<>(host);
                    if (!mSkipForegroundStart) {
                        ContextCompat.startForegroundService(host, (Intent) params[0]);
                    }
                    return true;
                }

                mLayerFillReceiver = new BroadcastReceiver() {
                    public void onReceive(Context context, Intent intent) {
                        publishProgress(intent);
                    }
                };

                IntentFilter intentFilter = new IntentFilter(LayerFillService.ACTION_UPDATE);
                Context appCtx = host.getApplicationContext();
                sFillReceiverAppContext = appCtx;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appCtx.registerReceiver(mLayerFillReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    appCtx.registerReceiver(mLayerFillReceiver, intentFilter);
                }
            }

            if (!mSkipForegroundStart) {
                ContextCompat.startForegroundService(host, (Intent) params[0]);
            }
            mHostRef = new WeakReference<>(host);
            mActivity = new WeakReference<>(host);

            while (!mIsFinished) {
                SystemClock.sleep(500);
            }

            return true;
        }

        @Override
        protected void onProgressUpdate(Intent... values) {
            super.onProgressUpdate(values);

            final Activity host = getHost();
            if (host == null || host.isFinishing()) {
                return;
            }

            final Intent intent = values[0];
            short serviceStatus = intent.getShortExtra(LayerFillService.KEY_STATUS, (short) 0);
            String title = intent.getStringExtra(LayerFillService.KEY_TITLE);

            if (mProgressDialog == null) {
                createProgressDialog(host);
                setDialogInfo(title, title);
                mProgressDialog.show();
            }

            switch (serviceStatus) {
                case LayerFillService.STATUS_START:
                    mProgressDialog.setIndeterminate(true);
                    if (!TextUtils.isEmpty(title)) {
                        setDialogInfo(title, title);
                    }
                    mProgressDialog.show();
                    break;
                case LayerFillService.STATUS_UPDATE: {
                    final String message = intent.getStringExtra(LayerFillService.KEY_MESSAGE);
                    final int total = intent.getIntExtra(LayerFillService.KEY_TOTAL, 0);
                    final int progress = intent.getIntExtra(LayerFillService.KEY_PROGRESS, 0);

                    if (mProgressDialog == null) {
                        createProgressDialog(host);
                        final String t = TextUtils.isEmpty(title)
                                ? host.getString(com.nextgis.maplib.R.string.start_fill_layer) : title;
                        final String m = TextUtils.isEmpty(message) ? t : message;
                        setDialogInfo(t, m);
                        mProgressDialog.show();
                    }
                    if (mProgressDialog == null) {
                        break;
                    }
                    if (total > 0) {
                        mProgressDialog.setIndeterminate(false);
                        mProgressDialog.setMax(total);
                        mProgressDialog.setProgress(progress);
                    }
                    if (!TextUtils.isEmpty(message)) {
                        setDialogInfo(title, message);
                        if (total > 0) {
                            mProgressDialog.setIndeterminate(false);
                        }
                    }
                    if (!mProgressDialog.isShowing()) {
                        mProgressDialog.show();
                    }
                    break;
                }
                case LayerFillService.STATUS_STOP: {
                    if (intent.getBooleanExtra(LayerFillService.KEY_COLLECTOR_SESSION_UI_COMPLETE, false)) {
                        completeFillProgressUi();
                        break;
                    }
                    /*
                     * KEY_TOTAL = tasks still queued after this one finishes (0 = last/final stop).
                     * Must not tear down before handling KEY_RESULT / toast / NGW sync — otherwise a
                     * failed or completed single-task fill closes the dialog with no message ("silent" stop).
                     */
                    final int remainingQueue = intent.getIntExtra(LayerFillService.KEY_TOTAL, 0);
                    final boolean finalStop = (remainingQueue == 0);
                    final boolean suppressToast =
                            intent.getBooleanExtra(LayerFillService.KEY_SUPPRESS_STOP_TOAST, false);
                    final boolean keepUiBlocking =
                            intent.getBooleanExtra(LayerFillService.KEY_KEEP_PROGRESS_UI_BLOCKING, false);

                    boolean canceled = intent.getBooleanExtra(LayerFillService.KEY_CANCELLED, false);
                    String toast = host.getString(com.nextgis.maplibui.R.string.message_layer_created);
                    boolean success = intent.getBooleanExtra(LayerFillService.KEY_RESULT, false);
                    if (!success) {
                        if (canceled) {
                            toast = host.getString(com.nextgis.maplibui.R.string.canceled);
                        } else {
                            String err = intent.getStringExtra(LayerFillService.KEY_MESSAGE);
                            toast = TextUtils.isEmpty(err)
                                    ? host.getString(com.nextgis.maplib.R.string.error_connect_failed)
                                    : err;
                        }
                    }

                    if (!suppressToast && (finalStop || intent.hasExtra(LayerFillService.KEY_MESSAGE))) {
                        if (!intent.getBooleanExtra(IS_POINTS, false)) {
                            Toast.makeText(host, toast, Toast.LENGTH_LONG).show();
                        } else {
                            final androidx.appcompat.app.AlertDialog builder
                                    = new androidx.appcompat.app.AlertDialog.Builder(host).setTitle(title)
                                    .setMessage(toast)
                                    .setPositiveButton(R.string.ok, null)
                                    .create();
                            builder.setCanceledOnTouchOutside(false);
                            builder.show();
                        }
                    }

                    boolean isNgwSync = intent.getBooleanExtra(LayerFillService.KEY_SYNC, false);
                    if (success && !canceled && isNgwSync) {
                        int id = intent.getIntExtra(LayerFillService.KEY_REMOTE_ID, -1);
                        final IGISApplication app = (IGISApplication) host.getApplication();
                        final ILayer rawLayer = app.getMap().getLayerById(id);
                        if (rawLayer instanceof NGWVectorLayer) {
                            final NGWVectorLayer ngwLayer = (NGWVectorLayer) rawLayer;
                            final String accountName = ngwLayer.getAccountName();
                            if (!TextUtils.isEmpty(accountName)) {
                                final Account account = app.getAccount(accountName);
                                if (account != null) {
                                    boolean mobileConfigApplied = intent.getBooleanExtra(
                                            LayerFillService.KEY_MOBILE_LAYER_CONFIG_APPLIED, false);
                                    if (mobileConfigApplied) {
                                        boolean layerSyncOff = (ngwLayer.getSyncType() & Constants.SYNC_NONE) != 0;
                                        if (!layerSyncOff) {
                                            NGWSettingsFragment.setAccountSyncEnabled(
                                                    host, account, app.getAuthority(), true);
                                        }
                                    } else {
                                        NGWSettingsFragment.setAccountSyncEnabled(
                                                host, account, app.getAuthority(), true);
                                        ngwLayer.setSyncType(Constants.SYNC_ALL);
                                        ngwLayer.save();
                                    }
                                    if (host instanceof NGActivity) {
                                        ((NGActivity) host).refreshLayersFrarment();
                                    }
                                }
                            }
                        }
                    }

                    if (finalStop && !keepUiBlocking) {
                        completeFillProgressUi();
                    }
                    break;
                }
                case LayerFillService.STATUS_SHOW:
                    if (host == null || host.isFinishing()) {
                        break;
                    }
                    if (mProgressDialog != null && mProgressDialog.isShowing()) {
                        break;
                    }
                    if (mProgressDialog != null) {
                        try {
                            mProgressDialog.dismiss();
                        } catch (Exception ignored) {
                        }
                        mProgressDialog = null;
                    }
                    createProgressDialog(host);
                    final String showTitle = TextUtils.isEmpty(title)
                            ? host.getString(com.nextgis.maplib.R.string.start_fill_layer) : title;
                    final String snapMsg = intent.getStringExtra(LayerFillService.KEY_MESSAGE);
                    final boolean snapIndeterminate =
                            intent.getBooleanExtra(LayerFillService.KEY_INDETERMINATE, true);
                    final int snapMax = intent.getIntExtra(LayerFillService.KEY_TOTAL, 0);
                    final int snapProg = intent.getIntExtra(LayerFillService.KEY_PROGRESS, 0);

                    mProgressDialog.setTitle(showTitle);
                    if (snapIndeterminate || snapMax <= 0) {
                        mProgressDialog.setIndeterminate(true);
                        mProgressDialog.setMessage(
                                !TextUtils.isEmpty(snapMsg) ? snapMsg : showTitle);
                    } else {
                        mProgressDialog.setIndeterminate(false);
                        mProgressDialog.setMax(snapMax);
                        mProgressDialog.setProgress(snapProg);
                        mProgressDialog.setMessage(
                                !TextUtils.isEmpty(snapMsg) ? snapMsg : showTitle);
                    }
                    mProgressDialog.show();
                    break;
            }
        }

        private void setDialogInfo(final String title, final String message) {

            final ProgressDialog progressDialogFinal = mProgressDialog;
            final Activity host = getHost();
            if (host == null || progressDialogFinal == null) {
                return;
            }
            host.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressDialogFinal.setTitle(title);
                    progressDialogFinal.setMessage(message);
                }
            });
        }
    }
}
