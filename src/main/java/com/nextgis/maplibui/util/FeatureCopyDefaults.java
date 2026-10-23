package com.nextgis.maplibui.util;

import android.content.ContentValues;
import android.os.Bundle;
import com.nextgis.maplib.datasource.Feature;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.util.FeatureCopyPolicy;
import com.nextgis.maplib.util.FileUtil;
import org.json.*;
import java.io.File;
import java.util.*;
import static com.nextgis.maplib.util.GeoConstants.*;

/** Initial control values plus typed values for matching fields absent from the destination form. */
public final class FeatureCopyDefaults {
    public static final String VALUES = "feature_copy_values";
    public static final String BASELINE = "feature_copy_empty_controls";
    private FeatureCopyDefaults() { }

    public static Bundle create(Feature source, VectorLayer target, Bundle category) throws Exception {
        Map<String, Object> copied = FeatureCopyPolicy.attributes(source, target.getFields());
        Bundle state = new Bundle();
        JSONObject raw = new JSONObject();
        for (Field field : target.getFields()) {
            String name = field.getName();
            if (!copied.containsKey(name)) continue;
            Object value = copied.get(name);
            if (value instanceof Date) value = ((Date) value).getTime();
            if (value instanceof Calendar) value = ((Calendar) value).getTimeInMillis();
            raw.put(name, value == null ? JSONObject.NULL : value);
            String key = ControlHelper.getSavedStateKey(name);
            if (value != null && isDate(field.getType())) state.putLong(key, ((Number) value).longValue());
            else state.putString(key, value == null ? null : value.toString());
        }
        if (category != null) {
            state.putAll(category);
            // Explicit destination categories (including their parents/cleared children) win.
            for (Field field : target.getFields()) {
                String key = ControlHelper.getSavedStateKey(field.getName());
                if (category.containsKey(key)) raw.put(field.getName(),
                        category.get(key) == null ? JSONObject.NULL : category.get(key));
            }
        }
        File form = LayerUtil.formFiles(target, -1)[0];
        if (form.isFile()) {
            String text = FileUtil.readFromFile(form);
            if (text.length() > 4 * 1024 * 1024) throw new IllegalArgumentException("Form too large");
            adapt(new JSONTokener(text).nextValue(), state, raw, 0);
        }
        state.putString(VALUES, raw.toString());
        return state;
    }

    private static boolean isDate(int type) { return type == FTDate || type == FTTime || type == FTDateTime; }

    private static void adapt(Object object, Bundle state, JSONObject raw, int depth) throws JSONException {
        if (depth > 64) throw new JSONException("Form nesting too deep");
        if (object instanceof JSONArray) {
            JSONArray array = (JSONArray) object;
            for (int i=0; i<array.length(); i++) adapt(array.get(i), state, raw, depth+1);
        } else if (object instanceof JSONObject) {
            JSONObject element = (JSONObject) object;
            JSONObject attributes = element.optJSONObject("attributes");
            if (attributes != null && attributes.has("field")) {
                String field = ControlHelper.getFieldName(attributes.optString("field"));
                String key = ControlHelper.getSavedStateKey(field);
                String type = element.optString("type");
                if (raw.has(field) && ("checkbox".equals(type) || "counter".equals(type) || "averaging".equals(type))) {
                    Object value = raw.opt(field);
                    Number number = value == JSONObject.NULL || "".equals(value) ? 0 : value instanceof Number ? (Number) value
                            : new java.math.BigDecimal(value.toString());
                    switch (type) {
                        case "checkbox": state.putBoolean(key, number.doubleValue() != 0); break;
                        case "counter": state.putLong(key, number.longValue()); break;
                        case "averaging": state.putDouble(key, number.doubleValue()); break;
                    }
                }
            }
            Iterator<String> keys = element.keys();
            while (keys.hasNext()) adapt(element.get(keys.next()), state, raw, depth+1);
        }
    }

    public static ContentValues values(String encoded, List<Field> fields) {
        ContentValues result = new ContentValues();
        if (encoded == null) return result;
        try {
            JSONObject raw = new JSONObject(encoded);
            for (Field field : fields) {
                String name = field.getName();
                if (!raw.has(name) || com.nextgis.maplib.util.Constants.FIELD_ID.equals(name)
                        || com.nextgis.maplib.util.Constants.FIELD_GEOM.equals(name)) continue;
                if (raw.isNull(name)) { result.putNull(name); continue; }
                switch (field.getType()) {
                    case FTString: result.put(name, raw.getString(name)); break;
                    case FTInteger: result.put(name, raw.getInt(name)); break;
                    case FTLong: case FTDate: case FTTime: case FTDateTime:
                        result.put(name, raw.getLong(name)); break;
                    case FTReal: result.put(name, raw.getDouble(name)); break;
                }
            }
        } catch (JSONException error) { throw new IllegalArgumentException("Invalid copied values", error); }
        return result;
    }

    /** A checkbox/counter can display an empty database value as zero; retain it until edited. */
    public static String baseline(Map<String,Object> controls) {
        JSONObject result = new JSONObject();
        try {
            for (Map.Entry<String,Object> entry : controls.entrySet()) {
                Object value = entry.getValue();
                if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean)
                    result.put(entry.getKey(), value == null ? JSONObject.NULL : value.getClass().getName()+":"+value);
            }
            return result.toString();
        } catch (JSONException error) { throw new IllegalArgumentException(error); }
    }

    public static boolean retainEmpty(ContentValues values, String copied, String baseline, String name, Object current) {
        if (copied == null || baseline == null) return false;
        try {
            JSONObject raw = new JSONObject(copied), initial = new JSONObject(baseline);
            if (!raw.has(name) || !initial.has(name)) return false;
            Object value = raw.get(name);
            if (value != JSONObject.NULL && !"".equals(value)) return false;
            String now = current == null ? null : current.getClass().getName()+":"+current;
            String before = initial.isNull(name) ? null : initial.getString(name);
            if (!Objects.equals(now,before)) return false;
            if (value == JSONObject.NULL) values.putNull(name); else values.put(name,"");
            return true;
        } catch (JSONException error) { throw new IllegalArgumentException("Invalid copy baseline",error); }
    }
}
