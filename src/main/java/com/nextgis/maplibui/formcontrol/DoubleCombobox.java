/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * Author:   NikitaFeodonit, nfeodonit@yandex.com
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2015-2016, 2019-2020 NextGIS, info@nextgis.com
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

package com.nextgis.maplibui.formcontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import androidx.appcompat.widget.AppCompatSpinner;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.activity.ModifyAttributesActivity;
import com.nextgis.maplibui.api.IFormControl;
import com.nextgis.maplibui.control.AliasList;
import com.nextgis.maplibui.util.ControlHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nextgis.maplib.util.LayerUtil.getColumnIndexSafely;
import static com.nextgis.maplibui.util.ConstantsUI.JSON_ATTRIBUTES_KEY;
import static com.nextgis.maplibui.util.ConstantsUI.JSON_DEFAULT_KEY;
import static com.nextgis.maplibui.util.ConstantsUI.JSON_FIELD_LEVEL1_KEY;
import static com.nextgis.maplibui.util.ConstantsUI.JSON_FIELD_LEVEL2_KEY;
import static com.nextgis.maplibui.util.ConstantsUI.JSON_VALUES_KEY;
import static com.nextgis.maplibui.util.ConstantsUI.JSON_VALUE_ALIAS_KEY;
import static com.nextgis.maplibui.util.ConstantsUI.JSON_VALUE_NAME_KEY;


public class DoubleCombobox extends AppCompatSpinner implements IFormControl
{
    protected Spinner mSubCombobox;

    protected String mFieldName;
    protected String mSubFieldName;

    protected boolean mIsShowLast;

    protected Map<String, String>              mAliasValueMap;
    protected Map<String, Map<String, String>> mSubAliasValueMaps;
    protected Map<String, AliasList>           mAliasSubListMap;

    protected boolean mFirstShow = true;
    private com.nextgis.maplibui.util.CascadingFormController mCascades;

    public DoubleCombobox(Context context) {
        super(context);
    }

    public DoubleCombobox(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DoubleCombobox(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    //TODO: add mode_dialog if attribute asDialog == true, Spinner.MODE_DIALOG API Level 11+

    @Override
    public void init(JSONObject element, List<Field> fields, Bundle savedState,
                     Cursor featureCursor, SharedPreferences preferences,
                     Map<String, Map<String, String>> translations
    ,final ModifyAttributesActivity modifyAttributesActivity) throws JSONException {
        mSubCombobox = new Spinner(getContext(), Spinner.MODE_DIALOG);
        JSONObject attributes = element.getJSONObject(JSON_ATTRIBUTES_KEY);
        mFieldName = attributes.getString(JSON_FIELD_LEVEL1_KEY);
        mSubFieldName = attributes.getString(JSON_FIELD_LEVEL2_KEY);
        mIsShowLast = ControlHelper.isSaveLastValue(attributes);
        setEnabled(ControlHelper.isEnabled(fields, mFieldName));
        com.nextgis.maplibui.util.FormFieldLayout.prepareSelector(this);
        com.nextgis.maplibui.util.FormFieldLayout.prepareSelector(mSubCombobox);

        if (modifyAttributesActivity instanceof com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity) {
            com.nextgis.maplibui.util.CascadingFormController controller =
                    ((com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity) modifyAttributesActivity).getCascadingLists();
            if (controller != null && (controller.manages(mFieldName) || controller.manages(mSubFieldName))) {
                if (!controller.manages(mFieldName) || !controller.manages(mSubFieldName))
                    throw new JSONException("Both double-combobox fields must be managed together");
                mCascades = controller;
                mIsShowLast = false;
                controller.register(mFieldName, this);
                controller.register(mSubFieldName, mSubCombobox);
                return;
            }
        }

        String lastValue = null;
        String subLastValue = null;
        if (ControlHelper.hasKey(savedState, mFieldName) && ControlHelper.hasKey(savedState, mSubFieldName)) {
            lastValue = savedState.getString(ControlHelper.getSavedStateKey(mFieldName));
            subLastValue = savedState.getString(ControlHelper.getSavedStateKey(mSubFieldName));
        } else if (null != featureCursor) {
            int column = getColumnIndexSafely(featureCursor, mFieldName); // featureCursor.getColumnIndex(mFieldName);
            int subColumn = getColumnIndexSafely(featureCursor, mSubFieldName );// featureCursor.getColumnIndex(mSubFieldName);
            if (column >= 0)
                lastValue = featureCursor.getString(column);
            if (subColumn >= 0)
                subLastValue = featureCursor.getString(subColumn);
        } else if (mIsShowLast) {
            lastValue = preferences.getString(mFieldName, null);
            subLastValue = preferences.getString(mSubFieldName, null);
        }

        JSONArray values = attributes.optJSONArray(JSON_VALUES_KEY);
        int defaultPosition = 0;
        int lastValuePosition = -1;
        int subLastValuePosition = -1;
        mAliasValueMap = new HashMap<>();
        mSubAliasValueMaps = new HashMap<>();
        mAliasSubListMap = new HashMap<>();

        final ArrayAdapter<String> comboboxAdapter = new com.nextgis.maplibui.util.FormChoiceAdapter<>(getContext());
        setAdapter(comboboxAdapter);

        if (values != null) {
            for (int j = 0; j < values.length(); j++) {
                JSONObject keyValue = values.getJSONObject(j);
                String value = keyValue.getString(JSON_VALUE_NAME_KEY);
                String valueAlias = keyValue.getString(JSON_VALUE_ALIAS_KEY);

                Map<String, String> subAliasValueMap = new HashMap<>();
                AliasList subAliasList = new AliasList();

                mAliasValueMap.put(valueAlias, value);
                mSubAliasValueMaps.put(valueAlias, subAliasValueMap);
                mAliasSubListMap.put(valueAlias, subAliasList);
                comboboxAdapter.add(valueAlias);

                if (keyValue.has(JSON_DEFAULT_KEY) && keyValue.getBoolean(JSON_DEFAULT_KEY))
                    defaultPosition = j;

                if (null != lastValue && lastValue.equals(value)) // if modify data
                    lastValuePosition = j;

                JSONArray subValues = keyValue.getJSONArray(JSON_VALUES_KEY);
                for (int k = 0; k < subValues.length(); k++) {
                    JSONObject subKeyValue = subValues.getJSONObject(k);
                    String subValue = subKeyValue.getString(JSON_VALUE_NAME_KEY);
                    String subValueAlias = subKeyValue.getString(JSON_VALUE_ALIAS_KEY);

                    subAliasValueMap.put(subValueAlias, subValue);
                    subAliasList.aliasList.add(subValueAlias);

                    if (subKeyValue.has(JSON_DEFAULT_KEY) && subKeyValue.getBoolean(JSON_DEFAULT_KEY))
                        subAliasList.defaultPosition = k;

                    if (null != subLastValue && subLastValue.equals(subValue)
                    && (null!= lastValue) && lastValue.equals(value)) { // if modify data
                        //lastValuePosition = j;
                        subLastValuePosition = k;
                    }
                }
            }
        }

        setSelection(lastValuePosition >= 0 ? lastValuePosition : defaultPosition);
        final int subLastValuePositionFinal = subLastValuePosition;

        // The drop down view
        comboboxAdapter.setDropDownViewResource(R.layout.formtemplate_spinner_choice);

        setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener()
                {
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id)
                    {
                        String selectedValueAlias = comboboxAdapter.getItem(position);
                        AliasList subAliasList = mAliasSubListMap.get(selectedValueAlias);

                        ArrayAdapter<String> subComboboxAdapter = new com.nextgis.maplibui.util.FormChoiceAdapter<>(
                                getContext(), subAliasList.aliasList, com.nextgis.maplib.forms.CascadingLists::isMissing);
                        subComboboxAdapter.setDropDownViewResource(
                                R.layout.formtemplate_spinner_choice);

                        mSubCombobox.setAdapter(subComboboxAdapter);
                        mSubCombobox.setSelection(
                                mFirstShow && subLastValuePositionFinal >= 0
                                ? subLastValuePositionFinal
                                : subAliasList.defaultPosition);

                        if (mFirstShow) {
                            mFirstShow = false;
                        }
                    }


                    public void onNothingSelected(AdapterView<?> arg0)
                    {
                    }
                });
    }

