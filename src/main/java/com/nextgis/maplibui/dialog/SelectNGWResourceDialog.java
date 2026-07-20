/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * Author:   NikitaFeodonit, nfeodonit@yandex.com
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2015-2017, 2021 NextGIS, info@nextgis.com
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

package com.nextgis.maplibui.dialog;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.datasource.ngw.CollectorResource;
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.datasource.ngw.Connections;
import com.nextgis.maplib.datasource.ngw.INGWResource;
import com.nextgis.maplib.datasource.ngw.LayerWithStyles;
import com.nextgis.maplib.datasource.ngw.Resource;
import com.nextgis.maplib.datasource.ngw.WebMap;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.NGWUtil;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.map.CollectorProjectMetadata;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.NGWRasterLayer;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.activity.NGWLoginActivity;
import com.nextgis.maplibui.fragment.LayerFillProgressDialogFragment;
import com.nextgis.maplibui.mapui.NGWRasterLayerUI;
import com.nextgis.maplibui.mapui.NGWWebMapLayerUI;
import com.nextgis.maplibui.service.LayerFillService;
import com.nextgis.maplibui.util.CheckState;
import com.nextgis.maplibui.util.CollectorProjectRegistry;

import java.util.ArrayList;
import java.util.List;

import static com.nextgis.maplib.util.Constants.TAG;
import static com.nextgis.maplib.util.GeoConstants.TMSTYPE_OSM;


