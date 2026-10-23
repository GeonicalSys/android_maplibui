/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * Author:   NikitaFeodonit, nfeodonit@yandex.com
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2012-2016 NextGIS, info@nextgis.com
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

import android.app.Activity;
import android.content.Context;
import androidx.annotation.MenuRes;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.widget.Toolbar;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.nextgis.maplibui.util.ControlHelper;


/**
 * Bottom toolbar
 */
public class BottomToolbar
        extends Toolbar
{

    protected boolean mIsMenuInitialized;
    private boolean textFitAttached;
    private boolean fitEditingActions;

    /** Keep every editing action on the panel, shrinking only the overflowing icon targets. */
    public void fitAllActions() {
        fitEditingActions = true;
        setContentInsetsRelative(0, 0);
        for (int i = 0; i < getMenu().size(); i++) {
            MenuItem item = getMenu().getItem(i);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            if (!(item.getActionView() instanceof androidx.appcompat.widget.AppCompatImageButton)) {
                androidx.appcompat.widget.AppCompatImageButton button = new androidx.appcompat.widget.AppCompatImageButton(
                        getContext(),null,androidx.appcompat.R.attr.actionButtonStyle);
                button.setId(item.getItemId()); button.setMinimumWidth(0); button.setMinimumHeight(0);
                button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                button.setPadding(dp(4),0,dp(4),0);
                button.setOnClickListener(view -> { if(item.isEnabled()) getMenu().performIdentifierAction(item.getItemId(),0); });
                item.setActionView(button);
            }
        }
        if (!textFitAttached) {
            com.nextgis.maplibui.util.ToolbarTextFit.attach(this);
            textFitAttached = true;
        }
    }

    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (fitEditingActions) {
            int count = 0;
            for (int i=0; i<getMenu().size(); i++) if (getMenu().getItem(i).isVisible()) count++;
            int available = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight()
                    - Math.max(getCurrentContentInsetStart(),getNavigationIcon() == null ? 0 : dp(56))
                    - getCurrentContentInsetEnd();
            int cell = Math.min(dp(56), Math.max(1, available / Math.max(1,count)));
            for (int i=0; i<getMenu().size(); i++) {
                final MenuItem item = getMenu().getItem(i);
                if (!item.isVisible()) continue;
                android.view.View action = item.getActionView();
                if (!(action instanceof androidx.appcompat.widget.AppCompatImageButton)) continue;
                androidx.appcompat.widget.AppCompatImageButton button = (androidx.appcompat.widget.AppCompatImageButton) action;
                button.setImageDrawable(item.getIcon()); button.setEnabled(item.isEnabled());
                button.setContentDescription(item.getTitle());
                androidx.appcompat.widget.TooltipCompat.setTooltipText(button,item.getTitle());
                android.view.ViewGroup.LayoutParams params = button.getLayoutParams();
                if (params == null) {
                    params = new android.view.ViewGroup.LayoutParams(cell,android.view.ViewGroup.LayoutParams.MATCH_PARENT);
                    button.setLayoutParams(params);
                }
                if (params.width != cell || params.height != android.view.ViewGroup.LayoutParams.MATCH_PARENT) {
                    params.width = cell; params.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                    button.setLayoutParams(params);
                }
            }
        }
        super.onMeasure(widthSpec,heightSpec);
    }


    public BottomToolbar(Context context)
    {
        super(context);
        mIsMenuInitialized = false;
    }


    public BottomToolbar(
            Context context,
            AttributeSet attrs)
    {
        super(context, attrs);
        mIsMenuInitialized = false;
    }


    public BottomToolbar(
            Context context,
            AttributeSet attrs,
            int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        mIsMenuInitialized = false;
    }

    @Override
    public void inflateMenu(@MenuRes int resId) {
        super.inflateMenu(resId);
        final Menu menu = getMenu();
        if (menu.size() == 0) {
            return;
        }
        MenuItem item = menu.getItem(0);
        if (item.getIcon() == null) {
            return;
        }
        int size = item.getIcon().getIntrinsicWidth() + ControlHelper.dpToPx(30, getResources());
        int width = getWidth();
        if (width == 0) {
            width = getContext().getResources().getDisplayMetrics().widthPixels;
        }

        // Lean bars (select_action_view: info+edit; attributes_editable: form+prev+next):
        // force ALWAYS so width math does not drop an icon on narrow screens.
        if (menu.size() <= 3) {
            for (int i = 0; i < menu.size(); i++) {
                MenuItemCompat.setShowAsAction(menu.getItem(i), MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
            }
            return;
        }

        for (int i = 0; i < menu.size(); i++) {
            item = menu.getItem(i);
            if (size * (i + 2) < width) {
                MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
            } else {
                break;
            }
        }
    }
}
