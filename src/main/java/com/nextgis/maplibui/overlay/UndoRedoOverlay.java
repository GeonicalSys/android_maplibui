/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2016-2017, 2019 NextGIS, info@nextgis.com
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

package com.nextgis.maplibui.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import android.view.MenuItem;

import com.nextgis.maplib.datasource.Feature;
import com.nextgis.maplib.datasource.GeoGeometry;
import com.nextgis.maplib.datasource.GeoGeometryFactory;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.api.Overlay;
import com.nextgis.maplibui.mapui.MapViewOverlays;
import com.nextgis.maplibui.util.ControlHelper;

import java.io.IOException;
import java.util.LinkedList;

public class UndoRedoOverlay extends Overlay {
    private static final String BUNDLE_KEY_HISTORY = "history_";
    private static final String BUNDLE_KEY_HISTORY_SIZE = "history_size";
    private static final String BUNDLE_KEY_HISTORY_STATE = "history_state";

    static final int MAX_UNDO = 100;

    private Toolbar mTopToolbar;
    private int mHistoryState = -1;
    private LinkedList<GeoGeometry> mHistory;
    private Feature mFeature;

    public UndoRedoOverlay(Context context, MapViewOverlays mapViewOverlays) {
        super(context, mapViewOverlays);
        mHistory = new LinkedList<>();
        mFeature = new Feature();
    }

    @Override
    public void draw(Canvas canvas, MapDrawable mapDrawable) {

    }

    @Override
    public void drawOnPanning(Canvas canvas, PointF currentMouseOffset) {

    }

    @Override
    public void drawOnZooming(Canvas canvas, PointF currentFocusLocation, float scale) {

    }

    @Override
    public Bundle onSaveState() {
        Bundle bundle = super.onSaveState();

        bundle.putInt(BUNDLE_KEY_HISTORY_STATE, mHistoryState);
        bundle.putInt(BUNDLE_KEY_HISTORY_SIZE, mHistory.size());
        for (int i = 0; i < mHistory.size(); i++)
            try {
                bundle.putByteArray(BUNDLE_KEY_HISTORY + i, mHistory.get(i).toBlob());
            } catch (IOException e) {
                e.printStackTrace();
            }

        return bundle;
    }

    @Override
    public void onRestoreState(Bundle bundle) {
        mHistory.clear();
        int restoredState = bundle.getInt(BUNDLE_KEY_HISTORY_STATE, -1);
        for (int i = 0; i < bundle.getInt(BUNDLE_KEY_HISTORY_SIZE, 0); i++)
            try {
                byte[] blob = bundle.getByteArray(BUNDLE_KEY_HISTORY + i);
                if (blob != null) {
                    mHistory.add(GeoGeometryFactory.fromBlob(blob));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        mHistoryState = mHistory.isEmpty()
                ? -1
                : Math.max(0, Math.min(restoredState, mHistory.size() - 1));
        if (mHistoryState >= 0) {
            mFeature.setGeometry(mHistory.get(mHistoryState).copy());
        } else {
            mFeature.setGeometry(null);
        }

        super.onRestoreState(bundle);
    }


    public boolean onOptionsItemSelected(int id) {
        if (id == R.id.menu_edit_undo) {
            return restoreFromHistory(mHistoryState - 1);
        } else if (id == R.id.menu_edit_redo) {
            return restoreFromHistory(mHistoryState + 1);
        }

        return false;
    }

    public void setTopToolbar(final Toolbar toolbar) {
        mTopToolbar = toolbar;
    }

    public void saveToHistory(Feature feature) {
        if (null == feature || null == feature.getGeometry())
            return;

        GeoGeometry geometry = feature.getGeometry();
        if (mHistoryState >= 0
                && mHistoryState < mHistory.size()
                && hasSameCoordinates(mHistory.get(mHistoryState), geometry)) {
            GeoGeometry snapshot = geometry.copy();
            mHistory.set(mHistoryState, snapshot);
            mFeature.setGeometry(snapshot.copy());
            defineUndoRedo();
            return;
        }

        for (int i = mHistory.size() - 1; i > mHistoryState; i--)
            mHistory.remove(i);

        if (mHistory.size() >= MAX_UNDO + 1) {
            mHistory.removeFirst();
            mHistoryState--;
        }

        mHistory.add(geometry.copy());
        mHistoryState = mHistory.size() - 1;
        mFeature.setGeometry(mHistory.getLast().copy());
        defineUndoRedo();
    }


    private static boolean hasSameCoordinates(GeoGeometry first, GeoGeometry second) {
        return first.toWKT(true).equals(second.toWKT(true));
    }


    private boolean restoreFromHistory(int id) {
        if (id < 0 || id >= mHistory.size())
            return false;

        mHistoryState = id;
        mFeature.setGeometry(mHistory.get(id).copy());
        defineUndoRedo();

        return true;
    }


    public void clearHistory() {
        mHistory.clear();
        mHistoryState = -1;
        mFeature.setGeometry(null);
    }

    public boolean hasHistory() {
        return !mHistory.isEmpty();
    }


    public void defineUndoRedo() {
        if (mTopToolbar == null)
            return;

        MenuItem item = mTopToolbar.getMenu().findItem(R.id.menu_edit_undo);
        if (item != null)
            ControlHelper.setEnabled(item, 0 < mHistoryState);

        item = mTopToolbar.getMenu().findItem(R.id.menu_edit_redo);
        if (item != null)
            ControlHelper.setEnabled(item, mHistoryState + 1 < mHistory.size());
    }

    public Feature getFeature() {
        return mFeature;
    }
}
