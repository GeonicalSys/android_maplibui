package com.nextgis.maplibui.util;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.datasource.ngw.CollectorResource;
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.datasource.ngw.Connections;
import com.nextgis.maplib.map.CollectorProjectMetadata;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.LisaCatalog;
import com.nextgis.maplib.util.LisaCatalogLookup;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.activity.NGActivity;
import com.nextgis.maplibui.activity.NGWLoginActivity;
import com.nextgis.maplibui.dialog.SelectNGWResourceDialog;
import com.nextgis.maplibui.fragment.LayerFillProgressDialogFragment;
import com.nextgis.maplibui.service.LayerFillService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads Collector projects that live under the Web GIS resource with keyname {@code lisa}.
 */
public final class LoadLisaCollectorProject {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private LoadLisaCollectorProject() {
    }

    public static void start(NGActivity activity) {
        if (activity == null) {
            return;
        }
        AccountManager accountManager = AccountManager.get(activity);
        Connections connections = SelectNGWResourceDialog.fillConnections(activity, accountManager);
        if (connections.getChildrenCount() == 0) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.lisa_catalog_load_project)
                    .setMessage(R.string.lisa_catalog_need_account)
                    .setPositiveButton(R.string.ngw_account_add, (dialog, which) ->
                            activity.startActivity(new Intent(activity, NGWLoginActivity.class)))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        if (connections.getChildrenCount() == 1) {
            loadFromConnection(activity, (Connection) connections.getChild(0));
            return;
        }
        String[] names = new String[connections.getChildrenCount()];
        Connection[] items = new Connection[connections.getChildrenCount()];
        for (int i = 0; i < connections.getChildrenCount(); i++) {
            Connection connection = (Connection) connections.getChild(i);
            items[i] = connection;
            names[i] = connection == null ? "" : connection.getName();
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.ngw_accounts)
                .setItems(names, (dialog, which) -> {
                    if (which >= 0 && which < items.length && items[which] != null) {
                        loadFromConnection(activity, items[which]);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void loadFromConnection(NGActivity activity, Connection connection) {
        ProgressDialog progress = ProgressDialog.show(
                activity,
                activity.getString(R.string.lisa_catalog_load_project),
                activity.getString(R.string.waiting),
                true,
                false);
        EXECUTOR.execute(() -> {
            boolean connected = connection.connect(false);
            LisaCatalog.Ref catalog = connected ? LisaCatalogLookup.findCatalog(connection) : null;
            List<LisaCatalog.Ref> projects = catalog == null
                    ? LisaCatalog.emptyRefList()
                    : LisaCatalogLookup.listCollectorProjects(connection, catalog.remoteId);
            activity.runOnUiThread(() -> {
                progress.dismiss();
                if (activity.isFinishing()) {
                    return;
                }
                if (!connected) {
                    Toast.makeText(activity, R.string.lisa_catalog_connect_failed, Toast.LENGTH_LONG)
                            .show();
                    return;
                }
                if (catalog == null) {
                    Toast.makeText(activity, R.string.lisa_catalog_not_found, Toast.LENGTH_LONG)
                            .show();
                    return;
                }
                if (projects.isEmpty()) {
                    Toast.makeText(activity, R.string.lisa_catalog_no_projects, Toast.LENGTH_LONG)
                            .show();
                    return;
                }
                showProjectList(activity, connection, projects);
            });
        });
    }

    private static void showProjectList(
            NGActivity activity,
            Connection connection,
            List<LisaCatalog.Ref> projects) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(density, 16), dp(density, 8), dp(density, 16), dp(density, 8));

        String[] names = LisaCatalog.displayNames(projects);
        CheckedTextView[] rows = new CheckedTextView[projects.size()];
        final int[] selected = {-1};

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.lisa_catalog_load_project)
                .setView(wrapScroll(activity, container))
                .setPositiveButton(R.string.lisa_catalog_load, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        TypedValue selectable = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true);
        for (int i = 0; i < projects.size(); i++) {
            final int index = i;
            CheckedTextView row = new CheckedTextView(activity);
            row.setText(names[i]);
            row.setTextSize(18f);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(dp(density, 60));
            row.setPadding(dp(density, 18), 0, dp(density, 18), 0);
            row.setCheckMarkDrawable(android.R.drawable.btn_radio);
            row.setBackgroundResource(selectable.resourceId);
            row.setOnClickListener(v -> {
                if (selected[0] >= 0 && selected[0] < rows.length) {
                    rows[selected[0]].setChecked(false);
                }
                selected[0] = index;
                row.setChecked(true);
                Button load = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (load != null) {
                    load.setEnabled(true);
                }
            });
            rows[i] = row;
            container.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            if (i < projects.size() - 1) {
                View divider = new View(activity);
                divider.setBackgroundColor(0x33000000);
                container.addView(divider, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        Math.max(1, dp(density, 1))));
            }
        }

