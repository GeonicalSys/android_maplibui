/*
 * Project:  NextGIS Mobile
 * Purpose:  Shared initial import pipeline for supported Collector project items.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.nextgis.maplibui.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.datasource.ngw.CollectorProjectItem;
import com.nextgis.maplib.datasource.ngw.CollectorResource;
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.map.CollectorProjectMetadata;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.NGWRasterLayer;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.NGWUtil;
import com.nextgis.maplibui.service.LayerFillService;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps Activity and Dialog Collector imports identical.
 */
public final class CollectorProjectImportHelper {
    public enum Result {
        SUCCESS,
        NO_SUPPORTED_ITEMS,
        FAILED
    }

    private CollectorProjectImportHelper() {
    }

    public static Result appendImportTasks(
            Context context,
            LayerGroup group,
            CollectorResource collector,
            ArrayList<Intent> vectorFillBatch) {
        if (context == null
                || group == null
                || collector == null
                || vectorFillBatch == null
                || !(context.getApplicationContext() instanceof IGISApplication)) {
            return Result.FAILED;
        }
        List<CollectorProjectItem> items = collector.getProjectItems();
        if (items.isEmpty()) {
            return Result.NO_SUPPORTED_ITEMS;
        }
        Connection connection = collector.getConnection();
        if (connection == null
                || TextUtils.isEmpty(connection.getName())
                || TextUtils.isEmpty(connection.getURL())) {
            return Result.FAILED;
        }

        IGISApplication app = (IGISApplication) context.getApplicationContext();
        String projectUid = CollectorProjectMetadata.buildProjectUid(
                connection.getName(), collector.getRemoteId());
        String projectDistrict = collector.getProjectDistrict();
        group.setCollectorProjectMetadata(CollectorProjectMetadata.create(
                connection.getName(),
                collector.getRemoteId(),
                collector.getName(),
                projectDistrict));
        group.setCollectorDistrict(projectDistrict);
        try {
            if (!group.save()) {
                return Result.FAILED;
            }
        } catch (RuntimeException e) {
            HyperLog.w(Constants.TAG, "Collector import: metadata save failed: "
                    + e.getMessage(), e);
            return Result.FAILED;
        }

        long[] fullProjectOrder = new long[items.size()];
        ArrayList<IndexedItem> vectors = new ArrayList<>();
        ArrayList<IndexedItem> rasterStyles = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            CollectorProjectItem item = items.get(i);
            fullProjectOrder[i] = item.getRemoteId();
            if (LayerGroup.findCollectorManagedLayerByRemoteIdRecursive(
                    group,
                    item.getRemoteId(),
                    connection.getName(),
                    projectUid) != null) {
                HyperLog.d(Constants.TAG, "Collector import: skip existing supported item remoteId="
                        + item.getRemoteId() + " account=" + connection.getName());
                continue;
            }
            IndexedItem indexed = new IndexedItem(item, i);
            if (item.isVector()) {
                vectors.add(indexed);
            } else if (item.isRasterStyle()) {
                rasterStyles.add(indexed);
            }
        }

        if (!vectors.isEmpty() && !registerVectorBatch(
                app,
                group,
                connection,
                projectUid,
                vectors,
                fullProjectOrder)) {
            HyperLog.e(Constants.TAG, "Collector import aborted: vector batch registration failed"
                    + " group=" + group.getId() + " account=" + connection.getName());
            return Result.FAILED;
        }

        ArrayList<NGWRasterLayer> addedRasterLayers = new ArrayList<>();
        for (IndexedItem indexed : rasterStyles) {
            NGWRasterLayer added = CollectorRasterLayerHelper.addStyleLayer(
                    group,
                    connection.getURL(),
                    connection.getName(),
                    projectUid,
                    indexed.item,
                    indexed.order,
                    fullProjectOrder);
            if (added != null) {
                addedRasterLayers.add(added);
            }
        }
        try {
            if (!group.save()) {
                rollbackRasterLayers(group, addedRasterLayers);
                if (!vectors.isEmpty()) {
                    app.clearCollectorImportBatch();
                }
                return Result.FAILED;
            }
        } catch (RuntimeException e) {
            HyperLog.w(Constants.TAG, "Collector import: group save failed: " + e.getMessage(), e);
            rollbackRasterLayers(group, addedRasterLayers);
            if (!vectors.isEmpty()) {
                app.clearCollectorImportBatch();
            }
            return Result.FAILED;
        }

