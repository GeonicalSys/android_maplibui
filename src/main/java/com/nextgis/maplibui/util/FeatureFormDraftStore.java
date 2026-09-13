package com.nextgis.maplibui.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.datasource.GeoGeometry;
import com.nextgis.maplib.datasource.GeoGeometryFactory;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Crash-safe unfinished attribute-form draft.
 * Process memory and Activity Bundle do not survive process death;
 * this journal keeps field control state, geometry, and form meta
 * in app-private SharedPreferences.
 */
public final class FeatureFormDraftStore {
    private static final String TAG = "FeatureFormDraftStore";
    private static final String PREFS = "feature_form_draft";
    private static final String KEY_STATE = "state_json";
    private static final int VERSION = 1;
    private static final String BUNDLE_TYPE = "__bundle_type";
    private static final String BUNDLE_VALUE = "value";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_INT = "int";
    private static final String TYPE_LONG = "long";
    private static final String TYPE_DOUBLE = "double";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INT_LIST = "int_list";
    private static final String TYPE_STRING_LIST = "string_list";

    public static final String KEY_APPLY_FORM_DRAFT = "apply_form_draft";

    private FeatureFormDraftStore() {
    }

    public static final class Snapshot {
        public int layerId = Constants.NOT_FOUND;
        public long featureId = Constants.NOT_FOUND;
        public boolean geometryChanged;
        /** WKT in CRS_WEB_MERCATOR, or null */
        public String geometryWkt;
        /** Absolute form.json path when custom form was used */
        public String formPath;
        public String metaPath;
        /** Session identities prevent a restored/late form from unlocking another walk. */
        public String pointSessionId;
        public String walkSessionId;
        /** Control saved-state bundle encoded as JSON */
        public JSONObject controlState = new JSONObject();
        /** Pending local photo paths/URIs (AttachInfo.oldAttachString) */
        public List<String> photoPaths = new ArrayList<>();
        public long updatedAtMs;

        public boolean isValid() {
            return layerId != Constants.NOT_FOUND && controlState != null && photoPaths != null;
        }
    }

    public static boolean save(Context context, Snapshot snapshot) {
        if (context == null || snapshot == null || !snapshot.isValid()) {
            return false;
        }
        try {
            snapshot.updatedAtMs = System.currentTimeMillis();
            boolean committed = preferences(context).edit()
                    .putString(KEY_STATE, encode(snapshot).toString())
                    .commit();
            if (!committed) {
                Log.e(TAG, "Failed to commit form draft");
                HyperLog.e(Constants.TAG, "FormDraft commit failed layer=" + snapshot.layerId
                        + " feature=" + snapshot.featureId);
            } else {
                HyperLog.v(Constants.TAG, "FormDraft saved layer=" + snapshot.layerId
                        + " feature=" + snapshot.featureId
                        + " controls=" + snapshot.controlState.length()
                        + " photos=" + snapshot.photoPaths.size()
                        + " geometry=" + (snapshot.geometryWkt != null));
            }
            return committed;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to encode form draft", e);
            HyperLog.e(Constants.TAG, "FormDraft encode failed: " + e.getMessage(), e);
            return false;
        }
    }

    public static Snapshot load(Context context) {
        if (context == null) {
            return null;
        }
        String value = preferences(context).getString(KEY_STATE, null);
        if (value == null || value.length() == 0) {
            return null;
        }
        try {
            Snapshot snapshot = decode(new JSONObject(value));
            if (snapshot == null || !snapshot.isValid()) {
                Log.e(TAG, "Invalid form draft; discarding");
                HyperLog.w(Constants.TAG, "FormDraft invalid; discarding");
                clear(context);
                return null;
            }
            HyperLog.v(Constants.TAG, "FormDraft loaded layer=" + snapshot.layerId
                    + " feature=" + snapshot.featureId
                    + " ageMs=" + Math.max(0L, System.currentTimeMillis() - snapshot.updatedAtMs)
                    + " controls=" + snapshot.controlState.length()
                    + " photos=" + snapshot.photoPaths.size());
            return snapshot;
        } catch (JSONException e) {
            Log.e(TAG, "Corrupt form draft; discarding", e);
            HyperLog.e(Constants.TAG, "FormDraft corrupt; discarding: " + e.getMessage(), e);
            clear(context);
            return null;
        }
    }

    public static boolean hasAnyDraft(Context context) {
        return load(context) != null;
    }

    public static boolean hasDraft(Context context, int layerId, long featureId) {
        Snapshot snapshot = load(context);
        return snapshot != null
                && snapshot.layerId == layerId
                && snapshot.featureId == featureId;
    }

    public static void clear(Context context) {
        if (context != null) {
            boolean existed = preferences(context).contains(KEY_STATE);
            if (!preferences(context).edit().remove(KEY_STATE).commit()) {
                Log.e(TAG, "Failed to clear form draft");
                HyperLog.e(Constants.TAG, "FormDraft clear failed");
            } else if (existed) {
                HyperLog.v(Constants.TAG, "FormDraft cleared");
            }
        }
    }

