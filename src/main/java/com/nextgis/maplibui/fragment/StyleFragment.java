/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2016-2018 NextGIS, info@nextgis.com
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

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import androidx.appcompat.widget.SwitchCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.nextgis.maplib.api.ITextStyle;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.display.LabelAttributes;
import com.nextgis.maplib.display.MplStyleMapper;
import com.nextgis.maplib.display.SimpleLineStyle;
import com.nextgis.maplib.display.SimpleMarkerStyle;
import com.nextgis.maplib.display.SimplePolygonStyle;
import com.nextgis.maplib.display.Style;
import com.nextgis.maplib.display.TextStyleUtil;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.dialog.StyledDialogFragment;
import com.nextgis.maplibui.util.ControlHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import yuku.ambilwarna.AmbilWarnaDialog;

public class StyleFragment extends StyledDialogFragment implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {
    protected ImageView mColorFillImage, mColorStrokeImage, mColorTextImage, mColorTextHaloImage;
    protected TextView mColorFillName, mColorStrokeName, mColorTextName, mColorTextHaloName;
    protected LinearLayout mColorText, mColorTextHalo;
    protected EditText mEditText, mLabelTemplateEdit, mTextHaloWidthEdit, mLabelMinZoomEdit, mLabelMaxZoomEdit;
    protected Spinner mField, mTextSize, mTextAlignment, mLineLabelRotation;
    protected CheckBox mTextEnabled;
    protected SwitchCompat mNotHardcoded, mTextScaleWithZoom, mTextAllowOverlap, mLineLabelRepeat;
    protected TextView mFillAlphaLabel, mStrokeAlphaLabel;
    protected TextView mTextOpacityLabel;
    protected SeekBar mFillAlphaSeek, mStrokeAlphaSeek, mTextOpacitySeek;
    protected int mFillColor, mStrokeColor, mTextColor, mTextHaloColor;
    protected Style mStyle;
    protected VectorLayer mLayer;

    public StyleFragment() {
    }