        dialog.setOnShowListener(shown -> {
            Button load = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (load == null) {
                return;
            }
            load.setEnabled(false);
            load.setOnClickListener(v -> {
                if (selected[0] < 0 || selected[0] >= projects.size()) {
                    return;
                }
                dialog.dismiss();
                importProject(activity, connection, projects.get(selected[0]));
            });
        });
        dialog.show();
    }

    private static ScrollView wrapScroll(NGActivity activity, LinearLayout container) {
        ScrollView scroll = new ScrollView(activity) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * 0.55f);
                int size = MeasureSpec.getSize(heightMeasureSpec);
                int mode = MeasureSpec.getMode(heightMeasureSpec);
                if (mode == MeasureSpec.UNSPECIFIED || size > maxHeight) {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        scroll.setFillViewport(false);
        scroll.addView(container, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private static int dp(float density, int value) {
        return Math.round(value * density);
    }

    private static void importProject(
            NGActivity activity,
            Connection connection,
            LisaCatalog.Ref project) {
        ProgressDialog progress = ProgressDialog.show(
                activity,
                activity.getString(R.string.lisa_catalog_load_project),
                activity.getString(R.string.waiting),
                true,
                false);
        EXECUTOR.execute(() -> {
            CollectorResource collector = LisaCatalogLookup.loadCollector(
                    connection, project.remoteId);
            activity.runOnUiThread(() -> {
                progress.dismiss();
                if (activity.isFinishing()) {
                    return;
                }
                if (collector == null) {
                    Toast.makeText(activity, R.string.error, Toast.LENGTH_LONG).show();
                    return;
                }
                startImport(activity, collector);
            });
        });
    }

    private static void startImport(Activity activity, CollectorResource collector) {
        if (ProjectSyncInterruption.confirmAndRun(
                activity, () -> startImport(activity, collector))) {
            return;
        }
        if (collector == null || collector.getConnection() == null) {
            Toast.makeText(activity, R.string.error, Toast.LENGTH_LONG).show();
            return;
        }
        if (!collector.isSnapshotComplete()) {
            HyperLog.e(Constants.TAG, "Lisa catalog import: incomplete snapshot remoteId="
                    + collector.getRemoteId() + " error=" + collector.getSnapshotError());
            Toast.makeText(activity, R.string.ngw_collector_incomplete_snapshot, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (collector.getProjectItems().isEmpty()) {
            Toast.makeText(activity, R.string.ngw_collector_no_supported_layers, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        Connection connection = collector.getConnection();
        CollectorProjectMetadata metadata = CollectorProjectMetadata.create(
                connection.getName(),
                collector.getRemoteId(),
                collector.getName(),
                collector.getProjectDistrict());
        CollectorProjectRegistry.PrepareWorkspaceResult prepareResult =
                CollectorProjectRegistry.prepareCollectorProjectWorkspaceForImport(
                        activity, metadata);
        LayerGroup workspace = prepareResult.getWorkspace();
        if (workspace == null) {
            if (prepareResult.isBusy()) {
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.ngw_collector_import_busy_title)
                        .setMessage(R.string.ngw_collector_import_busy_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            }
            Toast.makeText(activity, R.string.error, Toast.LENGTH_LONG).show();
            return;
        }
        ProjectOperationCoordinator.Lease importLease = prepareResult.getOperationLease();
        if (importLease == null) {
            Toast.makeText(activity, R.string.error, Toast.LENGTH_LONG).show();
            return;
        }
        boolean leaseTransferred = false;
        try {
        ArrayList<Intent> batch = new ArrayList<>();
        CollectorProjectImportHelper.Result result =
                CollectorProjectImportHelper.appendImportTasks(
                        activity, workspace, collector, batch);
        if (result == CollectorProjectImportHelper.Result.NO_SUPPORTED_ITEMS) {
            Toast.makeText(activity, R.string.ngw_collector_no_supported_layers, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (result == CollectorProjectImportHelper.Result.FAILED) {
            Toast.makeText(activity, R.string.error, Toast.LENGTH_LONG).show();
            return;
        }
        if (!batch.isEmpty()) {
            if (!LayerFillService.startFillBatch(activity, batch, importLease)) {
                Toast.makeText(activity, R.string.error, Toast.LENGTH_LONG).show();
                return;
            }
            leaseTransferred = true;
            LayerFillProgressDialogFragment.startBatchFillProgress(activity);
        }
        workspace.save();
        } finally {
            if (!leaseTransferred) {
                importLease.close();
            }
        }
    }
}
