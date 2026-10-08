package com.nextgis.maplibui.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.api.IControl;
import com.nextgis.maplibui.formcontrol.DoubleCombobox;
import com.nextgis.maplibui.formcontrol.DoubleComboboxValue;
import com.nextgis.maplibui.formcontrol.Space;
import com.nextgis.maplibui.formcontrol.Tabs;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** One caption/value hierarchy for standard and NGFP forms, including inactive pages. */
public final class FormFieldLayout {
    private FormFieldLayout() { }

    public static int dp(View view, int size) { return Math.round(size * view.getResources().getDisplayMetrics().density); }

    public static void prepareSelector(View view) {
        view.setMinimumHeight(dp(view, 56));
        // The collapsed item has its own inset. Reserve room for the dropdown arrow.
        view.setPadding(dp(view, 14), dp(view, 8), dp(view, 14), dp(view, 8));
        styleInput(view);
    }
    public static void addSelector(ViewGroup parent, View view) {
        prepareSelector(view);
        parent.addView(view, matchWidth());
    }

    /** Existing adjacent text labels remain the caption; standalone labels stay section headings. */
    public static void addControl(LinearLayout parent, IControl control, JSONObject element,
                                  List<Field> fields, Function<String, String> captions) throws JSONException {
        View view = (View) control;
        if (isLabel(view) || view instanceof Space || view instanceof Tabs) {
            control.addToLayout(parent);
            if (view instanceof TextView) styleHeading((TextView) view);
            tagElement(view, element);
            return;
        }
        if (control instanceof DoubleCombobox) {
            // Keep legacy values/listeners; only give each selector its own caption and outline.
            LinearLayout pair = new LinearLayout(parent.getContext());
            pair.setOrientation(LinearLayout.VERTICAL);
            control.addToLayout(pair);
            DoubleComboboxValue names = (DoubleComboboxValue) control.getValue();
            for (String fieldName : new String[]{names.mFieldName, names.mSubFieldName}) {
                View selector = ((DoubleCombobox) control).getFieldView(fieldName);
                if (selector == null) continue;
                pair.removeView(selector);
                FieldContainer container = new FieldContainer(parent.getContext(), captions.apply(fieldName), selector,
                        () -> {
                            DoubleComboboxValue value = (DoubleComboboxValue) control.getValue();
                            return RequiredFieldUi.sameName(fieldName, value.mFieldName) ? value.mValue : value.mSubValue;
                        }, null);
                container.body.addView(selector, matchWidth());
                pair.addView(container, fieldParams(container));
            }
            tagElement(pair, element);
            parent.addView(pair, matchWidth());
            return;
        }

        String field = control.getFieldName();
        boolean bound = false;
        for (Field candidate : fields) if (RequiredFieldUi.sameName(field, candidate.getName())) { bound = true; break; }
        TextView label = null;
        // A label above a checkbox describes the section; the checkbox already owns its question.
        if (!(view instanceof CheckBox) && parent.getChildCount() > 0) {
            View previous = parent.getChildAt(parent.getChildCount() - 1);
            if (isLabel(previous) && previous.getTag(R.id.form_element_id) == null) {
                label = (TextView) previous;
                parent.removeView(previous);
            }
        }
        String caption = label != null ? label.getText().toString() : bound ? captions.apply(field) : "";
        FieldContainer container = new FieldContainer(parent.getContext(), caption, view, control::getValue, label);
        control.addToLayout(container.body);
        for (int i = container.body.getChildCount() - 1; i >= 0; i--)
            if (container.body.getChildAt(i).getClass() == View.class) container.body.removeViewAt(i);
        if (container.body.getChildCount() == 0) container.setVisibility(View.GONE);
        styleControl(view);
        tagElement(container, element);
        parent.addView(container, fieldParams(container));
    }

