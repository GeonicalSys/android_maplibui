package com.nextgis.maplibui.util;

import com.nextgis.maplib.datasource.Field;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;

/** Read legacy NGFP pairs as independent controls when the declarative graph owns both fields. */
public final class CascadingFormElements {
    private CascadingFormElements() { }

    public static JSONArray expand(JSONArray elements, List<Field> fields,
                                   CascadingFormController cascades) throws JSONException {
        if (cascades == null) return elements;
        JSONArray result = new JSONArray();
        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.getJSONObject(i);
            JSONObject attributes = element.optJSONObject("attributes");
            if (!"double_combobox".equals(element.optString("type")) || attributes == null
                    || !cascades.manages(attributes.optString("field_level1"))
                    || !cascades.manages(attributes.optString("field_level2"))) {
                result.put(element);
                continue;
            }
            for (String key : new String[]{"field_level1", "field_level2"}) {
                String name = attributes.getString(key), caption = name;
                for (Field field : fields) if (RequiredFieldUi.sameName(field.getName(), name)) {
                    if (field.getAlias() != null && !field.getAlias().isEmpty()) caption = field.getAlias();
                    break;
                }
                result.put(new JSONObject().put("type", "text_label")
                        .put("attributes", new JSONObject().put("text", caption)));
                JSONObject single = new JSONObject(attributes.toString());
                single.remove("field_level1"); single.remove("field_level2");
                single.put("field", name).put("values", new JSONArray());
                result.put(new JSONObject().put("type", "combobox").put("attributes", single));
            }
        }
        return result;
    }
}