    @Override
    public void saveLastValue(SharedPreferences preferences) {
        DoubleComboboxValue result = (DoubleComboboxValue) getValue();
        preferences.edit().putString(result.mFieldName, result.mValue).commit();
        preferences.edit().putString(result.mSubFieldName, result.mSubValue).commit();
    }

    @Override
    public boolean isShowLast() {
        return mIsShowLast;
    }


    @Override
    public void setEnabled(boolean enabled)
    {
        super.setEnabled(enabled);
        if (mSubCombobox != null) mSubCombobox.setEnabled(enabled);
    }


    public String getFieldName()
    {
        return mFieldName;
    }

    public View getFieldView(String fieldName) {
        if (com.nextgis.maplibui.util.RequiredFieldUi.sameName(fieldName, mFieldName)) return this;
        if (com.nextgis.maplibui.util.RequiredFieldUi.sameName(fieldName, mSubFieldName))
            return mSubCombobox;
        return null;
    }


    @Override
    public void addToLayout(ViewGroup layout)
    {
        com.nextgis.maplibui.util.FormFieldLayout.addSelector(layout, this);
        com.nextgis.maplibui.util.FormFieldLayout.addSelector(layout, mSubCombobox);
    }


    @Override
    public Object getValue()
    {
        if (mCascades != null) {
            DoubleComboboxValue result = new DoubleComboboxValue();
            result.mFieldName = mFieldName; result.mValue = mCascades.value(mFieldName);
            result.mSubFieldName = mSubFieldName; result.mSubValue = mCascades.value(mSubFieldName);
            return result;
        }
        String valueAlias = (String) getSelectedItem();
        String subValueAlias = (String) mSubCombobox.getSelectedItem();

        String value = mAliasValueMap.get(valueAlias);
        Map<String, String> val = mSubAliasValueMaps.get(valueAlias);
        String subValue = val != null ? val.get(subValueAlias) : null;

        DoubleComboboxValue retValue = new DoubleComboboxValue();

        retValue.mFieldName = mFieldName;
        retValue.mValue = value;

        retValue.mSubFieldName = mSubFieldName;
        retValue.mSubValue = subValue;

        return retValue;
    }

    @Override
    public void saveState(Bundle outState) {
        DoubleComboboxValue result = (DoubleComboboxValue) getValue();
        outState.putString(ControlHelper.getSavedStateKey(mFieldName), result.mValue);
        outState.putString(ControlHelper.getSavedStateKey(mSubFieldName), result.mSubValue);
    }
}