        for (IndexedItem indexed : vectors) {
            vectorFillBatch.add(buildVectorIntent(
                    context,
                    group,
                    connection,
                    projectUid,
                    indexed,
                    fullProjectOrder));
        }
        if (!addedRasterLayers.isEmpty()) {
            app.requestMapReloadAfterLayerFillBatch();
        }
        HyperLog.d(Constants.TAG, NGWVectorLayer.LOG_DISTRICT_FILTER
                + " import collector=\"" + collector.getName() + "\""
                + " remoteId=" + collector.getRemoteId()
                + (TextUtils.isEmpty(projectDistrict)
                ? " no resmeta district; collector_district cleared"
                : " district=" + projectDistrict)
                + " vectorsQueued=" + vectors.size()
                + " rasterStylesAdded=" + addedRasterLayers.size());
        return Result.SUCCESS;
    }

    private static void rollbackRasterLayers(
            LayerGroup group,
            List<NGWRasterLayer> addedLayers) {
        for (NGWRasterLayer layer : addedLayers) {
            group.removeLayer(layer);
            layer.delete(true);
        }
        try {
            group.save();
        } catch (RuntimeException ignored) {
        }
    }

    private static boolean registerVectorBatch(
            IGISApplication app,
            LayerGroup group,
            Connection connection,
            String projectUid,
            List<IndexedItem> vectors,
            long[] fullProjectOrder) {
        int count = vectors.size();
        long[] ids = new long[count];
        String[] names = new String[count];
        String[] configs = new String[count];
        long[] formIds = new long[count];
        boolean[] editables = new boolean[count];
        for (int i = 0; i < count; i++) {
            CollectorProjectItem item = vectors.get(i).item;
            ids[i] = item.getRemoteId();
            names[i] = item.getName();
            configs[i] = item.getConfigJson();
            formIds[i] = item.getFormId();
            editables[i] = item.isCollectorEditable();
        }
        return app.registerCollectorImportBatch(
                group.getId(),
                connection.getName(),
                projectUid,
                ids,
                names,
                configs,
                formIds,
                editables,
                fullProjectOrder);
    }

    private static Intent buildVectorIntent(
            Context context,
            LayerGroup group,
            Connection connection,
            String projectUid,
            IndexedItem indexed,
            long[] fullProjectOrder) {
        CollectorProjectItem item = indexed.item;
        Intent intent = new Intent(context, LayerFillService.class);
        intent.setAction(LayerFillService.ACTION_ADD_TASK);
        intent.putExtra(LayerFillService.KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, true);
        intent.putExtra(LayerFillService.KEY_NAME, item.getName());
        intent.putExtra(LayerFillService.KEY_ACCOUNT, connection.getName());
        intent.putExtra(LayerFillService.KEY_REMOTE_ID, item.getRemoteId());
        intent.putExtra(LayerFillService.KEY_LAYER_GROUP_ID, group.getId());
        intent.putExtra(LayerFillService.KEY_INPUT_TYPE, LayerFillService.NGW_LAYER);
        intent.putExtra(LayerFillService.KEY_COLLECTOR_PROJECT_UID, projectUid);
        intent.putExtra(
                LayerFillService.KEY_COLLECTOR_TRACKING_REMOTE_ID,
                item.getRemoteId());
        intent.putExtra(
                LayerFillService.KEY_COLLECTOR_ORDER_INDEX,
                indexed.order);
        intent.putExtra(
                LayerFillService.KEY_COLLECTOR_PROJECT_REMOTE_IDS,
                fullProjectOrder);
        intent.putExtra(
                LayerFillService.KEY_COLLECTOR_LAYER_EDITABLE,
                item.isCollectorEditable());
        if (!TextUtils.isEmpty(item.getConfigJson())) {
            intent.putExtra(
                    LayerFillService.KEY_LAYER_CONFIG_JSON,
                    item.getConfigJson());
        }
        if (item.getFormId() > 0L) {
            intent.putExtra(
                    LayerFillService.KEY_LAYER_ORIGIN_FORM_ID,
                    item.getFormId());
            intent.putExtra(
                    LayerFillService.KEY_DEFAULT_FORM_IDS,
                    new long[]{item.getFormId()});
            intent.putExtra(
                    LayerFillService.KEY_URI,
                    Uri.parse(NGWUtil.getFormUrl(
                            connection.getURL(), item.getFormId())));
            intent.putExtra(
                    LayerFillService.KEY_INPUT_TYPE,
                    LayerFillService.VECTOR_LAYER_WITH_FORM);
        }
        return intent;
    }

    private static final class IndexedItem {
        final CollectorProjectItem item;
        final int order;

        IndexedItem(CollectorProjectItem item, int order) {
            this.item = item;
            this.order = order;
        }
    }
}
