package com.nextgis.maplibui.util;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/** Shared accessible touch height and separation for ordinary and legacy form selectors. */
public final class FormFieldLayout {
    private FormFieldLayout() { }
    public static void prepareSelector(View view) {
        float density = view.getResources().getDisplayMetrics().density;
        view.setMinimumHeight(Math.round(56 * density));
        view.setPadding(Math.max(view.getPaddingLeft(), Math.round(12 * density)),
                Math.round(12 * density), view.getPaddingRight(), Math.round(12 * density));
    }
    public static void addSelector(ViewGroup parent, View view) {
        prepareSelector(view);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Math.round(12 * view.getResources().getDisplayMetrics().density);
        parent.addView(view, params);
    }
}
