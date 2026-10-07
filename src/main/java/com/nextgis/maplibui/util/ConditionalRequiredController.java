package com.nextgis.maplibui.util;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.forms.ConditionalRequiredRules;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Dynamic markers and requirements share the same pinned rules and current scalar values. */
public final class ConditionalRequiredController implements AutoCloseable {
    public static final String PIN="lisa_required_pin";
    private final ConditionalRequiredRules rules;
    private final VectorLayer layer;
    private final ContentValues original=new ContentValues();
    private final String pin;
    private View root;
    private ViewTreeObserver.OnPreDrawListener observer;

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
        List<Field> fields=new ArrayList<>();
        for (Field field:layer.getFields()) fields.add(new Field(field.getType(),field.getName(),rules.label(field.getName(),field.getAlias()),
                field.isRequired()||required.contains(field.getName())));
        return fields;
    }
    public void saveState(Bundle state) { state.putString(PIN,pin); }
    public void attach(View root,Supplier<ContentValues> capture,Runnable changed) {
        this.root=root;
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
    @Override public void close() {
        if (root!=null&&root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(observer);
        root=null;observer=null;
    }
}
