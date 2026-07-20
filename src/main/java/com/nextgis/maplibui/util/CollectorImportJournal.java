package com.nextgis.maplibui.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.nextgis.maplib.util.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Crash-safe description of an unfinished Collector vector-layer import.
 *
 * <p>The Android service queue itself is process memory. This journal keeps the
 * complete desired batch in app-private SharedPreferences (whose file update is
 * atomic) so a new process can verify the already-created layers and requeue only
 * missing/broken ones. It intentionally contains no credentials or downloaded
 * feature data.</p>
 */
public final class CollectorImportJournal {
    private static final String TAG = "CollectorImportJournal";
    private static final String PREFS = "collector_import_journal";
    private static final String KEY_STATE = "state_json";
    private static final int VERSION = 1;

    private CollectorImportJournal() {
    }

    public static final class Snapshot {
        public int groupId;
        public String accountName;
        public String projectUid;
        public long[] remoteIds;
        public String[] names;
        public String[] configJsons;
        public long[] formIds;
        public boolean[] editables;
        public long[] fullProjectRemoteIds;
        public int repairPassesRemaining;

        public boolean isValid() {
            int count = remoteIds != null ? remoteIds.length : 0;
            if (groupId == Constants.NOT_FOUND || isEmpty(accountName) || count == 0
                    || names == null || names.length != count
                    || configJsons == null || configJsons.length != count
                    || formIds == null || formIds.length != count
                    || editables == null || editables.length != count
                    || fullProjectRemoteIds == null || fullProjectRemoteIds.length == 0
                    || repairPassesRemaining < 0) {
                return false;
            }
            for (long remoteId : remoteIds) {
                boolean found = false;
                for (long projectRemoteId : fullProjectRemoteIds) {
                    if (remoteId == projectRemoteId) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }
    }

    public static boolean save(Context context, Snapshot snapshot) {
        if (context == null || snapshot == null || !snapshot.isValid()) {
            return false;
        }
        try {
            String json = encode(snapshot).toString();
            boolean committed = preferences(context).edit().putString(KEY_STATE, json).commit();
            if (!committed) {
                Log.e(TAG, "Failed to commit Collector import journal");
            }
            return committed;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to encode Collector import journal", e);
            return false;
        }
    }

    public static Snapshot load(Context context) {
        if (context == null) {
            return null;
        }
        String value = preferences(context).getString(KEY_STATE, null);
        if (isEmpty(value)) {
            return null;
        }
        try {
            Snapshot snapshot = decode(new JSONObject(value));
            if (snapshot == null || !snapshot.isValid()) {
                Log.e(TAG, "Invalid Collector import journal; discarding it");
                clear(context);
                return null;
            }
            return snapshot;
        } catch (JSONException e) {
            Log.e(TAG, "Corrupt Collector import journal; discarding it", e);
            clear(context);
            return null;
        }
    }

    public static void clear(Context context) {
        if (context != null && !preferences(context).edit().remove(KEY_STATE).commit()) {
            Log.e(TAG, "Failed to clear Collector import journal");
        }
    }

    static JSONObject encode(Snapshot snapshot) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("version", VERSION);
        root.put("group_id", snapshot.groupId);
        root.put("account", snapshot.accountName);
        root.put("project_uid", snapshot.projectUid != null ? snapshot.projectUid : JSONObject.NULL);
        root.put("remote_ids", longArray(snapshot.remoteIds));
        root.put("names", stringArray(snapshot.names));
        root.put("configs", stringArray(snapshot.configJsons));
        root.put("form_ids", longArray(snapshot.formIds));
        root.put("editables", booleanArray(snapshot.editables));
        root.put("full_project_remote_ids", longArray(snapshot.fullProjectRemoteIds));
        root.put("repair_passes_remaining", snapshot.repairPassesRemaining);
        return root;
    }

    static Snapshot decode(JSONObject root) throws JSONException {
        if (root == null || root.optInt("version", -1) != VERSION) {
            return null;
        }
        Snapshot snapshot = new Snapshot();
        snapshot.groupId = root.optInt("group_id", Constants.NOT_FOUND);
        snapshot.accountName = root.optString("account", null);
        snapshot.projectUid = root.isNull("project_uid") ? null : root.optString("project_uid", null);
        snapshot.remoteIds = toLongArray(root.getJSONArray("remote_ids"));
        snapshot.names = toStringArray(root.getJSONArray("names"));
        snapshot.configJsons = toStringArray(root.getJSONArray("configs"));
        snapshot.formIds = toLongArray(root.getJSONArray("form_ids"));
        snapshot.editables = toBooleanArray(root.getJSONArray("editables"));
        snapshot.fullProjectRemoteIds = toLongArray(root.getJSONArray("full_project_remote_ids"));
        snapshot.repairPassesRemaining = root.optInt("repair_passes_remaining", -1);
        return snapshot.isValid() ? snapshot : null;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }

    private static JSONArray longArray(long[] values) {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (long value : values) {
                array.put(value);
            }
        }
        return array;
    }

    private static JSONArray stringArray(String[] values) {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (String value : values) {
                array.put(value != null ? value : JSONObject.NULL);
            }
        }
        return array;
    }

    private static JSONArray booleanArray(boolean[] values) {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (boolean value : values) {
                array.put(value);
            }
        }
        return array;
    }

    private static long[] toLongArray(JSONArray array) throws JSONException {
        long[] result = new long[array.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = array.getLong(i);
        }
        return result;
    }

    private static String[] toStringArray(JSONArray array) throws JSONException {
        String[] result = new String[array.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = array.isNull(i) ? null : array.getString(i);
        }
        return result;
    }

    private static boolean[] toBooleanArray(JSONArray array) throws JSONException {
        boolean[] result = new boolean[array.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = array.getBoolean(i);
        }
        return result;
    }
}
