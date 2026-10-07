package com.nextgis.maplibui.util;

import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.forms.CascadingLists;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.activity.ModifyAttributesActivity;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One controller per open NGFP, with one pinned data snapshot for Bundle and durable drafts. */
public final class CascadingFormController {
    public static final String PIN = "lisa_cascade_pin", KEYS = "lisa_cascade_keys",
            ORIGINAL = "lisa_cascade_original", FIELDS = "lisa_cascade_fields";
    private final ModifyAttributesActivity activity;
    private final CascadingLists model;
    private final String definition;
    private final String pin;
    private final Map<String, Spinner> spinners = new LinkedHashMap<>();
    private final Map<String, Boolean> enabled = new LinkedHashMap<>();
    private boolean attached;

    public static CascadingFormController load(ModifyAttributesActivity activity, File meta,
            VectorLayer layer, long featureId, Bundle saved) throws IOException, JSONException {
        String definition;
        if (saved != null && saved.containsKey(PIN)) {
            definition = saved.getString(PIN, "");
            if (definition.startsWith("sha256:")) {
                definition = FormMetadataSnapshot.read(layer.getPath(), "form_dependencies", definition, 16 * 1024 * 1024);
            }
        }
        else {
            JSONObject json = meta != null && meta.isFile()
                    ? new JSONObject(FileUtil.readFromFile(meta)) : new JSONObject();
            definition = json.has(CascadingLists.META_KEY)
                    ? json.getJSONObject(CascadingLists.META_KEY).toString() : "";
        }
        if (definition.isEmpty()) return null;
        if (definition.length() > 4 * 1024 * 1024) throw new JSONException("List metadata is too large");
        CascadingFormController controller = new CascadingFormController(activity, layer, featureId, saved, definition);
        controller.persistSnapshot(layer);
        return controller;
    }

    private CascadingFormController(ModifyAttributesActivity activity, VectorLayer layer,
            long featureId, Bundle saved, String definition) throws JSONException {
        this.activity = activity; this.definition = definition;
        pin = "sha256:" + FormMetadataSnapshot.hash(definition.getBytes(StandardCharsets.UTF_8));
        model = new CascadingLists(new JSONObject(definition));
        Map<String, String> initial = new LinkedHashMap<>(), original = new LinkedHashMap<>();
        Cursor cursor = featureId != Constants.NOT_FOUND ? layer.query(null,
                Constants.FIELD_ID + " = ?", new String[]{Long.toString(featureId)}, null, null) : null;
        try {
            boolean existing = cursor != null && cursor.moveToFirst();
            for (String name : model.fields()) {
                Field field = layer.getFieldByName(name);
                if (field == null || field.getType() != GeoConstants.FTString)
                    throw new JSONException("Cascading list needs an existing STRING field: " + name);
                String value = null;
                if (existing) {
                    int column = cursor.getColumnIndex(name);
                    if (column < 0) throw new JSONException("Missing physical list field: " + name);
                    value = cursor.isNull(column) ? null : cursor.getString(column);
                    original.put(name, CascadingLists.isMissing(value) ? null : value);
                }
                if (saved != null && ControlHelper.hasKey(saved, name))
                    value = saved.getString(ControlHelper.getSavedStateKey(name));
                initial.put(name, value);
            }
        } finally { if (cursor != null) cursor.close(); }
        if (saved != null && saved.containsKey(ORIGINAL)) original = decode(saved.getString(ORIGINAL));
        model.restore(initial, saved != null ? decode(saved.getString(KEYS)) : new LinkedHashMap<>(), original);
    }

    public boolean manages(String field) { return model.manages(field); }
    public String value(String field) { return model.value(field); }

    public void register(String field, Spinner spinner) throws JSONException {
        if (!manages(field) || spinners.put(field, spinner) != null)
            throw new JSONException("Repeated or unmanaged list control: " + field);
        enabled.put(field, spinner.isEnabled());
    }

