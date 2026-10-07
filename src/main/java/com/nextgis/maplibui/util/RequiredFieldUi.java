package com.nextgis.maplibui.util;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.util.LayerUtil;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.api.IControl;
import com.nextgis.maplibui.formcontrol.DoubleCombobox;
import com.nextgis.maplibui.formcontrol.Tabs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Required markers and navigation also work for controls on inactive NGFP pages. */
public final class RequiredFieldUi {
    private RequiredFieldUi() { }

    public static View fieldView(Field field, Map<String, IControl> controls) {
        for (Map.Entry<String, IControl> entry : controls.entrySet()) {
            IControl control = entry.getValue();
            if (control instanceof DoubleCombobox) {
                View view = ((DoubleCombobox) control).getFieldView(field.getName());
                if (view != null) return view;
            } else if (sameName(entry.getKey(), field.getName()) && control instanceof View) {
                return (View) control;
            }
        }
        return null;
    }

    public static boolean sameName(String left, String right) {
        return left != null && right != null && LayerUtil.normalizeFieldName(
                LayerUtil.unwrapQuotation(left)).equals(LayerUtil.normalizeFieldName(
                LayerUtil.unwrapQuotation(right)));
    }

    public static Map<String, String> decorate(List<Field> fields, Map<String, IControl> controls) {
        Map<String, String> captions = new HashMap<>();
        for (Field field : fields) {
            View view = fieldView(field, controls);
            if (view == null || !(view.getParent() instanceof ViewGroup)) continue;
            ViewGroup parent = (ViewGroup) view.getParent();
            int index = parent.indexOfChild(view);
            View previous = index > 0 ? parent.getChildAt(index - 1) : null;
            TextView label;
            if (previous instanceof com.nextgis.maplibui.control.TextLabel
                    || previous instanceof com.nextgis.maplibui.formcontrol.TextLabel
                    || previous instanceof TextView && previous.getTag(R.id.form_required_caption) != null) {
                label = (TextView) previous;
            } else {
                if (!field.isRequired()) continue;
                label = new androidx.appcompat.widget.AppCompatTextView(view.getContext());
                label.setText(field.getAlias());
                parent.addView(label, index);
            }
            Object savedCaption=label.getTag(R.id.form_required_caption);
            if (!field.isRequired() && savedCaption==null) continue;
            String caption = savedCaption==null?label.getText().toString():savedCaption.toString();
            if (savedCaption==null) {
                label.setTag(R.id.form_required_caption,caption);
                view.setTag(R.id.form_required_original_description,view.getContentDescription());
            }
            captions.put(field.getName(), caption);
            label.setText(caption+(field.isRequired()?" *":""));
            view.setContentDescription(field.isRequired()?view.getContext().getString(
                    R.string.form_required_field_hint, caption):(CharSequence)view.getTag(R.id.form_required_original_description));
        }
        return captions;
    }

    public static boolean containsView(View root, View target) {
        if (root == target) return true;
        if (root instanceof Tabs) {
            for (View page : ((Tabs) root).getPageLayouts())
                if (containsView(page, target)) return true;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++)
                if (containsView(group.getChildAt(i), target)) return true;
        }
        return false;
    }

    public static boolean reveal(View root, View target, Runnable onVisible) {
        if (root == target) {
            onVisible.run();
            return true;
        }
        if (root instanceof Tabs) return ((Tabs) root).revealView(target, onVisible);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++)
                if (reveal(group.getChildAt(i), target, onVisible)) return true;
        }
        return false;
    }

    public static void focus(View view) {
        view.requestFocus();
        view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), false);
        view.announceForAccessibility(view.getContentDescription());
    }
}
