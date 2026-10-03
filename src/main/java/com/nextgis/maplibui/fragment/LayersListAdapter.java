/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * Author:   NikitaFeodonit, nfeodonit@yandex.com
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2014-2019, 2021 NextGIS, info@nextgis.com
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

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.drawerlayout.widget.DrawerLayout;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.MapEventListener;
import com.nextgis.maplib.datasource.GeoEnvelope;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.map.Layer;
import com.nextgis.maplib.map.LocalTMSLayer;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.NGWLookupTable;
import com.nextgis.maplib.map.NGWRasterLayer;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.map.RemoteTMSLayer;
import com.nextgis.maplib.map.Table;
import com.nextgis.maplib.map.TrackLayer;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.util.AccountUtil;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.activity.NGActivity;
import com.nextgis.maplibui.api.ILayerUI;
import com.nextgis.maplibui.api.IVectorLayerUI;
import com.nextgis.maplibui.mapui.MapView;
import com.nextgis.maplibui.mapui.NGWRasterLayerUI;
import com.nextgis.maplibui.mapui.NGWWebMapLayerUI;
import com.nextgis.maplibui.mapui.RemoteTMSLayerUI;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import com.nextgis.maplibui.util.ControlHelper;
import com.nextgis.maplibui.util.LayerBackupManager;
import com.nextgis.maplibui.util.LayerUtil;
import com.nextgis.maplibui.util.UiUtil;

import static com.nextgis.maplib.util.Constants.NOT_FOUND;
import static com.nextgis.maplib.util.LayerUtil.normalizeLayerName;
import static com.nextgis.maplibui.util.ConstantsUI.CODE_SAVE_FILE;
import static com.nextgis.maplibui.util.ExportGeoJSONTask.ZIP_EXT;

import java.lang.ref.WeakReference;


/**
 * An adapter to show layers as list
 */
public class LayersListAdapter extends BaseAdapter implements MapEventListener {
    protected final MapView mMapView;
    protected final MapDrawable mMap;
    //protected final Context mContext;
    protected final WeakReference<NGActivity> mActivity;
    protected DrawerLayout mDrawer;
    protected onEdit mEditListener;
    protected View.OnClickListener mOnPencilClickListener;

    int nChoise =0;

    public interface onEdit {
        void onLayerEdit(ILayer layer);
    }

    public LayersListAdapter(NGActivity activity, MapView map) {
        mMapView = map;
        mMap = map.getMap();
        mActivity = new WeakReference<>(activity);

        if (null != mMap)
            mMap.addListener(this);
    }


    public void setOnLayerEditListener(onEdit listener) {
        mEditListener = listener;
    }


    public void setOnPencilClickListener(View.OnClickListener listener) {
        mOnPencilClickListener = listener;
    }


    @Override
    protected void finalize() throws Throwable {
        if (null != mMap) {
            mMap.removeListener(this);
        }
        super.finalize();
    }


    public void onResume() {
        notifyDataSetChanged();
    }


    @Override
    public int getCount() {
        if (null != mMap)
            return mMap.getLayerCount();
        return 0;
    }


    @Override
    public Object getItem(int i) {
        int nIndex = getCount() - 1 - i;
        if (null != mMap)
            return mMap.getLayer(nIndex);
        return null;
    }


