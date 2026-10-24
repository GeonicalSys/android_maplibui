package com.nextgis.maplibui.util;

import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.nextgis.maplib.display.*;
import com.nextgis.maplibui.R;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;
import yuku.ambilwarna.AmbilWarnaDialog;

/** Binds optional polygon settings without altering an old style just by opening its editor. */
public final class PolygonStyleControls {
    private PolygonStyleControls() { }
    public static void bind(View root, SimplePolygonStyle style) {
        PolygonPatternOptions pattern = style.getPatternOptions() == null ? new PolygonPatternOptions() : style.getPatternOptions();
        PolygonOutlineOptions outline = style.getOutlineOptions() == null ? new PolygonOutlineOptions() : style.getOutlineOptions();
        CheckBox configure = root.findViewById(R.id.pattern_configure);
        CheckBox advanced = root.findViewById(R.id.outline_configure);
        configure.setChecked(style.getPatternOptions() != null && pattern.isEnabled());
        advanced.setChecked(style.getOutlineOptions() != null && outline.isEnabled());
        Runnable patternVisibility = () -> {
            boolean custom = style.getFillPatternImage() != null;
            configure.setEnabled(!custom);
            root.findViewById(R.id.pattern_options).setVisibility(configure.isChecked() && !custom ? View.VISIBLE : View.GONE);
            // Procedural pattern selection stays usable when the background is disabled.
            boolean visible = style.isFill() || configure.isChecked();
            for (int id : new int[]{R.id.fill_pattern_label,R.id.fill_pattern,R.id.fill_pattern_image_label,
                    R.id.fill_pattern_image,R.id.fill_translate_label,R.id.fill_translate})
                root.findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE);
            boolean second = style.getFillPattern() == PolygonPatternRegistry.FILL_PATTERN_CROSS
                    || style.getFillPattern() == PolygonPatternRegistry.FILL_PATTERN_FOREST;
            root.findViewById(R.id.pattern_second_angle).setVisibility(second ? View.VISIBLE : View.GONE);
            root.findViewById(R.id.pattern_second_angle_label).setVisibility(second ? View.VISIBLE : View.GONE);
        };
        configure.setOnCheckedChangeListener((button, checked) -> {
            pattern.setEnabled(checked); style.setPatternOptions(pattern);
            if (checked && style.getFillPattern() == 0) {
                style.setFillPattern(1); ((Spinner)root.findViewById(R.id.fill_pattern)).setSelection(1);
            }
            patternVisibility.run();
        });
        root.findViewById(R.id.outline_options).setVisibility(advanced.isChecked() ? View.VISIBLE : View.GONE);
        advanced.setOnCheckedChangeListener((button, checked) -> {
            outline.setEnabled(checked); style.setOutlineOptions(outline);
            root.findViewById(R.id.outline_options).setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        spinner(root,R.id.fill_pattern,R.array.polygon_fill_patterns,style.getFillPattern(),value -> {
            style.setFillPattern(value); patternVisibility.run();
        });
        EditText image = root.findViewById(R.id.fill_pattern_image);
        image.addTextChangedListener(watcher(value -> { style.setFillPatternImage(value); patternVisibility.run(); }));
        ((CheckBox) root.findViewById(R.id.fill)).setOnCheckedChangeListener((button, checked) -> {
            style.setFill(checked); patternVisibility.run();
        });
        number(root,R.id.pattern_angle,pattern.getAngle(),-360,360,pattern::setAngle);
        number(root,R.id.pattern_second_angle,pattern.getSecondAngle(),-360,360,pattern::setSecondAngle);
        number(root,R.id.pattern_spacing,pattern.getSpacing(),4,64,pattern::setSpacing);
        number(root,R.id.pattern_width,pattern.getWidth(),.5f,16,pattern::setWidth);
        colour(root,R.id.pattern_color,R.string.polygon_pattern_color,pattern.getColor(),pattern::setColor);
        alpha(root,R.id.pattern_alpha,R.id.pattern_alpha_label,R.string.polygon_pattern_alpha,pattern.getAlpha(),pattern::setAlpha);
        Runnable dashVisibility = () -> {
            int visibility = outline.getType() == PolygonOutlineOptions.CUSTOM ? View.VISIBLE : View.GONE;
            root.findViewById(R.id.outline_custom_dash).setVisibility(visibility);
            root.findViewById(R.id.outline_custom_dash_label).setVisibility(visibility);
            EditText input = root.findViewById(R.id.outline_custom_dash);
            if (visibility == View.VISIBLE) input.setError(MplStyleMapper.isValidDashArray(input.getText().toString())
                    ? null : root.getContext().getString(R.string.polygon_outline_custom_dash));
        };
        spinner(root,R.id.outline_type,R.array.polygon_outline_types,outline.getType(),value -> { outline.setType(value); dashVisibility.run(); });
        EditText dash = root.findViewById(R.id.outline_custom_dash); dash.setText(outline.getDashArray());
        dash.addTextChangedListener(watcher(value -> {
            boolean valid = MplStyleMapper.isValidDashArray(value);
            dash.setError(valid ? null : root.getContext().getString(R.string.polygon_outline_custom_dash));
            if (valid) outline.setDashArray(value);
        }));
        spinner(root,R.id.outline_cap,R.array.line_caps,outline.getCap(),outline::setCap);
        spinner(root,R.id.outline_join,R.array.line_joins,outline.getJoin(),outline::setJoin);
        number(root,R.id.outline_miter,outline.getMiter(),1,10,outline::setMiter);
        number(root,R.id.outline_gap,outline.getGap(),0,64,outline::setGap);
        number(root,R.id.outline_offset,outline.getOffset(),-64,64,outline::setOffset);
        CheckBox casing = root.findViewById(R.id.outline_casing); casing.setChecked(outline.isCasing());
        root.findViewById(R.id.outline_casing_options).setVisibility(outline.isCasing() ? View.VISIBLE : View.GONE);
        casing.setOnCheckedChangeListener((button,checked) -> {
            outline.setCasing(checked); root.findViewById(R.id.outline_casing_options).setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        number(root,R.id.outline_casing_width,outline.getCasingWidth(),0,32,outline::setCasingWidth);
        colour(root,R.id.outline_casing_color,R.string.polygon_outline_casing_color,outline.getCasingColor(),outline::setCasingColor);
        alpha(root,R.id.outline_casing_alpha,R.id.outline_casing_alpha_label,R.string.polygon_outline_casing_alpha,
                outline.getCasingAlpha(),outline::setCasingAlpha);
        dashVisibility.run(); patternVisibility.run();
    }
    private static void number(View root,int id,float initial,float min,float max,Consumer<Float> setter) {
        EditText input = root.findViewById(id); input.setText(format(initial));
        input.addTextChangedListener(watcher(text -> {
            try {
                float value = Float.parseFloat(text.trim().replace(',','.'));
                if (!Float.isFinite(value) || value < min || value > max) throw new NumberFormatException();
                input.setError(null); setter.accept(value);
            } catch (NumberFormatException error) {
                input.setError(root.getContext().getString(R.string.style_invalid_number,format(min),format(max)));
            }
        }));
    }
    private static String format(float value) { return String.format(Locale.US,"%s",value == (int)value ? Integer.toString((int)value) : Float.toString(value)); }
    private static void spinner(View root,int id,int array,int initial,Consumer<Integer> setter) {
        Spinner spinner = root.findViewById(id);
        spinner.setAdapter(new FormChoiceAdapter(root.getContext(),Arrays.asList(root.getResources().getStringArray(array)),value -> false));
        spinner.setSelection(initial);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long itemId) { setter.accept(position); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }
    private static void alpha(View root,int id,int labelId,int caption,int initial,Consumer<Integer> setter) {
        SeekBar bar = root.findViewById(id); TextView label = root.findViewById(labelId);
        Consumer<Integer> update = value -> label.setText(root.getContext().getString(caption)+": "+Math.round(value*100/255f)+"%");
        bar.setProgress(initial); update.accept(initial);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar,int progress,boolean fromUser) { setter.accept(progress); update.accept(progress); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }
    private static void colour(View root,int id,int caption,int initial,Consumer<Integer> setter) {
        Button button = root.findViewById(id); final int[] selected = {initial};
        Consumer<Integer> update = value -> {
            String hex = String.format("#%06X",value & 0xffffff);
            button.setText(hex); button.setContentDescription(root.getContext().getString(caption)+" "+hex);
            android.graphics.drawable.GradientDrawable swatch = new android.graphics.drawable.GradientDrawable();
            swatch.setShape(android.graphics.drawable.GradientDrawable.OVAL); swatch.setColor(value);
            int pixels = Math.round(20 * root.getResources().getDisplayMetrics().density); swatch.setSize(pixels,pixels);
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(swatch,null,null,null);
        };
        update.accept(initial);
        button.setOnClickListener(v -> new AmbilWarnaDialog(root.getContext(),selected[0],new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override public void onOk(AmbilWarnaDialog dialog,int colour) { selected[0]=colour; setter.accept(colour); update.accept(colour); }
            @Override public void onCancel(AmbilWarnaDialog dialog) { }
        }).show());
    }
    private static TextWatcher watcher(Consumer<String> change) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { }
            @Override public void afterTextChanged(Editable value) { change.accept(value.toString()); }
        };
    }
    public static boolean hasInputError(View view) {
        if (view.getVisibility() != View.VISIBLE) return false;
        if (view instanceof EditText && ((EditText)view).getError() != null) return true;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++)
            if (hasInputError(((ViewGroup)view).getChildAt(i))) return true;
        return false;
    }
}
