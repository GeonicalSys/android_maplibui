package com.nextgis.maplibui.util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;

import com.nextgis.maplibui.R;

/** Offers a safe way to leave a running sync before a user-requested project action. */
public final class ProjectSyncInterruption {
    private static final long POLL_INTERVAL_MS = 250L;

    private ProjectSyncInterruption() {
    }

    /** Returns true when the action has been deferred to a confirmation dialog. */
    public static boolean confirmAndRun(Activity activity, Runnable action) {
        if (activity == null || action == null || !ProjectOperationCoordinator.isDataSyncActive()) {
            return false;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.sync_interrupt_title)
                .setMessage(R.string.sync_interrupt_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.sync_interrupt_confirm, (dialog, which) -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    if (!ProjectOperationCoordinator.isDataSyncActive()) {
                        action.run();
                        return;
                    }
                    ProgressDialog waiting = new ProgressDialog(activity);
                    waiting.setMessage(activity.getString(R.string.sync_interrupt_wait));
                    waiting.setIndeterminate(true);
                    waiting.setCancelable(false);
                    waiting.show();
                    Handler handler = new Handler(Looper.getMainLooper());
                    Runnable poll = new Runnable() {
                        @Override
                        public void run() {
                            if (activity.isFinishing() || activity.isDestroyed()) {
                                waiting.dismiss();
                                return;
                            }
                            if (ProjectOperationCoordinator.isBusy()) {
                                handler.postDelayed(this, POLL_INTERVAL_MS);
                                return;
                            }
                            waiting.dismiss();
                            action.run();
                        }
                    };
                    ProjectOperationCoordinator.requestDataSyncCancellation(activity);
                    handler.post(poll);
                })
                .show();
        return true;
    }
}