    @Override
    public long getItemId(int i) {
        if (i < 0 || i >= mMap.getLayerCount())
            return NOT_FOUND;
        Table layer = (Table) getItem(i);
        if (null != layer)
            return layer.getId();
        return NOT_FOUND;
    }


    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        final Table layer = (Table) getItem(i);
        return getStandardLayerView(layer, view);
    }


    private View getStandardLayerView(final ILayer layer, View view) {
        LayoutInflater inflater = LayoutInflater.from(mActivity.get());
        View v = view;
        if (v == null || v.getId() == R.id.empty_row)
            v = inflater.inflate(R.layout.row_layer, null);

        if (layer instanceof NGWLookupTable)
            return inflater.inflate(R.layout.row_empty, null);

        final ILayerUI layerui;
        if (layer == null) {
            return v;
        } else if (layer instanceof ILayerUI) {
            layerui = (ILayerUI) layer;
        } else {
            layerui = null;
        }

        ImageView ivIcon = v.findViewById(R.id.ivIcon);
        ivIcon.setImageDrawable(layerui != null ? layerui.getIcon(mActivity.get()) : null);

        TextView tvPaneName = v.findViewById(R.id.tvLayerName);
        tvPaneName.setText(layer.getName());
        //final int id = layer.getId();

        final ImageButton btMore = v.findViewById(R.id.btMore);
        ImageButton btShow = v.findViewById(R.id.btShow);
        ImageView ivEdited = v.findViewById(R.id.ivEdited);

        boolean hide = layerui instanceof VectorLayer && ((VectorLayer) layerui).isLocked()
                || ((IGISApplication) mActivity.get().getApplication()).isLayerReservedForWalk(layer.getId());
        btMore.setVisibility(hide ? View.GONE : View.VISIBLE);
        btShow.setVisibility(hide ? View.GONE : View.VISIBLE);
        ivEdited.setVisibility(hide ? View.VISIBLE : View.GONE);
        if (mOnPencilClickListener != null)
            ivEdited.setOnClickListener(mOnPencilClickListener);

        int[] attrs = new int[] { R.attr.ic_action_visibility_on, R.attr.ic_action_visibility_off};
        TypedArray ta = mActivity.get().obtainStyledAttributes(attrs);
        Drawable visibilityOn = ta.getDrawable(0);
        Drawable visibilityOff = ta.getDrawable(1);

        if (layer instanceof Layer) {
            boolean visible = ((Layer) layer).isVisible();
            btShow.setImageDrawable(visible ? visibilityOn : visibilityOff);
            if (visible)
                btShow.setColorFilter(ContextCompat.getColor(mActivity.get(), R.color.layer_visibility_on));
            else
                btShow.clearColorFilter();
            //btShow.refreshDrawableState();
            btShow.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View arg0) {
                            //Layer layer = mMap.getLayerById(id);
                            ((Layer) layer).setVisible(!((Layer) layer).isVisible());
                            layer.save();
                        }
                    });
        }
        ta.recycle();

        btMore.setOnClickListener(new View.OnClickListener() {
                    public void onClick(final View arg0) {
                        PopupMenu popup = new PopupMenu(mActivity.get(), btMore);
                        UiUtil.setForceShowIcon(popup);
                        popup.getMenuInflater().inflate(R.menu.layer_popup, popup.getMenu());

                        if (layerui == null) {
                            popup.getMenu().findItem(R.id.menu_settings).setEnabled(false);
                            popup.getMenu().findItem(R.id.menu_share).setEnabled(false);
                            popup.getMenu().findItem(R.id.menu_save).setVisible(false);
                        }

                        if (layer instanceof VectorLayerUI) {
                            popup.getMenu().findItem(R.id.menu_send_to_ngw).setVisible(true);
                            if (!AccountUtil.isProUser(mActivity.get())) {
                                popup.getMenu().findItem(R.id.menu_send_to_ngw).setIcon(R.drawable.ic_lock_black_24dp);
                            }
                        }

                        if (layerui instanceof TrackLayer) {
                            popup.getMenu().findItem(R.id.menu_delete).setVisible(false);
                            popup.getMenu().findItem(R.id.menu_settings).setTitle(R.string.track_list);
                            popup.getMenu().findItem(R.id.menu_share).setVisible(false);
                            popup.getMenu().findItem(R.id.menu_save).setVisible(false);
                        } else if (layerui instanceof VectorLayer) {
                            popup.getMenu().findItem(R.id.menu_edit).setVisible(
                                    ((VectorLayer) layerui).isEditingAllowed());
                            popup.getMenu().findItem(R.id.menu_share).setVisible(true);
                            popup.getMenu().findItem(R.id.menu_save).setVisible(true);
                            popup.getMenu().findItem(R.id.menu_zoom_extent).setVisible(true);
                            popup.getMenu().findItem(R.id.menu_download_tiles).setVisible(true);
                            popup.getMenu().findItem(R.id.menu_download_tiles).setTitle(R.string.attributes);
                        } else if (layerui instanceof LocalTMSLayer) {
                            if (((LocalTMSLayer) layerui).isSharedUnderlay())
                                popup.getMenu().findItem(R.id.menu_delete).setTitle(R.string.underlay_unlink);
                            GeoEnvelope extents = ((LocalTMSLayer) layerui).getExtents();
                            popup.getMenu().findItem(R.id.menu_zoom_extent).setVisible(extents != null && extents.isInit());
                        } else if (layerui instanceof NGWRasterLayer) {
                            popup.getMenu().findItem(R.id.menu_zoom_extent).setVisible(true);
                            popup.getMenu().findItem(R.id.menu_download_tiles).setVisible(
                                    ((NGWRasterLayer)layerui).getIsOfflie() ? false : true);
                        } else if (layerui instanceof RemoteTMSLayer) {
                            if (((RemoteTMSLayer)layerui).getIsOfflie())
                                popup.getMenu().findItem(R.id.menu_zoom_extent).setVisible(true);

                            popup.getMenu().findItem(R.id.menu_download_tiles).setVisible(
                                    ((RemoteTMSLayer)layerui).getIsOfflie() ? false : true);
                        }

                        if (layerui instanceof NGWWebMapLayerUI) {
                            popup.getMenu().findItem(R.id.menu_zoom_extent).setVisible(false);
                            popup.getMenu().findItem(R.id.menu_edit).setVisible(true);
                            popup.getMenu().findItem(R.id.menu_edit).setTitle(R.string.customize_ngw_map_layers);
                        }

                        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                                    public boolean onMenuItemClick(MenuItem item) {
                                        final int i = item.getItemId();
                                        if (i == R.id.menu_settings) {
                                            assert layerui != null;
                                            layerui.changeProperties(mActivity.get());
                                        } else if (i == R.id.menu_share  || i == R.id.menu_save ) {
                                            assert layerui != null;

                                            if (layerui instanceof VectorLayer) {

                                                String one = arg0.getContext().getResources().getString(R.string.use_keys);
                                                String two = arg0.getContext().getResources().getString(R.string.use_names);

                                                final String[] items = {one, two};

                                                AlertDialog.Builder builder = new AlertDialog.Builder(arg0.getContext());
                                                builder.setTitle(R.string.export_options);
                                                updateChoise(0);
                                                builder.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                                                            public void onClick(DialogInterface dialog, int which) {
                                                                updateChoise(which);
                                                            }})
                                                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                                            public void onClick(DialogInterface dialog, int which) {

                                                                dialog.cancel();
                                                                if (i == R.id.menu_share) {
                                                                    VectorLayer vectorLayer = (VectorLayer) layerui;
                                                                    LayerUtil.shareLayerAsGeoJSON(mActivity.get(), vectorLayer, true,
                                                                            false, null, nChoise == 1);
                                                                } else if (i == R.id.menu_save){
                                                                    mActivity.get().storeLayerForSave((VectorLayer) layerui);
                                                                    String fileName = normalizeLayerName(((VectorLayer) layerui).getName()) + ZIP_EXT;
                                                                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                                                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                                                                    intent.setType("*/*");
                                                                    intent.putExtra(Intent.EXTRA_TITLE, fileName);
                                                                    mActivity.get().updateChoise(nChoise);
                                                                    mActivity.get().startActivityForResult(intent, CODE_SAVE_FILE);
                                                                }
                                                            }})
                                                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(DialogInterface dialogInterface, int i) {
                                                                dialogInterface.cancel();
                                                            }
                                                        })
                                                        .create()
                                                        .show();
                                            }
                                        }