public class SelectNGWResourceDialog
        extends NGDialog
{
    protected LayerGroup mGroupLayer;
    protected int        mTypeMask;

    protected NGWResourcesListAdapter mListAdapter;
    protected AlertDialog             mDialog;
    protected AccountManager          mAccountManager;
    protected NGWResourcesListAdapter.OnConnectionListener mConnectionListener;
    boolean skipSubLoad = false;

    protected final static String KEY_MASK        = "mask";
    protected final static String KEY_ID          = "id";
    protected final static String KEY_CONNECTIONS = "connections";
    protected final static String KEY_RESOURCE_ID = "resource_id";
    protected final static String KEY_STATES      = "states";

    protected final static int ADD_ACCOUNT_CODE = 777;


    public SelectNGWResourceDialog(boolean skipSubLoad){
        this.skipSubLoad = skipSubLoad;
    }


    public SelectNGWResourceDialog setLayerGroup(LayerGroup groupLayer)
    {
        mGroupLayer = groupLayer;
        return this;
    }


    public SelectNGWResourceDialog setTypeMask(int typeMask)
    {
        mTypeMask = typeMask;
        return this;
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        super.onCreateDialog(savedInstanceState);
        mAccountManager = AccountManager.get(getActivity().getApplicationContext());
        Log.d(TAG, "SelectNGWDialog: AccountManager.get(" + getActivity().getApplicationContext() + ")");
        mListAdapter = new NGWResourcesListAdapter(getActivity());

        if (null == savedInstanceState) {
            //first launch, lets fill connections array
            Connections connections = fillConnections(getActivity(), mAccountManager);
            mListAdapter.setConnections(connections, skipSubLoad);
            mListAdapter.setCurrentResourceId(connections.getId());
            mListAdapter.setCheckState(new ArrayList<CheckState>());
        } else {
            mTypeMask = savedInstanceState.getInt(KEY_MASK);
            int id = savedInstanceState.getInt(KEY_ID);
            MapBase map = MapBase.getInstance();
            if (null != map) {
                ILayer iLayer = map.getLayerById(id);
                if (iLayer instanceof LayerGroup) {
                    mGroupLayer = (LayerGroup) iLayer;
                }
            }

            mListAdapter.setConnections((Connections) savedInstanceState.getParcelable(KEY_CONNECTIONS), skipSubLoad);
            mListAdapter.setCurrentResourceId(savedInstanceState.getInt(KEY_RESOURCE_ID));
            mListAdapter.setCheckState(savedInstanceState.<CheckState> getParcelableArrayList(KEY_STATES));
        }

        View view = View.inflate(mContextWeakRef.get(), R.layout.layout_resources, null);
        ListView dialogListView = (ListView) view.findViewById(R.id.listView);
        mListAdapter.setTypeMask(mTypeMask);
        dialogListView.setAdapter(mListAdapter);
        dialogListView.setOnItemClickListener(mListAdapter);

        LinearLayout pathView = (LinearLayout) view.findViewById(R.id.path);
        mListAdapter.setPathLayout(pathView);

//        AlertDialog.Builder builder = new AlertDialog.Builder(mContext, mDialogTheme);
        AlertDialog.Builder builder = new AlertDialog.Builder(mContextWeakRef.get());
        builder.setTitle(mTitle)
                .setIcon(R.drawable.ic_ngw)
                .setView(view)
                .setInverseBackgroundForced(true)
//                .setPositiveButton(
//                        R.string.add, new DialogInterface.OnClickListener()
//                        {
//                            public void onClick(
//                                    DialogInterface dialog,
//                                    int id)
//                            {
//                                createLayers(mContextWeakRef.get());
//                            }
//                        })
                .setNegativeButton(R.string.cancel, null);

        // Create the AlertDialog object and return it
        mDialog = builder.create();
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                mEnabledColor = mDialog.getButton(DialogInterface.BUTTON_POSITIVE).getTextColors().getDefaultColor();
                updateSelectButton();
            }
        });

        mListAdapter.setConnectionListener(mConnectionListener);

        return mDialog;
    }


    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        if (null != mGroupLayer)
            outState.putInt(KEY_ID, mGroupLayer.getId());

        if (null != mListAdapter) {
            outState.putInt(KEY_MASK, mTypeMask);
            outState.putInt(KEY_RESOURCE_ID, mListAdapter.getCurrentResourceId());
            outState.putParcelable(KEY_CONNECTIONS, mListAdapter.getConnections());
            outState.putParcelableArrayList(
                    KEY_STATES,
                    (ArrayList<? extends android.os.Parcelable>) mListAdapter.getCheckState());
        }
    }


    public NGDialog setConnectionListener(NGWResourcesListAdapter.OnConnectionListener connectionListener) {
        mConnectionListener = connectionListener;
        return this;
    }


    public static Connections fillConnections(Context context, AccountManager accountManager)
    {
        Connections connections = new Connections(context.getString(R.string.ngw_accounts));
        IGISApplication app = (IGISApplication) context.getApplicationContext();

        for (Account account : accountManager.getAccountsByType(app.getAccountsType())) {
            String url = app.getAccountUrl(account);
            String password = app.getAccountPassword(account);
            String login = app.getAccountLogin(account);
            connections.add(new Connection(account.name, login, password, url.toLowerCase()));
        }
        return connections;
    }


    public void onAddAccount(Context context)
    {
        Intent intent = new Intent(context, NGWLoginActivity.class);
        startActivityForResult(intent, ADD_ACCOUNT_CODE);
    }


    @Override
    public void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data)
    {
        if (requestCode == ADD_ACCOUNT_CODE) {
            if (resultCode != Activity.RESULT_CANCELED) {
                //search new account and add it
                Connections connections = mListAdapter.getConnections();
                IGISApplication app = (IGISApplication) mContextWeakRef.get().getApplicationContext();

                for (Account account : mAccountManager.getAccountsByType(app.getAccountsType())) {
                    boolean find = false;
                    for (int i = 0; i < connections.getChildrenCount(); i++) {
                        Connection connection = (Connection) connections.getChild(i);
                        if (null != connection && connection.getName().equals(account.name)) {
                            find = true;
                            break;
                        }
                    }

                    if (!find) {
                        String url = app.getAccountUrl(account);
                        String password = app.getAccountPassword(account);
                        String login = app.getAccountLogin(account);
                        connections.add(new Connection(account.name, login, password, url.toLowerCase()));
                        mListAdapter.notifyDataSetChanged();
                        break;
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    public void updateSelectButton() {
        boolean active = mListAdapter.getCheckState().size() > 0;
        setEnabled(mDialog.getButton(AlertDialog.BUTTON_POSITIVE), active);
    }


    public void createLayers(Context context) {
        if (mGroupLayer == null)
            return;

        setEnabled(mDialog.getButton(AlertDialog.BUTTON_POSITIVE), false);
        setEnabled(mDialog.getButton(AlertDialog.BUTTON_NEGATIVE), false);

        List<CheckState> checkStates = mListAdapter.getCheckState();
        Connections connections = mListAdapter.getConnections();
        int selectedCollectorCount = countSelectedCollectorResources(connections, checkStates);
        if (selectedCollectorCount > 1) {
            Toast.makeText(context, R.string.error, Toast.LENGTH_LONG).show();
            HyperLog.w(Constants.TAG, "Collector import (dialog): multiple collector projects selected in one batch");
            setEnabled(mDialog.getButton(AlertDialog.BUTTON_POSITIVE), true);
            setEnabled(mDialog.getButton(AlertDialog.BUTTON_NEGATIVE), true);
            return;
        }
        if (selectedCollectorCount == 1) {
            CollectorResource selectedCollector = findSelectedCollectorResource(connections, checkStates);
            if (selectedCollector == null || !prepareCollectorWorkspaceForImport(context, selectedCollector)) {
                setEnabled(mDialog.getButton(AlertDialog.BUTTON_POSITIVE), true);
                setEnabled(mDialog.getButton(AlertDialog.BUTTON_NEGATIVE), true);
                return;
            }
        }

        final ArrayList<Intent> vectorFillBatch = new ArrayList<>();
        for (CheckState checkState : checkStates) {
            if (checkState.isCheckState1()) { //create raster

                INGWResource resource = connections.getResourceById(checkState.getId());
                if (resource instanceof LayerWithStyles) {
                    LayerWithStyles layer = (LayerWithStyles) resource;
                    //1. get first style
                    Connection connection = layer.getConnection();
                    //2. create tiles url
                    String layerURL = layer.getTMSUrl(0);

                    if (layerURL == null) {
                        Toast.makeText(
                                context, getString(com.nextgis.maplib.R.string.error_layer_create),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    //3. create layer
                    String layerName = layer.getName();

                    NGWRasterLayer newLayer;
                    if (resource instanceof WebMap) {
                        NGWWebMapLayerUI webmap = new NGWWebMapLayerUI(mGroupLayer.getContext(), mGroupLayer.createLayerStorage());
                        webmap.setChildren(((WebMap) resource).getChildren());
                        newLayer = webmap;
                    } else
                        newLayer = new NGWRasterLayerUI(mGroupLayer.getContext(), mGroupLayer.createLayerStorage());

                    newLayer.setName(layerName);
                    newLayer.setURL(layerURL);
                    newLayer.setTMSType(TMSTYPE_OSM);
                    newLayer.setVisible(true);
                    newLayer.setAccountName(connection.getName());
                    newLayer.setMinZoom(GeoConstants.DEFAULT_MIN_ZOOM);
                    newLayer.setMaxZoom(GeoConstants.DEFAULT_MAX_ZOOM);

                    mGroupLayer.addLayer(newLayer);
                    mGroupLayer.save();
                }
            }

            if (checkState.isCheckState2()) { //create vector or collector import
                INGWResource resource = connections.getResourceById(checkState.getId());
                if (resource instanceof CollectorResource) {
                    CollectorResource collector = (CollectorResource) resource;
                    List<LayerWithStyles> layers = collector.getLayers();
                    if (layers.isEmpty()) {
                        Toast.makeText(context, R.string.ngw_collector_no_vector_layers, Toast.LENGTH_LONG).show();
                        HyperLog.w(Constants.TAG, "Collector import (dialog): 0 layers remoteId="
                                + collector.getRemoteId());
                        setEnabled(mDialog.getButton(AlertDialog.BUTTON_POSITIVE), true);
                        setEnabled(mDialog.getButton(AlertDialog.BUTTON_NEGATIVE), true);
                        return;
                    }
                    Connection connection = collector.getConnection();
                    String projectDistrict = collector.getProjectDistrict();
                    String collectorProjectUid = CollectorProjectMetadata.buildProjectUid(
                            connection.getName(), collector.getRemoteId());
                    // Collector architecture foundation: persist project identity now so future
                    // composition/form/tile sync can work without re-importing local layer data.
                    mGroupLayer.setCollectorProjectMetadata(CollectorProjectMetadata.create(
                            connection.getName(), collector.getRemoteId(),
                            collector.getName(), projectDistrict));
                    mGroupLayer.save();
                    if (!TextUtils.isEmpty(projectDistrict)) {
                        mGroupLayer.setCollectorDistrict(projectDistrict);
                        mGroupLayer.save();
                        HyperLog.d(Constants.TAG, NGWVectorLayer.LOG_DISTRICT_FILTER + " import (dialog) collector=\""
                                + collector.getName() + "\" remoteId=" + collector.getRemoteId()
                                + " district=" + projectDistrict);
                    } else {
                        mGroupLayer.setCollectorDistrict(null);
                        mGroupLayer.save();
                        HyperLog.d(Constants.TAG, NGWVectorLayer.LOG_DISTRICT_FILTER + " import (dialog) collector=\""
                                + collector.getName() + "\" remoteId=" + collector.getRemoteId()
                                + " no resmeta district; collector_district cleared");
                    }
                    ArrayList<LayerWithStyles> toImport = new ArrayList<>();
                    for (int li = 0; li < layers.size(); li++) {
                        LayerWithStyles layer = layers.get(li);
                        if (LayerGroup.findNgwVectorLayerByRemoteIdRecursive(
                                mGroupLayer, layer.getRemoteId(), connection.getName()) != null) {
                            HyperLog.d(TAG, "Collector import (dialog): skip existing remoteId="
                                    + layer.getRemoteId() + " account=" + connection.getName());
                            continue;
                        }
                        toImport.add(layer);
                    }
                    if (!toImport.isEmpty()) {
                        long[] fullProjectOrder = new long[layers.size()];
                        for (int pi = 0; pi < layers.size(); pi++) {
                            fullProjectOrder[pi] = layers.get(pi).getRemoteId();
                        }
                        int n = toImport.size();
                        long[] ids = new long[n];
                        String[] nms = new String[n];
                        String[] cfgs = new String[n];
                        long[] fids = new long[n];
                        boolean[] collectorEditables = new boolean[n];
                        for (int ord = 0; ord < n; ord++) {
                            LayerWithStyles layer = toImport.get(ord);
                            ids[ord] = layer.getRemoteId();
                            nms[ord] = layer.getName();
                            String desc = layer.getDescription();
                            cfgs[ord] = (desc != null && !desc.isEmpty()) ? desc : null;
                            collectorEditables[ord] = layer.isCollectorEditable();
                            long formId = 0L;
                            if (layer.getFormCount() > 0) {
                                Long fid = layer.getFormId(0);
                                if (fid != null) {
                                    formId = fid;
                                }
                            }
                            fids[ord] = formId;
                        }
                        boolean batchRegistered = ((IGISApplication) context.getApplicationContext()).registerCollectorImportBatch(
                                mGroupLayer.getId(), connection.getName(), collectorProjectUid, ids, nms, cfgs, fids,
                                collectorEditables, fullProjectOrder);
                        if (!batchRegistered) {
                            // No registered batch => no verify/repair; do not run a partial collector import.
                            HyperLog.e(Constants.TAG, "Collector import (dialog) aborted: batch registration failed group="
                                    + mGroupLayer.getId() + " account=" + connection.getName());
                            Toast.makeText(context, R.string.error, Toast.LENGTH_LONG).show();
                            setEnabled(mDialog.getButton(AlertDialog.BUTTON_POSITIVE), true);
                            setEnabled(mDialog.getButton(AlertDialog.BUTTON_NEGATIVE), true);
                            return;
                        } else {
                        for (int ord = 0; ord < n; ord++) {
                            LayerWithStyles layer = toImport.get(ord);
                            int projectIndex = -1;
                            long layerRid = layer.getRemoteId();
                            for (int pi = 0; pi < layers.size(); pi++) {
                                if (layers.get(pi).getRemoteId() == layerRid) {
                                    projectIndex = pi;
                                    break;
                                }
                            }
                            Intent intent = new Intent(context, LayerFillService.class);
                            intent.setAction(LayerFillService.ACTION_ADD_TASK);
                            intent.putExtra(LayerFillService.KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, true);
                            intent.putExtra(LayerFillService.KEY_NAME, layer.getName());
                            intent.putExtra(LayerFillService.KEY_ACCOUNT, connection.getName());
                            intent.putExtra(LayerFillService.KEY_REMOTE_ID, layer.getRemoteId());
                            intent.putExtra(LayerFillService.KEY_LAYER_GROUP_ID, mGroupLayer.getId());
                            intent.putExtra(LayerFillService.KEY_INPUT_TYPE, LayerFillService.NGW_LAYER);
                            if (!TextUtils.isEmpty(collectorProjectUid)) {
                                intent.putExtra(LayerFillService.KEY_COLLECTOR_PROJECT_UID, collectorProjectUid);
                            }
                            String desc = layer.getDescription();
                            if (desc != null && !desc.isEmpty()) {
                                intent.putExtra(LayerFillService.KEY_LAYER_CONFIG_JSON, desc);
                            }
                            long formId = 0L;
                            if (layer.getFormCount() > 0 && layer.getFormId(0) != null) {
                                formId = layer.getFormId(0);
                                intent.putExtra(LayerFillService.KEY_LAYER_ORIGIN_FORM_ID, formId);
                                intent.putExtra(LayerFillService.KEY_DEFAULT_FORM_IDS,
                                        new long[]{formId});
                                String path = NGWUtil.getFormUrl(connection.getURL(), formId);
                                intent.putExtra(LayerFillService.KEY_URI, Uri.parse(path));
                                intent.putExtra(LayerFillService.KEY_INPUT_TYPE, LayerFillService.VECTOR_LAYER_WITH_FORM);
                            }
                            intent.putExtra(LayerFillService.KEY_COLLECTOR_TRACKING_REMOTE_ID, layer.getRemoteId());
                            if (projectIndex >= 0) {
                                intent.putExtra(LayerFillService.KEY_COLLECTOR_ORDER_INDEX, projectIndex);
                                intent.putExtra(LayerFillService.KEY_COLLECTOR_PROJECT_REMOTE_IDS, fullProjectOrder);
                            }
                            intent.putExtra(LayerFillService.KEY_COLLECTOR_LAYER_EDITABLE, layer.isCollectorEditable());
                            vectorFillBatch.add(intent);
                        }
                        }
                    }
                } else if (resource instanceof LayerWithStyles) {
                    LayerWithStyles layer = (LayerWithStyles) resource;
                    //1. get connection for url
                    Connection connection = layer.getConnection();
                    // create or connect to fill layer with features
                    Intent intent = new Intent(context, LayerFillService.class);
                    intent.setAction(LayerFillService.ACTION_ADD_TASK);
                    intent.putExtra(LayerFillService.KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, true);
                    intent.putExtra(LayerFillService.KEY_NAME, layer.getName());
                    intent.putExtra(LayerFillService.KEY_ACCOUNT, connection.getName());
                    intent.putExtra(LayerFillService.KEY_REMOTE_ID, layer.getRemoteId());
                    intent.putExtra(LayerFillService.KEY_LAYER_GROUP_ID, mGroupLayer.getId());
                    intent.putExtra(LayerFillService.KEY_INPUT_TYPE, LayerFillService.NGW_LAYER);
                    intent.putExtra(LayerFillService.KEY_MARK_MANUAL_NGW_ORIGIN, true);
                    Resource remoteResource = (Resource) resource;
                    if (remoteResource.hasDataPermissionInfo()) {
                        intent.putExtra(
                                LayerFillService.KEY_SERVER_WRITE_PERMITTED,
                                remoteResource.hasDataWritePermission());
                    }

                    if (layer.getFormCount() > 0) {
                        Long formId = layer.getFormId(0);
                        if (formId != null && formId > 0L) {
                            intent.putExtra(LayerFillService.KEY_LAYER_ORIGIN_FORM_ID, formId);
                            intent.putExtra(LayerFillService.KEY_DEFAULT_FORM_IDS,
                                    new long[]{formId});
                            String path = NGWUtil.getFormUrl(connection.getURL(), formId);
                            intent.putExtra(LayerFillService.KEY_URI, Uri.parse(path));
                            intent.putExtra(LayerFillService.KEY_INPUT_TYPE, LayerFillService.VECTOR_LAYER_WITH_FORM);
                        }
                    }

                    vectorFillBatch.add(intent);
                }
            }
        }
        if (!vectorFillBatch.isEmpty()) {
            Activity hostActivity = LayerFillProgressDialogFragment.getProgressHostActivity();
            if (hostActivity == null) {
                hostActivity = context instanceof Activity ? (Activity) context : getActivity();
            }
            if (hostActivity == null) {
                Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show();
                setEnabled(mDialog.getButton(AlertDialog.BUTTON_POSITIVE), true);
                setEnabled(mDialog.getButton(AlertDialog.BUTTON_NEGATIVE), true);
                return;
            }
            // Single FGS start for the whole batch (one stopSelf() at drain end) avoids the
            // foreground-service lifecycle race of N separate startService deliveries.
            LayerFillService.startFillBatch(hostActivity, vectorFillBatch);
            LayerFillProgressDialogFragment.startBatchFillProgress(hostActivity);
        }
        mGroupLayer.save();
    }

    private int countSelectedCollectorResources(Connections connections, List<CheckState> checkStates) {
        int count = 0;
        if (connections == null || checkStates == null) {
            return count;
        }
        for (CheckState checkState : checkStates) {
            if (checkState == null || !checkState.isCheckState2()) {
                continue;
            }
            INGWResource resource = connections.getResourceById(checkState.getId());
            if (resource instanceof CollectorResource) {
                count++;
            }
        }
        return count;
    }

    private CollectorResource findSelectedCollectorResource(
            Connections connections,
            List<CheckState> checkStates) {
        if (connections == null || checkStates == null) {
            return null;
        }
        for (CheckState checkState : checkStates) {
            if (checkState == null || !checkState.isCheckState2()) {
                continue;
            }
            INGWResource resource = connections.getResourceById(checkState.getId());
            if (resource instanceof CollectorResource) {
                return (CollectorResource) resource;
            }
        }
        return null;
    }

    private boolean prepareCollectorWorkspaceForImport(Context context, CollectorResource collector) {
        if (context == null || collector == null || collector.getConnection() == null) {
            return false;
        }
        if (!collector.isSnapshotComplete()) {
            HyperLog.e(Constants.TAG, "Collector import (dialog): incomplete project snapshot remoteId="
                    + collector.getRemoteId() + " error=" + collector.getSnapshotError());
            Toast.makeText(context, R.string.ngw_collector_incomplete_snapshot, Toast.LENGTH_LONG).show();
            return false;
        }
        if (collector.getLayers().isEmpty()) {
            Toast.makeText(context, R.string.ngw_collector_no_vector_layers, Toast.LENGTH_LONG).show();
            return false;
        }
        Connection connection = collector.getConnection();
        CollectorProjectMetadata metadata = CollectorProjectMetadata.create(
                connection.getName(),
                collector.getRemoteId(),
                collector.getName(),
                collector.getProjectDistrict());
        LayerGroup projectWorkspace = CollectorProjectRegistry.prepareCollectorProjectWorkspace(
                context,
                metadata);
        if (projectWorkspace == null) {
            HyperLog.e(Constants.TAG, "Collector import (dialog): failed to prepare isolated workspace remoteId="
                    + collector.getRemoteId() + " account=" + connection.getName());
            Toast.makeText(context, R.string.error, Toast.LENGTH_LONG).show();
            return false;
        }
        mGroupLayer = projectWorkspace;
        return true;
    }

}