    public void attach(boolean viewOnly) throws JSONException {
        if (attached) return;
        if (spinners.size() != model.fields().size()) throw new JSONException("A cascading field has no supported control");
        for (String field : model.fields()) {
            if (viewOnly) enabled.put(field, false);
            Spinner spinner = spinners.get(field);
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Object item = spinner.getSelectedItem();
                    if (!(item instanceof Choice)) return;
                    Choice choice = (Choice)item;
                    // Android dispatches adapter/selection notifications after render returns.
                    if (choice.historical || (Objects.equals(choice.key, model.key(field))
                            && (choice.key != null || model.value(field) == null))) return;
                    model.select(field, choice.key);
                    render();
                }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });
        }
        attached = true;
        render();
    }

    private void render() {
        for (String field : model.fields()) {
            Spinner spinner = spinners.get(field);
            List<CascadingLists.Option> options = model.options(field);
            List<Choice> choices = new ArrayList<>();
            int hint = !model.ready(field) ? R.string.form_cascade_choose_parents
                    : options.isEmpty() ? R.string.form_cascade_empty : R.string.form_cascade_choose;
            choices.add(new Choice(null, activity.getString(hint), false));
            int selected = 0;
            for (CascadingLists.Option option : options) {
                choices.add(new Choice(option.key, option.label, false));
                if (option.key.equals(model.key(field))) selected = choices.size() - 1;
            }
            if (selected == 0 && model.value(field) != null) {
                choices.add(new Choice(null, activity.getString(R.string.form_cascade_saved,
                        model.value(field)), true));
                selected = choices.size() - 1;
            }
            ArrayAdapter<Choice> adapter = new ArrayAdapter<>(activity,
                    R.layout.formtemplate_double_spinner, choices);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setSelection(selected);
            spinner.setEnabled(Boolean.TRUE.equals(enabled.get(field)) && model.ready(field) && !options.isEmpty());
        }
    }

    public List<String> invalidFields() { return model.invalidFields(); }

    public void saveState(Bundle state) {
        state.putString(PIN, pin);
        state.putString(KEYS, new JSONObject(model.selectionKeys()).toString());
        state.putString(ORIGINAL, new JSONObject(model.originalValues()).toString());
        state.putString(FIELDS, new JSONArray(model.fields()).toString());
        for (String field : model.fields()) state.putString(ControlHelper.getSavedStateKey(field), model.value(field));
    }

    /** Retain pinned selections if the snapshot cannot be loaded; Save stays blocked. */
    public static void retainPinnedState(Bundle saved, Bundle state, VectorLayer layer) {
        for (String key : new String[]{PIN, KEYS, ORIGINAL, FIELDS})
            if (saved.containsKey(key)) state.putString(key, saved.getString(key));
        java.util.Set<String> fields = null;
        if (saved.containsKey(FIELDS)) {
            try {
                JSONArray names = new JSONArray(saved.getString(FIELDS));
                fields = new java.util.HashSet<>();
                for (int i = 0; i < names.length(); i++) fields.add(names.getString(i));
            } catch (JSONException | RuntimeException ignored) { fields = null; }
        }
        for (Field field : layer.getFields()) {
            if (fields != null && !fields.contains(field.getName())) continue;
            String key = ControlHelper.getSavedStateKey(field.getName());
            if (saved.containsKey(key)) {
                Object value = saved.get(key);
                if (value == null || value instanceof String) state.putString(key, (String)value);
            }
        }
    }

    private void persistSnapshot(VectorLayer layer) throws IOException {
        FormMetadataSnapshot.persist(layer.getPath(), "form_dependencies", definition, 16 * 1024 * 1024);
    }

    private static Map<String, String> decode(String text) throws JSONException {
        Map<String, String> result = new LinkedHashMap<>();
        if (text == null) return result;
        JSONObject json = new JSONObject(text);
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            result.put(key, json.isNull(key) ? null : json.getString(key));
        }
        return result;
    }

    private static final class Choice {
        final String key, label;
        final boolean historical;
        Choice(String key, String label, boolean historical) {
            this.key = key; this.label = label; this.historical = historical;
        }
        @Override public String toString() { return label; }
    }
}