    public void setStyle(Style style) {
        mStyle = style;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (mStyle == null)
            return null;

        View body = null;
        mFillColor = mStyle.getColor();
        if (mStyle instanceof SimpleMarkerStyle) {
            body = inflater.inflate(R.layout.style_marker, container, false);
            // inflate marker
            inflateMarker(body);
        } else if (mStyle instanceof SimpleLineStyle) {
            body = inflater.inflate(R.layout.style_line, container, false);
            inflateLine(body);
        } else if (mStyle instanceof SimplePolygonStyle) {
            body = inflater.inflate(R.layout.style_polygon, container, false);
            inflatePolygon(body);
        }

        inflateText(body);
        inflateLabelSettings(body);
        inflateOpacity(body);

        setView(body, true);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    private void inflateMarker(View v) {
        Spinner type = v.findViewById(R.id.type);
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ((SimpleMarkerStyle) mStyle).setType(position + 1);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        type.setSelection(((SimpleMarkerStyle) mStyle).getType() - 1);

        Spinner textAlignment = v.findViewById(R.id.text_alignment);
        textAlignment.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ((SimpleMarkerStyle) mStyle).setTextAlignment(SimpleMarkerStyle.ALIGNMENTS.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        int alignment = ((SimpleMarkerStyle) mStyle).getTextAlignment();
        textAlignment.setSelection(SimpleMarkerStyle.ALIGNMENTS.indexOf(alignment));

        float markerSize = ((SimpleMarkerStyle) mStyle).getSize();
        EditText sizeText = v.findViewById(R.id.size);
        sizeText.setText(String.format(Locale.getDefault(), "%.0f", markerSize));
        sizeText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    ((SimpleMarkerStyle) mStyle).setSize(Float.parseFloat(s.toString()));
                } catch (Exception ignored) { }
            }
        });

        bindBlurPresetSpinner(v.findViewById(R.id.circle_blur),
                MplStyleMapper.blurPresetIndex(((SimpleMarkerStyle) mStyle).getCircleBlur()),
                preset -> ((SimpleMarkerStyle) mStyle).setCircleBlur(MplStyleMapper.blurPresetValue(preset)));

        mStrokeColor = mStyle.getOutColor();
        mColorFillName = v.findViewById(R.id.color_fill_name);
        mColorFillImage = v.findViewById(R.id.color_fill_ring);
        mColorStrokeName = v.findViewById(R.id.color_stroke_name);
        mColorStrokeImage = v.findViewById(R.id.color_stroke_ring);

        LinearLayout color_fill = v.findViewById(R.id.color_fill);
        LinearLayout color_stroke = v.findViewById(R.id.color_stroke);
        color_fill.setOnClickListener(this);
        color_stroke.setOnClickListener(this);
        setFillColor(mFillColor);
        setStrokeColor(mStrokeColor);

        float width = mStyle.getWidth();
        EditText widthText = v.findViewById(R.id.width);
        widthText.setText(String.format(Locale.getDefault(), "%.0f", width));
        widthText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    mStyle.setWidth(Float.parseFloat(s.toString()));
                } catch (Exception ignored) { }
            }
        });
        inflateGeometryScale(v);
    }

    private void inflateGeometryScale(View v) {
        CheckBox scaleWithZoom = v.findViewById(R.id.geometry_scale_with_zoom);
        if (scaleWithZoom == null) {
            return;
        }
        scaleWithZoom.setChecked(mStyle.isScaleSizeWithZoom());
        scaleWithZoom.setOnCheckedChangeListener((buttonView, isChecked) ->
                mStyle.setScaleSizeWithZoom(isChecked));
    }

    private void inflateLine(View v) {
        mStrokeColor = mStyle.getOutColor();

        mColorFillName = v.findViewById(R.id.color_fill_name);
        mColorFillImage = v.findViewById(R.id.color_fill_ring);
        mColorStrokeName = v.findViewById(R.id.color_stroke_name);
        mColorStrokeImage = v.findViewById(R.id.color_stroke_ring);

        LinearLayout color_fill = v.findViewById(R.id.color_fill);
        LinearLayout color_stroke = v.findViewById(R.id.color_stroke);
        color_fill.setOnClickListener(this);
        color_stroke.setOnClickListener(this);
        setFillColor(mFillColor);
        setStrokeColor(mStrokeColor);

        float width = mStyle.getWidth();
        EditText widthText = v.findViewById(R.id.width);
        widthText.setText(String.format(Locale.getDefault(), "%.0f", width));
        widthText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    mStyle.setWidth(Float.parseFloat(s.toString()));
                } catch (Exception ignored) { }
            }
        });

        Spinner type = v.findViewById(R.id.type);
        final View dashPresetLabel = v.findViewById(R.id.dash_preset_label);
        final Spinner dashPreset = v.findViewById(R.id.dash_preset);
        if (dashPreset != null) {
            dashPreset.setSelection(((SimpleLineStyle) mStyle).getDashPreset());
            dashPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    ((SimpleLineStyle) mStyle).setDashPreset(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        Spinner lineCap = v.findViewById(R.id.line_cap);
        if (lineCap != null) {
            lineCap.setSelection(((SimpleLineStyle) mStyle).getLineCap());
            lineCap.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    ((SimpleLineStyle) mStyle).setLineCap(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        Spinner lineJoin = v.findViewById(R.id.line_join);
        final View lineMiterLimitLabel = v.findViewById(R.id.line_miter_limit_label);
        final EditText lineMiterLimit = v.findViewById(R.id.line_miter_limit);
        if (lineJoin != null) {
            lineJoin.setSelection(((SimpleLineStyle) mStyle).getLineJoin());
            lineJoin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    ((SimpleLineStyle) mStyle).setLineJoin(position);
                    updateLineMiterLimitVisibility(lineMiterLimitLabel, lineMiterLimit, position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            updateLineMiterLimitVisibility(
                    lineMiterLimitLabel, lineMiterLimit, ((SimpleLineStyle) mStyle).getLineJoin());
        }
        if (lineMiterLimit != null) {
            lineMiterLimit.setText(String.format(Locale.getDefault(), "%.1f",
                    ((SimpleLineStyle) mStyle).getLineMiterLimit()));
            lineMiterLimit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    try {
                        ((SimpleLineStyle) mStyle).setLineMiterLimit(Float.parseFloat(s.toString()));
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        bindBlurPresetSpinner(v.findViewById(R.id.line_blur),
                MplStyleMapper.blurPresetIndex(((SimpleLineStyle) mStyle).getLineBlur()),
                preset -> ((SimpleLineStyle) mStyle).setLineBlur(MplStyleMapper.blurPresetValue(preset)));

        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ((SimpleLineStyle) mStyle).setType(position + 1);
                updateLineDashPresetVisibility(dashPresetLabel, dashPreset);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        type.setSelection(((SimpleLineStyle) mStyle).getType() - 1);
        updateLineDashPresetVisibility(dashPresetLabel, dashPreset);
        inflateGeometryScale(v);
    }

    private void bindBlurPresetSpinner(
            Spinner spinner,
            int selectedPreset,
            BlurPresetListener listener) {
        if (spinner == null) {
            return;
        }
        spinner.setSelection(selectedPreset);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                listener.onBlurPresetSelected(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private interface BlurPresetListener {
        void onBlurPresetSelected(int preset);
    }

    private void updateLineMiterLimitVisibility(View label, View field, int lineJoin) {
        if (label == null || field == null) {
            return;
        }
        int visibility = lineJoin == MplStyleMapper.LINE_JOIN_MITER ? View.VISIBLE : View.GONE;
        label.setVisibility(visibility);
        field.setVisibility(visibility);
    }

    private void updateLineDashPresetVisibility(View dashPresetLabel, Spinner dashPreset) {
        if (dashPresetLabel == null || dashPreset == null) {
            return;
        }
        boolean isDash = mStyle instanceof SimpleLineStyle
                && MplStyleMapper.isLineDashType(((SimpleLineStyle) mStyle).getType());
        int visibility = isDash ? View.VISIBLE : View.GONE;
        dashPresetLabel.setVisibility(visibility);
        dashPreset.setVisibility(visibility);
    }

    private void inflatePolygon(View v) {
        float width = mStyle.getWidth();
        boolean fill = ((SimplePolygonStyle) mStyle).isFill();
        mStrokeColor = mStyle.getOutColor();

        mColorFillName = v.findViewById(R.id.color_fill_name);
        mColorFillImage = v.findViewById(R.id.color_fill_ring);
        mColorStrokeName = v.findViewById(R.id.color_stroke_name);
        mColorStrokeImage = v.findViewById(R.id.color_stroke_ring);

        CheckBox fillCheck = v.findViewById(R.id.fill);
        final View fillPatternLabel = v.findViewById(R.id.fill_pattern_label);
        final Spinner fillPattern = v.findViewById(R.id.fill_pattern);
        fillCheck.setChecked(fill);
        fillCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ((SimplePolygonStyle) mStyle).setFill(isChecked);
                updatePolygonFillPatternVisibility(fillPatternLabel, fillPattern, isChecked);
            }
        });
        if (fillPattern != null) {
            fillPattern.setSelection(((SimplePolygonStyle) mStyle).getFillPattern());
            fillPattern.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    ((SimplePolygonStyle) mStyle).setFillPattern(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }
        updatePolygonFillPatternVisibility(fillPatternLabel, fillPattern, fill);

        EditText widthText = v.findViewById(R.id.width);
        widthText.setText(String.format(Locale.getDefault(), "%.0f", width));
        widthText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    mStyle.setWidth(Float.parseFloat(s.toString()));
                } catch (Exception ignored) { }
            }
        });

        LinearLayout color_fill = v.findViewById(R.id.color_fill);
        color_fill.setOnClickListener(this);
        setFillColor(mFillColor);
        LinearLayout color_stroke = v.findViewById(R.id.color_stroke);
        color_stroke.setOnClickListener(this);
        setStrokeColor(mStrokeColor);
    }

    private void updatePolygonFillPatternVisibility(
            View fillPatternLabel, Spinner fillPattern, boolean fillEnabled) {
        if (fillPatternLabel == null || fillPattern == null) {
            return;
        }
        int visibility = fillEnabled ? View.VISIBLE : View.GONE;
        fillPatternLabel.setVisibility(visibility);
        fillPattern.setVisibility(visibility);
    }

    private void inflateText(View body) {
        if (!(mStyle instanceof ITextStyle))
            return;

        final ITextStyle style = (ITextStyle) mStyle;
        mTextEnabled = body.findViewById(R.id.text_enabled);
        body.findViewById(R.id.tsize).setVisibility(View.VISIBLE);
        if (!(mStyle instanceof SimpleMarkerStyle)) {
            View markerOnly = body.findViewById(R.id.text_marker_only);
            if (markerOnly != null) {
                markerOnly.setVisibility(View.GONE);
            }
        }
        if (mStyle instanceof SimpleLineStyle) {
            inflateLineLabelSettings(body);
        } else {
            View lineOnly = body.findViewById(R.id.text_line_only);
            if (lineOnly != null) {
                lineOnly.setVisibility(View.GONE);
            }
        }

        mNotHardcoded = body.findViewById(R.id.not_hardcoded);
        mTextSize = body.findViewById(R.id.text_size);
        mTextAlignment = body.findViewById(R.id.text_alignment);
        mColorText = body.findViewById(R.id.color_text);
        mColorTextName = body.findViewById(R.id.color_text_name);
        mColorTextImage = body.findViewById(R.id.color_text_ring);
        mTextColor = TextStyleUtil.getTextColor(mStyle);
        if (mColorText != null) {
            mColorText.setOnClickListener(this);
            setTextColor(mTextColor);
        }
        mEditText = body.findViewById(R.id.text);
        mEditText.setText(style.getText());
        mField = body.findViewById(R.id.field);

        mTextSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                TextStyleUtil.setTextSize(mStyle, TextStyleUtil.LABEL_TEXT_SIZES.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        mTextSize.setSelection(TextStyleUtil.indexOfTextSize(TextStyleUtil.getTextSize(mStyle)));

        String field = style.getField();

        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                style.setText(s.toString());
            }
        });

        final List<Field> mFields = mLayer.getFields();
        mFields.add(0, new Field(GeoConstants.FTInteger, Constants.FIELD_ID, Constants.FIELD_ID));
        final List<String> fieldNames = new ArrayList<>();
        int id = -1;
        for (int i = 0; i < mFields.size(); i++) {
            fieldNames.add(mFields.get(i).getAlias());
            if (mFields.get(i).getName().equals(field))
                id = i;
        }

        mTextEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mNotHardcoded.setEnabled(isChecked);
                mEditText.setEnabled(isChecked);
                mField.setEnabled(isChecked);
                mTextSize.setEnabled(isChecked);
                mTextAlignment.setEnabled(isChecked);
                if (mColorText != null)
                    mColorText.setEnabled(isChecked);
                if (mTextHaloWidthEdit != null)
                    mTextHaloWidthEdit.setEnabled(isChecked);
                if (mTextScaleWithZoom != null)
                    mTextScaleWithZoom.setEnabled(isChecked);
                if (mTextAllowOverlap != null)
                    mTextAllowOverlap.setEnabled(isChecked);
                if (mColorTextHalo != null)
                    mColorTextHalo.setEnabled(isChecked);
                if (mLineLabelRepeat != null)
                    mLineLabelRepeat.setEnabled(isChecked);
                if (mLineLabelRotation != null)
                    mLineLabelRotation.setEnabled(isChecked);

                if (!isChecked) {
                    style.setField(null);
                    style.setText(null);
                } else {
                    if (mNotHardcoded.isChecked()) {
                        style.setField(mFields.get(mField.getSelectedItemPosition()).getName());
                        style.setText(null);
                    } else {
                        style.setField(null);
                        style.setText(mEditText.getText().toString());
                    }
                }
            }
        });

        boolean hasText = style.getText() != null;
        boolean hasField = field != null;
        String template = getLabelAttributes().getLabelTemplate();
        boolean hasTemplate = template != null && !template.trim().isEmpty();
        boolean isChecked = hasField || hasText || hasTemplate;

        mTextEnabled.setChecked(isChecked);
        mNotHardcoded.setEnabled(isChecked);
        mEditText.setEnabled(isChecked);
        mField.setEnabled(isChecked);
        mTextSize.setEnabled(isChecked);
        mTextAlignment.setEnabled(isChecked);
        if (mLineLabelRepeat != null)
            mLineLabelRepeat.setEnabled(isChecked);
        if (mLineLabelRotation != null)
            mLineLabelRotation.setEnabled(isChecked);

        mNotHardcoded.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mEditText.setVisibility(isChecked ? View.GONE : View.VISIBLE);
                mField.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                style.setField(isChecked ? mFields.get(mField.getSelectedItemPosition()).getName(): null);
            }
        });
        mNotHardcoded.setChecked(hasField || !mTextEnabled.isChecked());

        ArrayAdapter fieldAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, fieldNames);
        mField.setAdapter(fieldAdapter);
        if (hasField && id > -1)
            mField.setSelection(id);

        mField.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mTextEnabled.isChecked() && mNotHardcoded.isChecked())
                    style.setField(mFields.get(position).getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void inflateLineLabelSettings(View body) {
        final LabelAttributes labelAttributes = getLabelAttributes();
        mLineLabelRepeat = body.findViewById(R.id.line_label_repeat);
        mLineLabelRotation = body.findViewById(R.id.line_label_rotation);

        if (mLineLabelRepeat != null) {
            mLineLabelRepeat.setChecked(labelAttributes.isLineLabelRepeat());
            mLineLabelRepeat.setOnCheckedChangeListener((buttonView, isChecked) ->
                    labelAttributes.setLineLabelRepeat(isChecked));
        }

        if (mLineLabelRotation != null) {
            mLineLabelRotation.setSelection(labelAttributes.isLineLabelHorizontal() ? 1 : 0);
            mLineLabelRotation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    labelAttributes.setLineLabelHorizontal(position == 1);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }
    }

    private void inflateOpacity(View body) {
        if (mStyle == null || getContext() == null) {
            return;
        }
        mFillAlphaLabel = body.findViewById(R.id.fill_alpha_label);
        mStrokeAlphaLabel = body.findViewById(R.id.stroke_alpha_label);
        mFillAlphaSeek = body.findViewById(R.id.fill_alpha_seek);
        mStrokeAlphaSeek = body.findViewById(R.id.stroke_alpha_seek);
        if (mFillAlphaSeek == null || mStrokeAlphaSeek == null) {
            return;
        }
        mFillAlphaSeek.setProgress(mStyle.getAlpha());
        mStrokeAlphaSeek.setProgress(mStyle.getOutAlpha());
        mFillAlphaSeek.setOnSeekBarChangeListener(this);
        mStrokeAlphaSeek.setOnSeekBarChangeListener(this);
        updateAlphaLabels();
    }

    private void updateAlphaLabels() {
        if (getContext() == null || mStyle == null) {
            return;
        }
        if (mFillAlphaLabel != null) {
            mFillAlphaLabel.setText(ControlHelper.getPercentValue(
                    getContext(), R.string.fill_opacity, mStyle.getAlpha() * 1.0f));
        }
        if (mStrokeAlphaLabel != null) {
            mStrokeAlphaLabel.setText(ControlHelper.getPercentValue(
                    getContext(), R.string.stroke_opacity, mStyle.getOutAlpha() * 1.0f));
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (!fromUser || mStyle == null) {
            return;
        }
        int id = seekBar.getId();
        if (id == R.id.fill_alpha_seek) {
            mStyle.setAlpha(progress);
        } else if (id == R.id.stroke_alpha_seek) {
            mStyle.setOutAlpha(progress);
        }
        updateAlphaLabels();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    private LabelAttributes getLabelAttributes() {
        if (mStyle instanceof SimpleMarkerStyle) {
            return ((SimpleMarkerStyle) mStyle).getLabelAttributes();
        }
        if (mStyle instanceof SimpleLineStyle) {
            return ((SimpleLineStyle) mStyle).getLabelAttributes();
        }
        if (mStyle instanceof SimplePolygonStyle) {
            return ((SimplePolygonStyle) mStyle).getLabelAttributes();
        }
        return LabelAttributes.defaults();
    }

    private void inflateLabelSettings(View body) {
        if (!(mStyle instanceof ITextStyle)) {
            return;
        }

        final LabelAttributes labelAttributes = getLabelAttributes();
        mLabelTemplateEdit = body.findViewById(R.id.label_template);

        if (mLabelTemplateEdit != null) {
            String template = labelAttributes.getLabelTemplate();
            mLabelTemplateEdit.setText(template != null ? template : "");
            mLabelTemplateEdit.setEnabled(true);
            mLabelTemplateEdit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    String value = s.toString().trim();
                    labelAttributes.setLabelTemplate(value.isEmpty() ? null : value);
                }
            });
        }

        mLabelMinZoomEdit = body.findViewById(R.id.label_min_zoom);
        mLabelMaxZoomEdit = body.findViewById(R.id.label_max_zoom);
        if (mLabelMinZoomEdit != null) {
            if (labelAttributes.getLabelMinZoom() >= 0f) {
                mLabelMinZoomEdit.setText(String.format(Locale.getDefault(), "%.0f",
                        labelAttributes.getLabelMinZoom()));
            }
            mLabelMinZoomEdit.addTextChangedListener(new SimpleZoomWatcher(labelAttributes, true));
        }
        if (mLabelMaxZoomEdit != null) {
            if (labelAttributes.getLabelMaxZoom() >= 0f) {
                mLabelMaxZoomEdit.setText(String.format(Locale.getDefault(), "%.0f",
                        labelAttributes.getLabelMaxZoom()));
            }
            mLabelMaxZoomEdit.addTextChangedListener(new SimpleZoomWatcher(labelAttributes, false));
        }

        mTextHaloColor = labelAttributes.getTextHaloColor();
        mColorTextHalo = body.findViewById(R.id.color_text_halo);
        mColorTextHaloImage = body.findViewById(R.id.color_text_halo_ring);
        mColorTextHaloName = body.findViewById(R.id.color_text_halo_name);
        if (mColorTextHalo != null) {
            mColorTextHalo.setOnClickListener(this);
            setHaloColor(mTextHaloColor);
        }

        mTextHaloWidthEdit = body.findViewById(R.id.text_halo_width);
        if (mTextHaloWidthEdit != null) {
            mTextHaloWidthEdit.setText(
                    String.format(Locale.getDefault(), "%.1f", labelAttributes.getTextHaloWidth()));
            mTextHaloWidthEdit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    try {
                        labelAttributes.setTextHaloWidth(Float.parseFloat(s.toString()));
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        mTextScaleWithZoom = body.findViewById(R.id.text_scale_with_zoom);
        if (mTextScaleWithZoom != null) {
            mTextScaleWithZoom.setChecked(labelAttributes.isTextScaleWithZoom());
            mTextScaleWithZoom.setOnCheckedChangeListener((buttonView, isChecked) ->
                    labelAttributes.setTextScaleWithZoom(isChecked));
        }

        mTextAllowOverlap = body.findViewById(R.id.text_allow_overlap);
        if (mTextAllowOverlap != null) {
            Boolean allowOverlap = labelAttributes.getTextAllowOverlap();
            mTextAllowOverlap.setChecked(allowOverlap != null && allowOverlap);
            mTextAllowOverlap.setOnCheckedChangeListener((buttonView, isChecked) ->
                    labelAttributes.setTextAllowOverlap(isChecked));
        }

        mTextOpacityLabel = body.findViewById(R.id.text_opacity_label);
        mTextOpacitySeek = body.findViewById(R.id.text_opacity_seek);
        if (mTextOpacitySeek != null) {
            mTextOpacitySeek.setProgress(labelAttributes.getTextOpacity());
            updateTextOpacityLabel(labelAttributes.getTextOpacity());
            mTextOpacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        labelAttributes.setTextOpacity(progress);
                        updateTextOpacityLabel(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }
    }

    private void updateTextOpacityLabel(int alpha) {
        if (mTextOpacityLabel == null || getContext() == null) {
            return;
        }
        mTextOpacityLabel.setText(ControlHelper.getPercentValue(
                getContext(), R.string.text_opacity, alpha * 1.0f));
    }

    private static class SimpleZoomWatcher implements TextWatcher {
        private final LabelAttributes mLabelAttributes;
        private final boolean mMinZoom;

        SimpleZoomWatcher(LabelAttributes labelAttributes, boolean minZoom) {
            mLabelAttributes = labelAttributes;
            mMinZoom = minZoom;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            String raw = s.toString().trim();
            if (raw.isEmpty()) {
                if (mMinZoom) {
                    mLabelAttributes.setLabelMinZoom(-1f);
                } else {
                    mLabelAttributes.setLabelMaxZoom(-1f);
                }
                return;
            }
            try {
                float zoom = Float.parseFloat(raw);
                if (mMinZoom) {
                    mLabelAttributes.setLabelMinZoom(zoom);
                } else {
                    mLabelAttributes.setLabelMaxZoom(zoom);
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    protected void setFillColor(int color) {
        setColor(mColorFillImage, mColorFillName, color);
    }

    protected void setStrokeColor(int color) {
        setColor(mColorStrokeImage, mColorStrokeName, color);
    }

    protected void setTextColor(int color) {
        setColor(mColorTextImage, mColorTextName, color);
    }

    protected void setHaloColor(int color) {
        if (mColorTextHaloImage != null && mColorTextHaloName != null) {
            setColor(mColorTextHaloImage, mColorTextHaloName, color);
        }
    }

    private static void setColor(ImageView image, TextView text, int color) {
        // set color
        GradientDrawable sd = (GradientDrawable) image.getDrawable();
        sd.setColor(color);
        image.invalidate();

        // set color name
        text.setText(getColorName(color));
    }

    protected static String getColorName(int color) {
        return String.format("#%06X", (0xFFFFFF & color));
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.color_fill) {//show colors dialog
            AmbilWarnaDialog dialog = new AmbilWarnaDialog(v.getContext(), mFillColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
                @Override
                public void onOk(AmbilWarnaDialog dialog, int color) {
                    mFillColor = color;
                    setFillColor(color);
                    mStyle.setColor(color);
                }

                @Override
                public void onCancel(AmbilWarnaDialog dialog) {

                }
            });

            dialog.show();
        } else if (i == R.id.color_stroke) {//show colors dialog
            AmbilWarnaDialog dialog = new AmbilWarnaDialog(v.getContext(), mStrokeColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
                @Override
                public void onOk(AmbilWarnaDialog dialog, int color) {
                    mStrokeColor = color;
                    setStrokeColor(color);

                    if (mStyle instanceof SimpleMarkerStyle)
                        mStyle.setOutColor(color);
                    else if (mStyle instanceof SimpleLineStyle)
                        mStyle.setOutColor(color);
                    else if (mStyle instanceof SimplePolygonStyle)
                        mStyle.setOutColor(color);
                }

                @Override
                public void onCancel(AmbilWarnaDialog dialog) {

                }
            });

            dialog.show();
        } else if (i == R.id.color_text) {//show colors dialog
            AmbilWarnaDialog dialog = new AmbilWarnaDialog(v.getContext(), mTextColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
                @Override
                public void onOk(AmbilWarnaDialog dialog, int color) {
                    mTextColor = color;
                    setTextColor(color);
                    TextStyleUtil.setTextColor(mStyle, color);
                }

                @Override
                public void onCancel(AmbilWarnaDialog dialog) {

                }
            });

            dialog.show();
        } else if (i == R.id.color_text_halo) {
            AmbilWarnaDialog dialog = new AmbilWarnaDialog(v.getContext(), mTextHaloColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
                @Override
                public void onOk(AmbilWarnaDialog dialog, int color) {
                    mTextHaloColor = color;
                    setHaloColor(color);
                    getLabelAttributes().setTextHaloColor(color);
                }

                @Override
                public void onCancel(AmbilWarnaDialog dialog) {
                }
            });
            dialog.show();
        }
    }

    public void setLayer(VectorLayer layer) {
        mLayer = layer;
    }
}