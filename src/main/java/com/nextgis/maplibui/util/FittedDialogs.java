package com.nextgis.maplibui.util;

import android.app.Dialog;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import com.nextgis.maplibui.view.FittedPanelLayout;

/** Shared builders keep non-list dialog content and its actions inside an enlarged viewport. */
public final class FittedDialogs {
    private FittedDialogs() { }

    public static android.app.AlertDialog.Builder nativeBuilder(Context context) {
        return nativeBuilder(context, 0);
    }

    public static android.app.AlertDialog.Builder nativeBuilder(Context context, int theme) {
        return new android.app.AlertDialog.Builder(context, theme) {
            @Override public android.app.AlertDialog create() {
                android.app.AlertDialog dialog = super.create();
                dialog.create();
                if (dialog.getListView() == null) fit(dialog);
                return dialog;
            }
        };
    }

    public static androidx.appcompat.app.AlertDialog.Builder compatBuilder(Context context) {
        return compatBuilder(context, 0);
    }

    public static androidx.appcompat.app.AlertDialog.Builder compatBuilder(Context context, int theme) {
        return new androidx.appcompat.app.AlertDialog.Builder(context, theme) {
            @Override public androidx.appcompat.app.AlertDialog create() {
                androidx.appcompat.app.AlertDialog dialog = super.create();
                dialog.create();
                if (dialog.getListView() == null) fit(dialog);
                return dialog;
            }
        };
    }

    private static void fit(Dialog dialog) {
        Context context = dialog.getContext();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        if (context.getResources().getConfiguration().fontScale <= 1.05f
                && metrics.densityDpi <= DisplayMetrics.DENSITY_DEVICE_STABLE) return;
        ViewGroup content = dialog.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() != 1 || content.getChildAt(0) instanceof FittedPanelLayout) return;
        View body = content.getChildAt(0);
        // A list supplied through setView() is not exposed by AlertDialog.getListView().
        // Measuring its entire dialog with UNSPECIFIED height would measure just the first
        // adapter row. Preserve the controller's bounded, scrollable viewport instead.
        if (containsScrollingAdapter(body)) return;
        wrapLabels(body);
        ViewGroup.LayoutParams preferred = body.getLayoutParams();
        content.removeView(body);
        if (body instanceof ViewGroup && body.getClass().getSimpleName().equals("AlertDialogLayout")) {
            // Its special measure pass either truncates stacked buttons or expands the middle panel.
            // Keep the controller's existing panels/buttons and measure their intrinsic vertical size.
            ViewGroup original = (ViewGroup) body;
            android.widget.LinearLayout intrinsic = new android.widget.LinearLayout(context);
            intrinsic.setId(body.getId()); intrinsic.setOrientation(android.widget.LinearLayout.VERTICAL);
            intrinsic.setPadding(body.getPaddingLeft(), body.getPaddingTop(), body.getPaddingRight(), body.getPaddingBottom());
            while (original.getChildCount() > 0) {
                View panel = original.getChildAt(0);
                ViewGroup.MarginLayoutParams params = panel.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
                        ? (ViewGroup.MarginLayoutParams) panel.getLayoutParams()
                        : new ViewGroup.MarginLayoutParams(panel.getLayoutParams());
                original.removeView(panel);
                android.widget.LinearLayout.LayoutParams natural = new android.widget.LinearLayout.LayoutParams(params);
                natural.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                intrinsic.addView(panel, natural);
            }
            body = intrinsic;
        }
        FittedPanelLayout fitted = new FittedPanelLayout(context, null);
        fitted.addView(body, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(fitted, preferred);
        // Use the available width instead of spending a narrow enlarged viewport on decorative insets.
        android.content.res.TypedArray surface = context.obtainStyledAttributes(new int[]{android.R.attr.colorBackground});
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(surface.getColor(0, android.graphics.Color.WHITE)));
        surface.recycle();
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static boolean containsScrollingAdapter(View view) {
        if (view instanceof android.widget.AbsListView
                || view instanceof androidx.recyclerview.widget.RecyclerView) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i=0; i<group.getChildCount(); i++)
                if (containsScrollingAdapter(group.getChildAt(i))) return true;
        }
        return false;
    }

    private static void wrapLabels(View view) {
        if (view instanceof TextView && !(view instanceof EditText)) {
            TextView text = (TextView) view;
            text.setSingleLine(false); text.setMaxLines(Integer.MAX_VALUE); text.setEllipsize(null);
            if (text.getLayoutParams() != null) text.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        if (view instanceof ViewGroup) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params != null && params.height > 0) {
                view.setMinimumHeight(Math.max(view.getMinimumHeight(), params.height));
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) wrapLabels(((ViewGroup) view).getChildAt(i));
        }
    }
}