    private static LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private static LinearLayout.LayoutParams fieldParams(View view) {
        LinearLayout.LayoutParams params = matchWidth();
        params.bottomMargin = dp(view, 20);
        return params;
    }
    private static boolean isLabel(View view) {
        return view instanceof com.nextgis.maplibui.formcontrol.TextLabel
                || view instanceof com.nextgis.maplibui.control.TextLabel;
    }
    public static void tagElement(View view, JSONObject element) throws JSONException {
        if (element == null || !element.has("lisa_id")) return;
        Object value = element.get("lisa_id");
        if (!(value instanceof String) || !com.nextgis.maplib.forms.ConditionalRequiredRules.validElementId((String) value))
            throw new JSONException("Invalid form element lisa_id");
        view.setTag(R.id.form_element_id, value);
    }
    private static void styleHeading(TextView label) {
        Palette palette = new Palette(label.getContext());
        label.setTextSize(16);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setTextColor(palette.value);
        label.setPadding(0, dp(label, 12), 0, dp(label, 12));
        ViewCompat.setAccessibilityHeading(label, true);
    }
    private static void styleControl(View view) {
        if (view instanceof Spinner) { prepareSelector(view); return; }
        if (view instanceof CheckBox) {
            CheckBox check = (CheckBox) view;
            check.setTextSize(17);
            check.setTextColor(new Palette(view.getContext()).value);
            check.setMinHeight(dp(view, 56));
            check.setGravity(Gravity.CENTER_VERTICAL);
            check.setPadding(dp(view, 4), dp(view, 10), dp(view, 8), dp(view, 10));
            Palette palette = new Palette(view.getContext());
            androidx.core.widget.CompoundButtonCompat.setButtonTintList(check, new ColorStateList(
                    new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{palette.hint, palette.accent, palette.border}));
            return;
        }
        if (view instanceof TextView) styleInput(view);
    }
    public static void styleChoice(TextView view, boolean placeholder) {
        Palette palette = new Palette(view.getContext());
        view.setTextSize(17);
        view.setTextColor(placeholder ? palette.hint : palette.value);
    }
    public static void styleSaveButton(android.widget.Button button) {
        Palette palette = new Palette(button.getContext());
        ViewCompat.setBackgroundTintList(button, null);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{-android.R.attr.state_enabled}, outline(button, palette.disabled, palette.border, 1));
        background.addState(new int[]{android.R.attr.state_pressed}, outline(button, ColorUtils.blendARGB(palette.accent, palette.value, 0.15f), palette.accent, 0));
        background.addState(new int[]{}, outline(button, palette.accent, palette.accent, 0));
        button.setBackground(background);
        button.setStateListAnimator(null);
        ViewCompat.setElevation(button, 0);
        button.setAllCaps(false);
        button.setTextSize(17);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        int text = ColorUtils.calculateLuminance(palette.accent) < 0.4 ? Color.WHITE : Color.rgb(19, 42, 29);
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{palette.hint, text}));
    }
    private static void styleInput(View view) {
        Palette palette = new Palette(view.getContext());
        view.setMinimumHeight(dp(view, 56));
        ViewCompat.setBackgroundTintList(view, null);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{-android.R.attr.state_enabled}, outline(view, palette.disabled, palette.border, 1));
        background.addState(new int[]{android.R.attr.state_focused}, outline(view, palette.surface, palette.accent, 2));
        background.addState(new int[]{android.R.attr.state_pressed}, outline(view, palette.disabled, palette.accent, 1));
        background.addState(new int[]{}, outline(view, palette.surface, palette.border, 1));
        view.setBackground(background);
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextSize(17);
            text.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                    new int[]{palette.hint, palette.value}));
            text.setHintTextColor(palette.hint);
            text.setPadding(dp(view, 14), dp(view, 14), dp(view, 14), dp(view, 14));
            if (text instanceof EditText && android.text.TextUtils.isEmpty(text.getHint())) text.setHint(R.string.form_enter_value);
            text.setGravity(text instanceof EditText && text.getMaxLines() > 1 ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL | Gravity.START);
        }
    }
    private static GradientDrawable outline(View view, int fill, int border, int width) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(view, 10));
        shape.setStroke(dp(view, width), border);
        return shape;
    }

    public static FieldContainer container(View view) {
        return view != null && view.getTag(R.id.form_field_container) instanceof FieldContainer
                ? (FieldContainer) view.getTag(R.id.form_field_container) : null;
    }
    public static View visibilityUnit(View view) { FieldContainer container = container(view); return container == null ? view : container; }
    public static void showError(View view, String message) {
        FieldContainer container = container(view);
        if (container != null) container.setError(message);
    }

    public static final class FieldContainer extends LinearLayout {
        private final TextView label, error;
        private final LinearLayout body;
        private final View control;
        private final Supplier<Object> value;
        private final String caption;
        private final CharSequence originalDescription;
        private final CharSequence checkboxCaption;
        private boolean required, invalid;
        private ViewTreeObserver.OnPreDrawListener errorObserver;

        FieldContainer(Context context, String caption, View control, Supplier<Object> value, TextView existingLabel) {
            super(context);
            setOrientation(VERTICAL);
            this.control = control; this.value = value;
            checkboxCaption = control instanceof CheckBox ? ((CheckBox) control).getText() : null;
            this.caption = checkboxCaption != null ? checkboxCaption.toString() : caption;
            originalDescription = control.getContentDescription();
            control.setTag(R.id.form_field_container, this);
            if (control.getId() == View.NO_ID) control.setId(ViewCompat.generateViewId());
            Palette palette = new Palette(context);
            label = existingLabel != null ? existingLabel : new AppCompatTextView(context);
            label.setText(caption);
            label.setTextSize(14);
            label.setTextColor(palette.caption);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setPadding(0, 0, 0, dp(this, 8));
            label.setLabelFor(control.getId());
            ViewCompat.setAccessibilityHeading(label, false);
            label.setVisibility(checkboxCaption == null && !caption.isEmpty() ? VISIBLE : GONE);
            addView(label, matchWidth());
            body = new LinearLayout(context); body.setOrientation(VERTICAL);
            addView(body, matchWidth());
            error = new AppCompatTextView(context);
            error.setTextColor(palette.error); error.setTextSize(13);
            error.setPadding(0, dp(this, 6), 0, 0);
            error.setVisibility(GONE);
            ViewCompat.setAccessibilityLiveRegion(error, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
            addView(error, matchWidth());
        }
        public String caption() { return caption; }
        public void setRequired(boolean required) {
            if (this.required == required && label.getTag(R.id.form_required_caption) != null) return;
            this.required = required;
            label.setTag(R.id.form_required_caption, caption);
            if (checkboxCaption != null) ((CheckBox) control).setText(caption + (required ? " *" : ""));
            else label.setText(caption + (required ? " *" : ""));
            control.setContentDescription(required ? getContext().getString(R.string.form_required_field_hint, caption) : originalDescription);
        }
        public void setError(String message) {
            invalid = message != null;
            error.setText(message);
            error.setVisibility(invalid ? VISIBLE : GONE);
            if (control instanceof TextView || control instanceof Spinner) {
                if (invalid) control.setBackground(outline(control, new Palette(getContext()).surface, new Palette(getContext()).error, 2));
                else styleControl(control);
            }
            if (invalid && errorObserver == null) {
                errorObserver = () -> {
                    if (invalid && (!RequiredFieldValidation.isMissing(value.get()) || getVisibility() == GONE)) setError(null);
                    return true;
                };
                getViewTreeObserver().addOnPreDrawListener(errorObserver);
            } else if (!invalid) removeErrorObserver();
        }
        private void removeErrorObserver() {
            if (errorObserver != null && getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnPreDrawListener(errorObserver);
            errorObserver = null;
        }
        @Override protected void onDetachedFromWindow() { removeErrorObserver(); super.onDetachedFromWindow(); }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (invalid) setError(error.getText().toString()); }
    }

    /** Use the actual app theme, including its explicit dark setting, rather than system night mode. */
    private static final class Palette {
        final int surface, disabled, value, caption, hint, border, accent, error;
        Palette(Context context) {
            TypedArray colors = context.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary, androidx.appcompat.R.attr.colorAccent});
            boolean dark = ColorUtils.calculateLuminance(colors.getColor(0, Color.BLACK)) > 0.5;
            accent = colors.getColor(1, Color.rgb(38, 112, 74)); colors.recycle();
            surface = Color.parseColor(dark ? "#222E28" : "#FFFFFF");
            disabled = Color.parseColor(dark ? "#2C3831" : "#F0F4F2");
            value = Color.parseColor(dark ? "#EEF5F1" : "#1B2925");
            caption = Color.parseColor(dark ? "#B6C6BD" : "#4C6058");
            hint = Color.parseColor(dark ? "#A2B4A8" : "#66756E");
            border = Color.parseColor(dark ? "#6B7E73" : "#AEBDB5");
            error = Color.parseColor(dark ? "#FFB4AB" : "#B3261E");
        }
    }
}