//                                        else if (i == R.id.menu_save) {
//                                             //
//
//                                            assert layerui != null; 222
//
//                                            if (layerui instanceof VectorLayer) {
//                                                mActivity.get().storeLayerForSave((VectorLayer) layerui);
//                                                String fileName = normalizeLayerName(((VectorLayer) layerui).getName()) + ZIP_EXT;
//                                                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
//                                                intent.addCategory(Intent.CATEGORY_OPENABLE);
//                                                intent.setType("*/*");
//                                                intent.putExtra(Intent.EXTRA_TITLE, fileName);
//                                                mActivity.get().startActivityForResult(intent, CODE_SAVE_FILE);
//                                            }
//                                        }
                                        else if (i == R.id.menu_edit) {
                                            if (layerui instanceof NGWWebMapLayerUI) {
                                                final MapDrawable finalMapdrawable = mMap;
                                                final Runnable updateRunnable = new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        finalMapdrawable.deleteLayerByID(((NGWWebMapLayerUI) layerui).getId());

                                                        finalMapdrawable.recreateNGWWebMapSourceById(((NGWWebMapLayerUI) layerui).getPath().toString(),
                                                                ((NGWWebMapLayerUI) layerui).getId());
                                                        finalMapdrawable.addLayerByID(( ((NGWWebMapLayerUI) layerui).getId()));
                                                    }
                                                };
                                                ((NGWWebMapLayerUI) layerui).showLayersDialog(mMapView, mActivity.get(), updateRunnable);

                                            }
                                            else if (mEditListener != null)
                                                mEditListener.onLayerEdit(layer);
                                        } else if (i == R.id.menu_delete) {
                                            return deleteLayer(layer);
                                        } else if (i == R.id.menu_zoom_extent) {
                                            mMap.zoomToExtent(layer.getExtents());
                                        } else if (i == R.id.menu_download_tiles) {

                                            GeoEnvelope env = mMap.getCurrentBounds();



                                            if (layer instanceof RemoteTMSLayerUI) {
                                                RemoteTMSLayerUI remoteTMSLayer = (RemoteTMSLayerUI) layer;
                                                remoteTMSLayer.downloadTiles(mActivity.get(), env);
                                            } else if (layer instanceof NGWRasterLayerUI) {
                                                NGWRasterLayerUI remoteTMSLayer = (NGWRasterLayerUI) layer;
                                                remoteTMSLayer.downloadTiles(mActivity.get(), env);
                                            } else if (layer instanceof NGWWebMapLayerUI) {
                                                NGWWebMapLayerUI remoteTMSLayer = (NGWWebMapLayerUI) layer;
                                                remoteTMSLayer.downloadTiles(mActivity.get(), env);
                                            } else if (layer instanceof IVectorLayerUI) {
                                                IVectorLayerUI vectorLayerUI = (IVectorLayerUI) layer;
                                                vectorLayerUI.showAttributes();
                                            }
                                        } else if (i == R.id.menu_send_to_ngw) {
                                            if (!AccountUtil.isUserExists(mActivity.get())) {
                                                ControlHelper.showNoLoginDialog(mActivity.get());
                                            } else if (layer instanceof VectorLayerUI)
                                                ((VectorLayerUI) layer).sendToNGW(mActivity.get());
                                        }

                                        if (mDrawer != null)
                                            mDrawer.closeDrawers();

                                        return true;
                                    }
                                });

                        popup.show();
                    }
                });

        return v;
    }

    private boolean deleteLayer(final ILayer layer) {
        boolean shared = layer instanceof LocalTMSLayer && ((LocalTMSLayer) layer).isSharedUnderlay();
        new AlertDialog.Builder(mActivity.get()).setTitle(R.string.are_you_sure)
                .setMessage(shared ? R.string.underlay_unlink_confirm : R.string.delete_confirm)
               .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialogInterface, int i) {
                       android.app.Activity activity = mActivity.get();
                       if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
                       if (((IGISApplication) activity.getApplication()).isLayerReservedForWalk(layer.getId())) {
                           android.widget.Toast.makeText(mActivity.get(), R.string.walk_layer_busy,
                                   android.widget.Toast.LENGTH_LONG).show();
                           return;
                       }
                       if (layer instanceof NGWVectorLayer) {
                           IGISApplication app = (IGISApplication) activity.getApplication();
                           final com.nextgis.maplib.map.MapBase owner = app.getMap();
                           final long generation = ((NGWVectorLayer) layer).getDataGeneration();
                           android.app.ProgressDialog progress = android.app.ProgressDialog.show(
                                   activity, null, activity.getString(R.string.form_save_processing), true, false);
                           new Thread(() -> {
                               boolean backedUp = app.backupEditableLayerData((NGWVectorLayer) layer,
                                       LayerBackupManager.REASON_MANUAL_LAYER_DELETE);
                               activity.runOnUiThread(() -> {
                                   if (activity.isFinishing() || activity.isDestroyed()) return;
                                   if (progress.isShowing()) progress.dismiss();
                                   if (!backedUp) return;
                                   if (app.getMap() != owner || layer.getParent() == null
                                           || ((NGWVectorLayer) layer).getDataGeneration() != generation
                                           || app.isLayerReservedForWalk(layer.getId())) {
                                       android.widget.Toast.makeText(activity, R.string.layer_delete_retry,
                                               android.widget.Toast.LENGTH_LONG).show();
                                       return;
                                   }
                                   removeBackedUpLayer(layer, generation);
                               });
                           }, "layer-delete-backup").start();
                       } else removeBackedUpLayer(layer, -1);
                   }
               })
               .setNegativeButton(R.string.cancel, null).show();
        return true;
    }

    private void removeBackedUpLayer(final ILayer layer, final long generation) {
        android.app.Activity activity = mActivity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        IGISApplication app = (IGISApplication) activity.getApplication();
        final ILayer parent = layer.getParent();
        if (parent == null || app.getMap() != mMap) return;
        // Keep the layer attached during Undo. Sync/form writes still resolve its owning database,
        // and the final gate can compare against the exact snapshot that was backed up.
        View root = activity.getWindow().getDecorView().getRootView();
        if (root == null) return;
        Snackbar snackbar = Snackbar.make(root, R.string.layer_delete_pending, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, view -> { })
                .addCallback(new Snackbar.Callback() {
                    @Override public void onDismissed(Snackbar bar, int event) {
                        if (event == DISMISS_EVENT_ACTION || event == DISMISS_EVENT_MANUAL) return;
                        if (app.getMap() != mMap || layer.getParent() != parent
                                || app.isLayerReservedForWalk(layer.getId())
                                || layer instanceof NGWVectorLayer
                                && ((NGWVectorLayer) layer).getDataGeneration() != generation) {
                            android.app.Activity current = mActivity.get();
                            if (current != null && !current.isFinishing() && !current.isDestroyed())
                                android.widget.Toast.makeText(current, R.string.layer_delete_retry,
                                        android.widget.Toast.LENGTH_LONG).show();
                            return;
                        }
                        try {
                            if (!layer.delete(true)) return;
                            mMap.save();
                            notifyDataChanged(false, false);
                        } catch (RuntimeException error) {
                            android.util.Log.e(com.nextgis.maplib.util.Constants.TAG,
                                    "Layer removal failed after backup", error);
                            android.app.Activity current = mActivity.get();
                            if (current != null && !current.isFinishing() && !current.isDestroyed())
                                android.widget.Toast.makeText(current, R.string.layer_delete_retry,
                                        android.widget.Toast.LENGTH_LONG).show();
                        }
                    }
                });
        TextView text = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        text.setTextColor(ContextCompat.getColor(activity, R.color.color_white));
        snackbar.show();
    }

    @Override
    public void onLayerAdded(int id) {
        notifyDataChanged(false, false);
    }


    @Override
    public void onLayerDeleted(int id) {
        notifyDataChanged(false, false);
    }


    @Override
    public void onLayerChanged(int id) {
        notifyDataChanged(false, false);
    }

    @Override
    public void onLayerVisibleChanged(int id) {
        notifyDataChanged(false, true);
    }

    @Override
    public void onLayerChangedFeatureId(long oldFeatureId, long newFeatureId, int layerId) {
    }


    @Override
    public void onExtentChanged(float zoom, GeoPoint center) {

    }


    @Override
    public void onLayersReordered() {
        notifyDataChanged(false, false);
    }


    @Override
    public void onLayerDrawFinished(int id, float percent) {

    }


    @Override
    public void onLayerDrawStarted() {

    }


    void notifyDataChanged(boolean reloadAllLayers, boolean skipMaplibre) {
        mActivity.get().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
                if (reloadAllLayers && !skipMaplibre)
                    mMap.mapContext.get().loadLayersLite();
            }
        });
    }


    public void setDrawer(DrawerLayout drawer) {
        mDrawer = drawer;
    }


    public void swapElements(int originalPosition, int newPosition) {
        Log.d("SSWAPP",
              "Original position: " + originalPosition + " Destination position: " + newPosition);
        if (null == mMap) {
            return;
        }

        final ILayer iLayerFrom = (ILayer)getItem(originalPosition);
        final ILayer iLayerTo = (ILayer)getItem(newPosition);

        Log.d("SSWAPP",
                "Original position: " + iLayerFrom.getName() + " Destination position: " + iLayerTo.getName());



        int newPositionFixed = getCount() - 1 - newPosition;
        mMap.moveLayer(newPositionFixed, (com.nextgis.maplib.api.ILayer) getItem(originalPosition));

//        mMap.updatelayerOrder(iLayerFrom, iLayerTo);
        notifyDataSetChanged();
    }


    public void endDrag() {
        if (null == mMap)
            return;
        mMap.save();

        mMap.thaw();
        mMap.runDraw(null);
    }


    void beginDrag() {
        if (null == mMap)
            return;
        mMap.freeze();
    }

    public void updateChoise(int choise){
        nChoise =choise;
    }

}
