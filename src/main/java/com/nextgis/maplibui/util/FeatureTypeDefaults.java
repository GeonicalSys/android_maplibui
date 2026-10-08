package com.nextgis.maplibui.util;

import android.os.Bundle;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.display.FieldStyleRule;
import com.nextgis.maplib.display.RuleFeatureRenderer;
import com.nextgis.maplib.display.Style;
import com.nextgis.maplib.forms.CascadingLists;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplib.util.GeoConstants;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit creation defaults; remembered form values never override a chosen style category. */
public final class FeatureTypeDefaults {
    public static final String INITIAL_VALUES = "feature_type_initial_values";
    private FeatureTypeDefaults() { }

    public static final class Choice {
        public final String label;
        public final Style style;
        public final Bundle state;
        Choice(String label, Style style, Bundle state) {
            this.label = label; this.style = style; this.state = state;
        }
        @Override public String toString() { return label; }
    }

    public static boolean hasCategories(VectorLayer layer) {
        if (!(layer.getRenderer() instanceof RuleFeatureRenderer)) return false;
        Object rule = ((RuleFeatureRenderer)layer.getRenderer()).getStyleRule();
        if (!(rule instanceof FieldStyleRule)) return false;
        Field field = layer.getFieldByName(((FieldStyleRule)rule).getKey());
        if (field == null || field.getName().equals(com.nextgis.maplib.util.Constants.FIELD_ID)) return false;
        int type = field.getType();
        return (type == GeoConstants.FTString || type == GeoConstants.FTInteger
                || type == GeoConstants.FTLong || type == GeoConstants.FTReal)
                && ((FieldStyleRule)rule).getStyleRules().keySet().stream().anyMatch(key -> key != null && !key.isEmpty());
    }

    public static List<Choice> choices(VectorLayer layer) throws Exception {
        if (!hasCategories(layer)) return Collections.emptyList();
        FieldStyleRule styleRule = (FieldStyleRule)((RuleFeatureRenderer)layer.getRenderer()).getStyleRule();
        String field = styleRule.getKey();
        File[] files = LayerUtil.formFiles(layer, -1);
        List<JSONObject> elements = new ArrayList<>();
        if (files[0].isFile()) {
            String text = FileUtil.readFromFile(files[0]);
            if (text.length() > 4 * 1024 * 1024) throw new IllegalArgumentException("Form too large");
            collect(new JSONTokener(text).nextValue(), elements, 0);
        }
        JSONObject meta = files[1].isFile() ? new JSONObject(FileUtil.readFromFile(files[1])) : new JSONObject();
        String definition = meta.has(CascadingLists.META_KEY) ? meta.getJSONObject(CascadingLists.META_KEY).toString() : "";
        if (definition.length() > 4 * 1024 * 1024) throw new IllegalArgumentException("List metadata too large");
        CascadingLists cascades = definition.isEmpty() ? null : new CascadingLists(new JSONObject(definition));
        String pin = cascades != null ? FormMetadataSnapshot.persist(layer.getPath(), "form_dependencies",
                definition, 16 * 1024 * 1024) : null;
        List<Choice> result = new ArrayList<>();
        for (Map.Entry<String, Style> entry : styleRule.getStyleRules().entrySet()) {
            String value = entry.getKey();
            if (value == null || value.isEmpty()) continue; // catch-all is not a concrete type.
            Style symbol = styleRule.resolveEffectiveStyle(value, layer.getDefaultStyleNoExcept());
            if (cascades != null && cascades.manages(field)) {
                List<CascadingLists.InitialSelection> selections = cascades.initialSelections(field, value, styleRule.isKeyIgnoreCase());
                for (CascadingLists.InitialSelection selection : selections) {
                    Bundle state = state(selection.values);
                    for (String name : cascades.fields()) {
                        Field schema = layer.getFieldByName(name);
                        if (schema == null || schema.getType() != GeoConstants.FTString)
                            throw new IllegalArgumentException("Invalid cascading field");
                        if (!selection.values.containsKey(name)) state.putString(ControlHelper.getSavedStateKey(name), null);
                    }
                    state.putString(CascadingFormController.PIN, pin);
                    state.putString(CascadingFormController.KEYS, new JSONObject(selection.keys).toString());
                    state.putString(CascadingFormController.ORIGINAL, "{}");
                    state.putString(CascadingFormController.FIELDS, new JSONArray(cascades.fields()).toString());
                    String label = selection.labels.get(field);
                    if (selections.size() > 1) {
                        List<String> parents = new ArrayList<>();
                        for (String name : selection.labels.keySet()) if (!name.equals(field)) parents.add(selection.labels.get(name));
                        label += " — " + android.text.TextUtils.join(" / ", parents);
                    }
                    result.add(new Choice(label, symbol, state));
                }
                if (selections.isEmpty()) result.add(new Choice(value, symbol, null));
                continue;
            }
            List<Choice> legacy = legacyChoices(layer, elements, styleRule, field, value, symbol);
            if (legacy != null) { result.addAll(legacy); continue; }
            result.add(new Choice(value, symbol, state(Collections.singletonMap(field, value))));
        }
        return result;
    }