    public static void discard(Context context) {
        Snapshot snapshot = load(context);
        clear(context);
        if (snapshot != null) WalkSessionStore.endPoint(context, snapshot.pointSessionId);
    }

    public static GeoGeometry geometryFromSnapshot(Snapshot snapshot) {
        if (snapshot == null || snapshot.geometryWkt == null || snapshot.geometryWkt.length() == 0) {
            return null;
        }
        return GeoGeometryFactory.fromWKT(snapshot.geometryWkt, GeoConstants.CRS_WEB_MERCATOR);
    }

    public static Bundle controlStateToBundle(Snapshot snapshot) {
        Bundle bundle = new Bundle();
        if (snapshot == null || snapshot.controlState == null) {
            return bundle;
        }
        Iterator<String> keys = snapshot.controlState.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                Object value = snapshot.controlState.get(key);
                if (value == null || value == JSONObject.NULL) {
                    continue;
                }
                if (value instanceof JSONObject
                        && ((JSONObject) value).has(BUNDLE_TYPE)) {
                    putTypedBundleValue(bundle, key, (JSONObject) value);
                    continue;
                }
                // Backward compatibility for drafts written before typed Bundle encoding.
                if (value instanceof Boolean) {
                    bundle.putBoolean(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    bundle.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    bundle.putLong(key, (Long) value);
                } else if (value instanceof Double) {
                    bundle.putDouble(key, (Double) value);
                } else if (value instanceof JSONArray) {
                    JSONArray array = (JSONArray) value;
                    ArrayList<String> strings = new ArrayList<>();
                    ArrayList<Integer> ints = new ArrayList<>();
                    boolean allInt = true;
                    boolean allString = true;
                    for (int i = 0; i < array.length(); i++) {
                        Object item = array.opt(i);
                        if (item instanceof Integer) {
                            ints.add((Integer) item);
                            allString = false;
                        } else if (item instanceof Number && !(item instanceof Double)) {
                            ints.add(((Number) item).intValue());
                            allString = false;
                        } else {
                            allInt = false;
                            strings.add(item == null || item == JSONObject.NULL ? null : String.valueOf(item));
                        }
                    }
                    if (allInt) {
                        bundle.putIntegerArrayList(key, ints);
                    } else if (allString) {
                        bundle.putStringArrayList(key, strings);
                    }
                } else {
                    bundle.putString(key, String.valueOf(value));
                }
            } catch (JSONException ignored) {
            }
        }
        return bundle;
    }

    private static void putTypedBundleValue(Bundle bundle, String key, JSONObject encoded)
            throws JSONException {
        String type = encoded.getString(BUNDLE_TYPE);
        switch (type) {
            case TYPE_BOOLEAN:
                bundle.putBoolean(key, encoded.getBoolean(BUNDLE_VALUE));
                break;
            case TYPE_INT:
                bundle.putInt(key, encoded.getInt(BUNDLE_VALUE));
                break;
            case TYPE_LONG:
                bundle.putLong(key, encoded.getLong(BUNDLE_VALUE));
                break;
            case TYPE_DOUBLE:
                bundle.putDouble(key, encoded.getDouble(BUNDLE_VALUE));
                break;
            case TYPE_STRING:
                bundle.putString(key, encoded.isNull(BUNDLE_VALUE)
                        ? null : encoded.getString(BUNDLE_VALUE));
                break;
            case TYPE_INT_LIST: {
                JSONArray array = encoded.getJSONArray(BUNDLE_VALUE);
                ArrayList<Integer> values = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    values.add(array.getInt(i));
                }
                bundle.putIntegerArrayList(key, values);
                break;
            }
            case TYPE_STRING_LIST: {
                JSONArray array = encoded.getJSONArray(BUNDLE_VALUE);
                ArrayList<String> values = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    values.add(array.isNull(i) ? null : array.getString(i));
                }
                bundle.putStringArrayList(key, values);
                break;
            }
            default:
                break;
        }
    }

    public static void putControlStateFromBundle(Snapshot snapshot, Bundle outState) {
        if (snapshot == null || outState == null) {
            return;
        }
        snapshot.controlState = bundleToJson(outState);
    }

    static JSONObject encode(Snapshot snapshot) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("version", VERSION);
        root.put("layer_id", snapshot.layerId);
        root.put("feature_id", snapshot.featureId);
        root.put("geometry_changed", snapshot.geometryChanged);
        root.put("point_session_id", snapshot.pointSessionId);
        root.put("walk_session_id", snapshot.walkSessionId);
        root.put("geometry_wkt",
                snapshot.geometryWkt != null ? snapshot.geometryWkt : JSONObject.NULL);
        root.put("form_path", snapshot.formPath != null ? snapshot.formPath : JSONObject.NULL);
        root.put("meta_path", snapshot.metaPath != null ? snapshot.metaPath : JSONObject.NULL);
        root.put("updated_at", snapshot.updatedAtMs);
        root.put("control_state",
                snapshot.controlState != null ? snapshot.controlState : new JSONObject());
        JSONArray photos = new JSONArray();
        if (snapshot.photoPaths != null) {
            for (String path : snapshot.photoPaths) {
                photos.put(path != null ? path : JSONObject.NULL);
            }
        }
        root.put("photo_paths", photos);
        return root;
    }

    static Snapshot decode(JSONObject root) throws JSONException {
        if (root == null || root.optInt("version", -1) != VERSION) {
            return null;
        }
        Snapshot snapshot = new Snapshot();
        snapshot.layerId = root.optInt("layer_id", Constants.NOT_FOUND);
        snapshot.featureId = root.optLong("feature_id", Constants.NOT_FOUND);
        snapshot.geometryChanged = root.optBoolean("geometry_changed", false);
        snapshot.pointSessionId = root.optString("point_session_id", null);
        snapshot.walkSessionId = root.optString("walk_session_id", null);
        snapshot.geometryWkt = root.isNull("geometry_wkt") ? null : root.optString("geometry_wkt", null);
        snapshot.formPath = root.isNull("form_path") ? null : root.optString("form_path", null);
        snapshot.metaPath = root.isNull("meta_path") ? null : root.optString("meta_path", null);
        snapshot.updatedAtMs = root.optLong("updated_at", 0L);
        JSONObject control = root.optJSONObject("control_state");
        snapshot.controlState = control != null ? control : new JSONObject();
        snapshot.photoPaths = new ArrayList<>();
        JSONArray photos = root.optJSONArray("photo_paths");
        if (photos != null) {
            for (int i = 0; i < photos.length(); i++) {
                snapshot.photoPaths.add(photos.isNull(i) ? null : photos.optString(i, null));
            }
        }
        return snapshot;
    }

    private static JSONObject bundleToJson(Bundle bundle) {
        JSONObject json = new JSONObject();
        if (bundle == null) {
            return json;
        }
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            try {
                JSONObject encoded = encodeBundleValue(value);
                if (encoded != null) {
                    json.put(key, encoded);
                }
            } catch (JSONException ignored) {
            }
        }
        return json;
    }

    /**
     * Preserve Bundle numeric types explicitly: JSONObject parses a small Long as Integer, while
     * controls such as Counter restore it with Bundle.getLong().  Unsupported Parcelable lists
     * are intentionally skipped; pending photos have their own path journal.
     */
    static JSONObject encodeBundleValue(Object value) throws JSONException {
        JSONObject encoded = new JSONObject();
        if (value == null) {
            encoded.put(BUNDLE_TYPE, TYPE_STRING);
            encoded.put(BUNDLE_VALUE, JSONObject.NULL);
            return encoded;
        }
        if (value instanceof Boolean) {
            encoded.put(BUNDLE_TYPE, TYPE_BOOLEAN);
            encoded.put(BUNDLE_VALUE, value);
            return encoded;
        }
        if (value instanceof Integer) {
            encoded.put(BUNDLE_TYPE, TYPE_INT);
            encoded.put(BUNDLE_VALUE, value);
            return encoded;
        }
        if (value instanceof Long) {
            encoded.put(BUNDLE_TYPE, TYPE_LONG);
            encoded.put(BUNDLE_VALUE, value);
            return encoded;
        }
        if (value instanceof Double || value instanceof Float) {
            encoded.put(BUNDLE_TYPE, TYPE_DOUBLE);
            encoded.put(BUNDLE_VALUE, ((Number) value).doubleValue());
            return encoded;
        }
        if (value instanceof String || value instanceof CharSequence) {
            encoded.put(BUNDLE_TYPE, TYPE_STRING);
            encoded.put(BUNDLE_VALUE, value.toString());
            return encoded;
        }
        if (value instanceof ArrayList) {
            List<?> list = (List<?>) value;
            // An empty Bundle list has no runtime element type.  Persisting it as an integer
            // list can corrupt an originally Parcelable list (for example attached_images).
            // Empty lists carry no user state, so omitting them is lossless.
            if (list.isEmpty()) {
                return null;
            }
            boolean allInts = true;
            boolean allStrings = true;
            for (Object item : list) {
                allInts &= item instanceof Integer;
                allStrings &= item == null || item instanceof String
                        || item instanceof CharSequence;
            }
            if (!allInts && !allStrings) {
                return null;
            }
            JSONArray array = new JSONArray();
            for (Object item : list) {
                array.put(item == null ? JSONObject.NULL : item.toString());
            }
            if (allInts) {
                array = new JSONArray();
                for (Object item : list) {
                    array.put(item);
                }
                encoded.put(BUNDLE_TYPE, TYPE_INT_LIST);
            } else {
                encoded.put(BUNDLE_TYPE, TYPE_STRING_LIST);
            }
            encoded.put(BUNDLE_VALUE, array);
            return encoded;
        }
        return null;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
