package com.nextgis.maplibui.util;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.forms.ConditionalRequiredRules;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.api.IControl;
import com.nextgis.maplibui.formcontrol.Tabs;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Visibility, markers and Save share pinned rules and current scalar values, including inactive tabs. */
public final class ConditionalRequiredController implements AutoCloseable {
    public static final String PIN="lisa_required_pin";
    private final ConditionalRequiredRules rules;
    private final VectorLayer layer;
    private final ContentValues original=new ContentValues();
    private final String pin;
    private View root;
    private ViewTreeObserver.OnPreDrawListener observer;
    private final Map<String, View> fieldTargets = new LinkedHashMap<>();
    private final Map<String, View> elementTargets = new LinkedHashMap<>();
    private final Map<View, Integer> originalVisibility = new LinkedHashMap<>();
    private Map<String, IControl> controls;

    public static ConditionalRequiredController load(File meta,VectorLayer layer,long featureId,Bundle saved)
            throws IOException,JSONException {
        String definition;
        if (saved!=null && saved.containsKey(PIN)) {
            String savedPin=saved.getString(PIN,"");
            definition=savedPin.isEmpty()?"":FormMetadataSnapshot.read(layer.getPath(),"form_rules",savedPin,ConditionalRequiredRules.MAX_BYTES);
        } else {
            JSONObject metadata=meta!=null&&meta.isFile()?new JSONObject(FileUtil.readFromFile(meta)):new JSONObject();
            definition=metadata.has(ConditionalRequiredRules.META_KEY)?metadata.getJSONObject(ConditionalRequiredRules.META_KEY).toString():"";
        }
        return definition.isEmpty()?null:new ConditionalRequiredController(layer,featureId,definition);
    }
    private ConditionalRequiredController(VectorLayer layer,long featureId,String definition) throws IOException,JSONException {
        if (definition.length()>ConditionalRequiredRules.MAX_BYTES) throw new JSONException("Form rules exceed limit");
        this.layer=layer;rules=new ConditionalRequiredRules(new JSONObject(definition));
        for (String target:rules.targets()) checkField(target);
        for (String target:rules.visibilityFields()) checkField(target);
        for (String reference:rules.references()) checkField(reference);
        if (featureId!=Constants.NOT_FOUND&&!rules.references().isEmpty()) {
            try (Cursor cursor=layer.query(rules.references().toArray(new String[0]),Constants.FIELD_ID+" = ?",
                    new String[]{Long.toString(featureId)},null,null)) {
                if (cursor!=null&&cursor.moveToFirst()) DatabaseUtils.cursorRowToContentValues(cursor,original);
            }
        }
        pin=FormMetadataSnapshot.persist(layer.getPath(),"form_rules",definition,ConditionalRequiredRules.MAX_BYTES);
    }
    private void checkField(String name) throws JSONException {
        if (layer.getFieldByName(name)==null) throw new JSONException("Unknown conditional field: "+name);
    }
    private Object value(ContentValues current,String name) {
        return current.containsKey(name)?current.get(name):original.get(name);
    }
    public List<Field> effectiveFields(ContentValues current) {
        Set<String> required=rules.required(name -> value(current,name));
        Map<View, Boolean> visibility = visibility(current);
        applyVisibility(visibility);
        List<Field> fields=new ArrayList<>();
        for (Field field:layer.getFields()) fields.add(new Field(field.getType(),field.getName(),label(field.getName(),field.getAlias()),
                !hidden(field, visibility) && (field.isRequired()||required.contains(field.getName()))));
        return fields;
    }
    public String label(String field, String fallback) { return rules.label(field, fallback); }
    public boolean isHidden(String name, ContentValues current) {
        Field field = layer.getFieldByName(name);
        return field != null && hidden(field, visibility(current));
    }
    private boolean hidden(Field field, Map<View, Boolean> visibility) {
        View view = controls == null ? null : RequiredFieldUi.fieldView(field, controls);
        if (view == null) return false;
        for (Map.Entry<View, Boolean> entry : visibility.entrySet())
            if (!entry.getValue() && RequiredFieldUi.containsView(entry.getKey(), view)) return true;
        return false;
    }
    private Map<View, Boolean> visibility(ContentValues current) {
        Map<View, Boolean> result = new LinkedHashMap<>();
        rules.visibleFields(name -> value(current,name)).forEach((name, visible) -> {
            View view = fieldTargets.get(name);
            if (view != null) result.merge(view, visible, (left, right) -> left && right);
        });
        rules.visibleElements(name -> value(current,name)).forEach((name, visible) -> {
            View view = elementTargets.get(name);
            if (view != null) result.merge(view, visible, (left, right) -> left && right);
        });
        return result;
    }
    private void applyVisibility(Map<View, Boolean> visibility) {
        for (Map.Entry<View, Boolean> entry : visibility.entrySet()) {
            View view = entry.getKey();
            int desired = entry.getValue() ? originalVisibility.get(view) : View.GONE;
            if (view.getVisibility() != desired) {
                if (desired == View.GONE) {
                    if (view.hasFocus()) view.clearFocus();
                    if (view instanceof FormFieldLayout.FieldContainer) ((FormFieldLayout.FieldContainer) view).setError(null);
                }
                view.setVisibility(desired);
            }
            if (view instanceof Tabs) ((Tabs) view).setRuleVisible(desired == View.VISIBLE);
        }
    }
    public void saveState(Bundle state) { state.putString(PIN,pin); }
    public void attach(View root,Map<String,IControl> controls,Supplier<ContentValues> capture,Runnable changed) throws JSONException {
        this.root=root;
        this.controls=controls;
        Map<String, View> elements = new HashMap<>();
        collectElements(root, elements, new HashSet<>());
        for (String field : rules.visibilityFields()) {
            View view = RequiredFieldUi.fieldView(layer.getFieldByName(field), controls);
            if (view == null) throw new JSONException("Visibility target is absent from form: " + field);
            view = FormFieldLayout.visibilityUnit(view);
            fieldTargets.put(field, view);
            originalVisibility.put(view, view.getVisibility());
        }
        for (String name : rules.visibilityElements()) {
            View view = elements.get(name);
            if (view == null) throw new JSONException("Unknown visibility element: " + name);
            elementTargets.put(name, view);
            originalVisibility.put(view, view.getVisibility());
        }
        changed.run();
        observer=new ViewTreeObserver.OnPreDrawListener() {
            private Map<String,Object> previous;
            @Override public boolean onPreDraw() {
                ContentValues current=capture.get();Map<String,Object> values=new HashMap<>();
                for (String reference:rules.references()) values.put(reference,value(current,reference));
                if (!values.equals(previous)) { previous=values;changed.run(); }
                return true;
            }
        };
        root.getViewTreeObserver().addOnPreDrawListener(observer);
    }
    private static void collectElements(View view, Map<String, View> elements, Set<View> visited) throws JSONException {
        if (view == null || !visited.add(view)) return;
        Object id = view.getTag(R.id.form_element_id);
        if (id != null && elements.put(id.toString(), view) != null) throw new JSONException("Repeated form element lisa_id");
        if (view instanceof Tabs) for (View page : ((Tabs) view).getPageLayouts()) collectElements(page, elements, visited);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectElements(group.getChildAt(i), elements, visited);
        }
    }
    @Override public void close() {
        if (observer!=null&&root!=null&&root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(observer);
        root=null;observer=null;
    }
}
