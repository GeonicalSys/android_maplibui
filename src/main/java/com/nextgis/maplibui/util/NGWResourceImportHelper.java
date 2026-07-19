package com.nextgis.maplibui.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.datasource.ngw.LayerWithStyles;
import com.nextgis.maplib.datasource.ngw.Resource;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.NGWRasterLayer;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.NGWUtil;
import com.nextgis.maplibui.fragment.LayerFillProgressDialogFragment;
import com.nextgis.maplibui.mapui.NGWRasterLayerUI;
import com.nextgis.maplibui.service.LayerFillService;

import java.util.ArrayList;

/** Starts the existing layer-fill pipeline for a resource resolved from a URL. */
public final class NGWResourceImportHelper {
    public enum Result {
        VECTOR_QUEUED,
        RASTER_ADDED,
        READ_PERMISSION_DENIED,
        UNSUPPORTED,
        FAILED
    }

    private NGWResourceImportHelper() {
    }

    public static boolean supports(Resource resource) {
        if (!(resource instanceof LayerWithStyles)) {
            return false;
        }
        int type = resource.getType();
        return type == Connection.NGWResourceTypeVectorLayer
                || type == Connection.NGWResourceTypePostgisLayer
                || type == Connection.NGWResourceTypeRasterLayer;
    }

    public static Result importResource(Activity activity, LayerGroup group, Resource resource) {
        if (activity == null || group == null || resource == null) {
            return Result.FAILED;
        }
        if (!supports(resource)) {
            return Result.UNSUPPORTED;
        }
        if (!resource.hasDataReadPermission()) {
            return Result.READ_PERMISSION_DENIED;
        }

        int type = resource.getType();
        if (type == Connection.NGWResourceTypeVectorLayer
                || type == Connection.NGWResourceTypePostgisLayer) {
            return queueVectorImport(activity, group, (LayerWithStyles) resource);
        }
        if (type == Connection.NGWResourceTypeRasterLayer) {
            return addRasterLayer(activity, group, (LayerWithStyles) resource);
        }
        return Result.UNSUPPORTED;
    }

    private static Result queueVectorImport(
            Activity activity, LayerGroup group, LayerWithStyles layer) {
        Connection connection = layer.getConnection();
        if (connection == null || TextUtils.isEmpty(connection.getName())) {
            return Result.FAILED;
        }

        Intent intent = new Intent(activity, LayerFillService.class);
        intent.setAction(LayerFillService.ACTION_ADD_TASK);
        intent.putExtra(LayerFillService.KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, true);
        intent.putExtra(LayerFillService.KEY_NAME, layer.getName());
        intent.putExtra(LayerFillService.KEY_ACCOUNT, connection.getName());
        intent.putExtra(LayerFillService.KEY_REMOTE_ID, layer.getRemoteId());
        intent.putExtra(LayerFillService.KEY_LAYER_GROUP_ID, group.getId());
        intent.putExtra(LayerFillService.KEY_INPUT_TYPE, LayerFillService.NGW_LAYER);
        intent.putExtra(LayerFillService.KEY_MARK_MANUAL_NGW_ORIGIN, true);
        intent.putExtra(
                LayerFillService.KEY_SERVER_WRITE_PERMITTED,
                layer.hasDataWritePermission());

        String description = layer.getDescription();
        if (!TextUtils.isEmpty(description)) {
            intent.putExtra(LayerFillService.KEY_LAYER_CONFIG_JSON, description);
        }

        if (layer.getFormCount() > 0) {
            Long formId = layer.getFormId(0);
            if (formId != null && formId > 0L) {
                intent.putExtra(LayerFillService.KEY_LAYER_ORIGIN_FORM_ID, formId);
                intent.putExtra(LayerFillService.KEY_DEFAULT_FORM_IDS, new long[]{formId});
                intent.putExtra(
                        LayerFillService.KEY_URI,
                        Uri.parse(NGWUtil.getFormUrl(connection.getURL(), formId)));
                intent.putExtra(
                        LayerFillService.KEY_INPUT_TYPE,
                        LayerFillService.VECTOR_LAYER_WITH_FORM);
            }
        }

        ArrayList<Intent> batch = new ArrayList<>();
        batch.add(intent);
        LayerFillService.startFillBatch(activity, batch);
        LayerFillProgressDialogFragment.startBatchFillProgress(activity);
        return Result.VECTOR_QUEUED;
    }

    private static Result addRasterLayer(
            Activity activity, LayerGroup group, LayerWithStyles layer) {
        Connection connection = layer.getConnection();
        String layerUrl = layer.getTMSUrl(0);
        if (connection == null || TextUtils.isEmpty(layerUrl)) {
            return Result.FAILED;
        }

        NGWRasterLayer newLayer = new NGWRasterLayerUI(
                group.getContext(), group.createLayerStorage());
        if (layer.getExtent() != null && layer.getExtent().isInit()) {
            newLayer.getExtents().set(layer.getExtent());
        }
        newLayer.setName(layer.getName());
        newLayer.setRemoteId(layer.getRemoteId());
        newLayer.setURL(layerUrl);
        newLayer.setTMSType(GeoConstants.TMSTYPE_OSM);
        newLayer.setVisible(true);
        newLayer.setAccountName(connection.getName());
        newLayer.setMinZoom(GeoConstants.DEFAULT_MIN_ZOOM);
        newLayer.setMaxZoom(GeoConstants.DEFAULT_MAX_ZOOM);

        group.addLayer(newLayer);
        group.save();
        ((IGISApplication) activity.getApplicationContext())
                .requestMapReloadAfterLayerFillBatch();
        return Result.RASTER_ADDED;
    }
}
