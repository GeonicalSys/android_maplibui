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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
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
    private static boolean mIsShowing;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mActivity = new WeakReference<>(getActivity());

        if (mActivity.get() == null)
            return;
        IntentFilter intentFilter = new IntentFilter(LayerFillService.ACTION_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mActivity.get().registerReceiver(mLayerFillReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            mActivity.get().registerReceiver(mLayerFillReceiver, intentFilter);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        if (mLayerFillReceiver != null && mActivity.get()!= null)
            mActivity.get().unregisterReceiver(mLayerFillReceiver);

        mProgressDialog = null;
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
        mProgressDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                host.getString(R.string.menu_visibility_off), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mIsShowing = false;
                    }
                });
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
            if (mHostRef != null) {
                Activity a = mHostRef.get();
                if (a != null) {
                    return a;
                }
            }
            return mActivity.get();
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
                ContextCompat.startForegroundService(host, (Intent) params[0]);
            }
            mHostRef = new WeakReference<>(host);

            mLayerFillReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    publishProgress(intent);
                }
            };

            IntentFilter intentFilter = new IntentFilter(LayerFillService.ACTION_UPDATE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                host.registerReceiver(mLayerFillReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                host.registerReceiver(mLayerFillReceiver, intentFilter);
            }

            while (!mIsFinished) {
                SystemClock.sleep(500);
            }

            return true;
        }

        @Override
        protected void onProgressUpdate(Intent... values) {
            super.onProgressUpdate(values);

            final Activity host = getHost();
            if (host == null) {
                return;
            }

            final Intent intent = values[0];
            short serviceStatus = intent.getShortExtra(LayerFillService.KEY_STATUS, (short) 0);
            String title = intent.getStringExtra(LayerFillService.KEY_TITLE);

            if (mProgressDialog == null) {
                createProgressDialog(host);
                setDialogInfo(title, title);

                if (mIsShowing)
                    mProgressDialog.show();
            }

            switch (serviceStatus) {
                case LayerFillService.STATUS_START:
                    mProgressDialog.setIndeterminate(true);
                    mProgressDialog.show();
                    mIsShowing = true;
                    break;
                case LayerFillService.STATUS_UPDATE:
                    final String message = intent.getStringExtra(LayerFillService.KEY_MESSAGE);
                    if (TextUtils.isEmpty(message))
                        return;

                    mProgressDialog.setIndeterminate(false);
                    setDialogInfo(title, message);
                    mProgressDialog.setMax(intent.getIntExtra(LayerFillService.KEY_TOTAL, 0));
                    mProgressDialog.setProgress(intent.getIntExtra(LayerFillService.KEY_PROGRESS, 0));
                    break;
                case LayerFillService.STATUS_STOP:
                    if (intent.getIntExtra(LayerFillService.KEY_TOTAL, 0) == 0) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                        if (mLayerFillReceiver != null)
                            host.unregisterReceiver(mLayerFillReceiver);
                        mLayerFillReceiver = null;
                        mIsFinished = true;
                    }

                    boolean canceled = intent.getBooleanExtra(LayerFillService.KEY_CANCELLED, false);
                    String toast = host.getString(com.nextgis.maplibui.R.string.message_layer_created);
                    boolean success = intent.getBooleanExtra(LayerFillService.KEY_RESULT, false);
                    if (!success) {
                        if (canceled)
                            toast = host.getString(com.nextgis.maplibui.R.string.canceled);
                        else
                            toast = intent.getStringExtra(LayerFillService.KEY_MESSAGE);
                    }

                    if (intent.hasExtra(LayerFillService.KEY_MESSAGE)) {
                        if (!intent.getBooleanExtra(IS_POINTS, false))
                            Toast.makeText(host, toast, Toast.LENGTH_LONG).show();
                        else {

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
                        if (!(rawLayer instanceof NGWVectorLayer))
                            break;
                        final NGWVectorLayer ngwLayer = (NGWVectorLayer) rawLayer;
                        final String accountName = ngwLayer.getAccountName();
                        if (TextUtils.isEmpty(accountName))
                            break;
                        final Account account = app.getAccount(accountName);
                        if (account == null)
                            break;

                        boolean mobileConfigApplied = intent.getBooleanExtra(
                                LayerFillService.KEY_MOBILE_LAYER_CONFIG_APPLIED, false);
                        if (mobileConfigApplied) {
                            // sync_type / direction already set by NGWVectorLayer.fromJSON (collector mobile config)
                            boolean layerSyncOff = (ngwLayer.getSyncType() & Constants.SYNC_NONE) != 0;
                            if (!layerSyncOff) {
                                NGWSettingsFragment.setAccountSyncEnabled(account, app.getAuthority(), true);
                            }
                        } else {
                            NGWSettingsFragment.setAccountSyncEnabled(account, app.getAuthority(), true);
                            ngwLayer.setSyncType(Constants.SYNC_ALL);
                            ngwLayer.save();
                        }

                        if (host instanceof NGActivity)
                            ((NGActivity) host).refreshLayersFrarment();



//                        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
//                        builder.setTitle(R.string.sync_dialog_title).setMessage(R.string.sync_dialog_message)
//                                .setPositiveButton(R.string.auto, new DialogInterface.OnClickListener() {
//                                    @Override
//                                    public void onClick(DialogInterface dialogInterface, int i) {
//                                        NGWSettingsFragment.setAccountSyncEnabled(account, app.getAuthority(), true);
//                                        ngwLayer.setSyncType(Constants.SYNC_ALL);
//                                        ngwLayer.save();
//                                    }
//                                })
//                                .setNeutralButton(R.string.skip, null)
//                                .setNegativeButton(R.string.manual, new DialogInterface.OnClickListener() {
//                                    @Override
//                                    public void onClick(DialogInterface dialogInterface, int i) {
//                                        ngwLayer.setSyncType(Constants.SYNC_ALL);
//                                        ngwLayer.save();
//                                    }
//                                });
//
//                        AlertDialog dialog = builder.show();
//                        dialog.setCanceledOnTouchOutside(false);
                    }
                    break;
                case LayerFillService.STATUS_SHOW:
                    if (!mProgressDialog.isShowing()) {
                        createProgressDialog(host);
                        setDialogInfo(title, title);
                        mProgressDialog.show();
                        mIsShowing = true;
                    }
                    break;
            }
        }

        private void setDialogInfo(final String title, final String message) {

            final ProgressDialog progressDialogFinal = mProgressDialog;
            final Activity host = getHost();
            if (host != null)
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
