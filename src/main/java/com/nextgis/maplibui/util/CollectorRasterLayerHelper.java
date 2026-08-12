/*
 * Project:  NextGIS Mobile
 * Purpose:  Materialize supported Collector style resources as NGW raster tile layers.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.nextgis.maplibui.util;

import android.text.TextUtils;

import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.datasource.ngw.CollectorProjectItem;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.LayerOriginMetadata;
import com.nextgis.maplib.map.NGWRasterLayer;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.NGWUtil;
import com.nextgis.maplibui.mapui.NGWRasterLayerUI;

/**
 * Shared creation path for initial Collector import and composition synchronization.
 */
public final class CollectorRasterLayerHelper {
    private CollectorRasterLayerHelper() {
    }

    /**
     * Adds one supported style unless this exact project-managed resource already exists.
     *
     * @return the newly created layer, or {@code null} when input is invalid or already present.
     */
    public static NGWRasterLayer addStyleLayer(
            LayerGroup group,
            String serverUrl,
            String accountName,
            String projectUid,
            CollectorProjectItem item,
            int collectorOrder,
            long[] fullProjectOrder) {
        if (group == null
                || item == null
                || !item.isRasterStyle()
                || item.getRemoteId() <= 0L
                || TextUtils.isEmpty(serverUrl)
                || TextUtils.isEmpty(accountName)
                || TextUtils.isEmpty(projectUid)) {
            return null;
        }

        ILayer existing = LayerGroup.findCollectorManagedLayerByRemoteIdRecursive(
                group, item.getRemoteId(), accountName, projectUid);
        if (existing != null) {
            return null;
        }

        NGWRasterLayer layer = new NGWRasterLayerUI(
                group.getContext(), group.createLayerStorage());
        layer.setName(item.getName());
        layer.setRemoteId(item.getRemoteId());
        layer.setExtentRemoteId(item.getExtentRemoteId());
        layer.setURL(NGWUtil.getTMSUrl(serverUrl, new Long[]{item.getRemoteId()}));
        layer.setTMSType(GeoConstants.TMSTYPE_OSM);
        layer.setVisible(item.isVisible());
        layer.setAccountName(accountName);
        layer.setMinZoom(item.getMinZoom());
        layer.setMaxZoom(item.getMaxZoom());
        layer.setTileMaxAge(item.getTileMaxAge());
        layer.setLayerOriginMetadata(LayerOriginMetadata.collectorLayer(
                projectUid, collectorOrder, 0L));

        int insertAt = fullProjectOrder != null
                ? LayerGroup.computeCollectorOrderedInsertIndex(
                        group, accountName, fullProjectOrder, collectorOrder)
                : group.getLayerCount();
        group.insertLayer(insertAt, layer);
        return layer;
    }

    /**
     * Updates display state and order metadata without replacing the local tile cache.
     *
     * @return {@code true} when persisted layer/group state needs saving.
     */
    public static boolean applyStyleState(
            LayerGroup parentGroup,
            NGWRasterLayer layer,
            CollectorProjectItem item,
            int collectorOrder,
            long[] fullProjectOrder) {
        if (parentGroup == null || layer == null || item == null || !item.isRasterStyle()) {
            return false;
        }
        LayerOriginMetadata origin = layer.getLayerOriginMetadata();
        if (origin == null
                || !origin.isManagedByProject()
                || TextUtils.isEmpty(origin.getProjectUid())) {
            return false;
        }

        boolean changed = false;
        if (!TextUtils.equals(layer.getName(), item.getName())) {
            layer.setName(item.getName());
            changed = true;
        }
        if (layer.isVisible() != item.isVisible()) {
            layer.setVisible(item.isVisible());
            changed = true;
        }
        if (Float.compare(layer.getMinZoom(), item.getMinZoom()) != 0) {
            layer.setMinZoom(item.getMinZoom());
            changed = true;
        }
        if (Float.compare(layer.getMaxZoom(), item.getMaxZoom()) != 0) {
            layer.setMaxZoom(item.getMaxZoom());
            changed = true;
        }
        if (layer.getTileMaxAge() != item.getTileMaxAge()) {
            layer.setTileMaxAge(item.getTileMaxAge());
            changed = true;
        }
        if (layer.getExtentRemoteId() != item.getExtentRemoteId()) {
            layer.setExtentRemoteId(item.getExtentRemoteId());
            changed = true;
        }

        int newOrder = collectorOrder >= 0 ? collectorOrder : origin.getCollectorOrder();
        boolean orderChanged = newOrder >= 0 && origin.getCollectorOrder() != newOrder;
        if (orderChanged) {
            LayerOriginMetadata updated = LayerOriginMetadata.collectorLayer(
                    origin.getProjectUid(), newOrder, 0L, origin.getRenderMode());
            layer.setLayerOriginMetadata(updated);
            changed = true;
        }
        if (orderChanged && fullProjectOrder != null) {
            parentGroup.removeLayer(layer);
            int insertAt = LayerGroup.computeCollectorOrderedInsertIndex(
                    parentGroup,
                    layer.getAccountName(),
                    fullProjectOrder,
                    newOrder);
            parentGroup.insertLayer(insertAt, layer);
        }
        return changed;
    }
}