    /** null means this field has no legacy selector; an invalid category remains unselectable. */
    static List<Choice> legacyChoices(VectorLayer layer, List<JSONObject> elements, FieldStyleRule rule, String field,
            String value, Style style) throws Exception {
        for (JSONObject element : elements) {
            JSONObject attributes = element.optJSONObject("attributes");
            if (attributes == null) continue;
            String type = element.optString("type");
            boolean pair = "double_combobox".equals(type);
            if (!pair && !"combobox".equals(type)) continue;
            String parentName = attributes.optString(pair ? "field_level1" : "field");
            String childName = pair ? attributes.optString("field_level2") : "";
            if (parentName.isEmpty() || (pair && childName.isEmpty())) continue;
            String parent = ControlHelper.getFieldName(parentName);
            String child = pair ? ControlHelper.getFieldName(childName) : null;
            if (!field.equals(parent) && !field.equals(child)) continue;
            List<Choice> matches = new ArrayList<>();
            if (!pair && attributes.optLong("ngw_id", -1) != -1) {
                com.nextgis.maplib.map.MapBase map = com.nextgis.maplib.map.MapBase.getInstance();
                String account = layer instanceof com.nextgis.maplib.map.NGWVectorLayer
                        ? ((com.nextgis.maplib.map.NGWVectorLayer)layer).getAccountName() : element.optString("account_name");
                if (map != null) for (int i = 0; i < map.getLayerCount(); i++) {
                    if (!(map.getLayer(i) instanceof com.nextgis.maplib.map.NGWLookupTable)) continue;
                    com.nextgis.maplib.map.NGWLookupTable table = (com.nextgis.maplib.map.NGWLookupTable)map.getLayer(i);
                    if (table.getRemoteId() != attributes.getLong("ngw_id") || !table.getAccountName().equals(account)) continue;
                    for (Map.Entry<String,String> row : table.getData().entrySet())
                        if (rule.normalizeKey(row.getKey()).equals(value)) matches.add(new Choice(row.getValue(),style,
                                state(Collections.singletonMap(field,row.getKey()))));
                    break;
                }
                if (matches.isEmpty()) matches.add(new Choice(value,style,null));
                return matches;
            }
            JSONArray values = attributes.optJSONArray("values");
            if (values == null) continue;
            for (int i = 0; i < values.length(); i++) {
                JSONObject item = values.getJSONObject(i);
                if (field.equals(parent) && rule.normalizeKey(item.getString("name")).equals(value)) {
                    Map<String,String> defaults = new LinkedHashMap<>(); defaults.put(parent,item.getString("name"));
                    if (pair) defaults.put(child, null);
                    matches.add(new Choice(item.optString("alias", value), style, state(defaults)));
                }
                if (!pair || !field.equals(child)) continue;
                JSONArray children = item.getJSONArray("values");
                for (int j = 0; j < children.length(); j++) {
                    JSONObject sub = children.getJSONObject(j);
                    if (!rule.normalizeKey(sub.getString("name")).equals(value)) continue;
                    Map<String,String> defaults = new LinkedHashMap<>();
                    defaults.put(parent,item.getString("name")); defaults.put(child,sub.getString("name"));
                    matches.add(new Choice(sub.optString("alias", value) + " — " + item.optString("alias", item.getString("name")),
                            style, state(defaults)));
                }
            }
            if (matches.isEmpty()) matches.add(new Choice(value, style, null));
            return matches;
        }
        return null;
    }

    private static Bundle state(Map<String, String> values) {
        Bundle result = new Bundle();
        for (Map.Entry<String,String> value : values.entrySet())
            result.putString(ControlHelper.getSavedStateKey(value.getKey()), value.getValue());
        return result;
    }

    private static void collect(Object json, List<JSONObject> result, int depth) throws Exception {
        if (depth > 64) throw new IllegalArgumentException("Form nesting too deep");
        if (json instanceof JSONArray) {
            JSONArray array = (JSONArray)json;
            for (int i = 0; i < array.length(); i++) collect(array.get(i), result, depth + 1);
        } else if (json instanceof JSONObject) {
            JSONObject object = (JSONObject)json;
            if (object.has("type")) result.add(object);
            java.util.Iterator<String> names = object.keys();
            while (names.hasNext()) collect(object.get(names.next()), result, depth + 1);
        }
    }

    public static JSONObject encode(Bundle state) {
        FeatureFormDraftStore.Snapshot draft = new FeatureFormDraftStore.Snapshot();
        FeatureFormDraftStore.putControlStateFromBundle(draft, state);
        return draft.controlState;
    }
    public static Bundle decode(JSONObject state) {
        if (state == null) return null;
        FeatureFormDraftStore.Snapshot draft = new FeatureFormDraftStore.Snapshot(); draft.controlState = state;
        return FeatureFormDraftStore.controlStateToBundle(draft);
    }
}
