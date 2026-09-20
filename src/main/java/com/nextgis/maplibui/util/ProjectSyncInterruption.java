package com.nextgis.maplibui.util;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.nextgis.maplib.util.NgwSyncIo;
import com.nextgis.maplibui.R;

import java.util.concurrent.atomic.AtomicBoolean;

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
                    AtomicBoolean actionCanceled = new AtomicBoolean();
                    AlertDialog waiting = new AlertDialog.Builder(activity)
                            .setTitle(R.string.sync_interrupt_title)
                            .setMessage(waitMessage(activity))
                            .setNegativeButton(R.string.cancel,
                                    (waitingDialog, waitingWhich) ->
                                            actionCanceled.set(true))
                            .create();
                    waiting.setCanceledOnTouchOutside(false);
                    waiting.setOnCancelListener(
                            waitingDialog -> actionCanceled.set(true));
                    waiting.show();
                    Handler handler = new Handler(Looper.getMainLooper());
                    Runnable poll = new Runnable() {
                        @Override
                        public void run() {
                            if (actionCanceled.get()
                                    || activity.isFinishing()
                                    || activity.isDestroyed()
                                    || !isHostStarted(activity)) {
                                actionCanceled.set(true);
                                waiting.dismiss();
                                return;
                            }
                            waiting.setMessage(waitMessage(activity));
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

    private static String waitMessage(Activity activity) {
        if (NgwSyncIo.isOutboundWriteInProgress()) {
            return activity.getString(R.string.sync_interrupt_wait_upload);
        }
        if (!ProjectOperationCoordinator.isDataSyncActive()
                && ProjectOperationCoordinator.isBusy()) {
            return activity.getString(R.string.sync_interrupt_wait_project_operation);
        }
        return activity.getString(R.string.sync_interrupt_wait);
    }

    private static boolean isHostStarted(Activity activity) {
        return !(activity instanceof LifecycleOwner)
                || ((LifecycleOwner) activity).getLifecycle()
                        .getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }
}
