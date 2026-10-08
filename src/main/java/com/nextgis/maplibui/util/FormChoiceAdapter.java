package com.nextgis.maplibui.util;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.nextgis.maplib.forms.CascadingLists;
import com.nextgis.maplibui.R;
import java.util.List;
import java.util.function.Predicate;

/** Wrapped values in the collapsed field and full-height choices in the native picker. */
public final class FormChoiceAdapter<T> extends ArrayAdapter<T> {
    private final Predicate<T> placeholder;
    public FormChoiceAdapter(Context context) {
        super(context, R.layout.formtemplate_spinner);
        placeholder = value -> value == null || CascadingLists.isMissing(value.toString());
        setDropDownViewResource(R.layout.formtemplate_spinner_choice);
    }
    public FormChoiceAdapter(Context context, List<T> values, Predicate<T> placeholder) {
        super(context, R.layout.formtemplate_spinner, values);
        this.placeholder = placeholder;
        setDropDownViewResource(R.layout.formtemplate_spinner_choice);
    }
    @Override public View getView(int position, View recycled, ViewGroup parent) {
        TextView view = (TextView) super.getView(position, recycled, parent);
        FormFieldLayout.styleChoice(view, placeholder.test(getItem(position)));
        return view;
    }
    @Override public View getDropDownView(int position, View recycled, ViewGroup parent) {
        TextView view = (TextView) super.getDropDownView(position, recycled, parent);
        FormFieldLayout.styleChoice(view, placeholder.test(getItem(position)));
        return view;
    }
}
