package com.nextgis.maplibui.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/** Prevents an unchanged broken server schema/config from rebuilding a heavy layer forever. */
public final class SchemaRebuildRetryGuard {
    static final int MAX_ATTEMPTS_PER_SIGNATURE = 2;
    static final long MIN_RETRY_INTERVAL_MS = 10L * 60L * 1000L;
    static final long ATTEMPT_WINDOW_MS = 24L * 60L * 60L * 1000L;

    private static final String PREFS = "ngw_schema_rebuild_guard";
    private static final String KEY_PREFIX = "guard_";

    public enum Decision {
        ALLOWED,
        COOLDOWN,
        LIMIT_REACHED,
        STORAGE_ERROR
    }

    private SchemaRebuildRetryGuard() {
    }

    public static Decision tryRecordAttempt(
            Context context,
            String workspaceKey,
            String accountName,
            long remoteId,
            String signature) {
        return tryRecordAttempt(
                context, workspaceKey, accountName, remoteId, signature,
                System.currentTimeMillis());
    }

    static Decision tryRecordAttempt(
            Context context,
            String workspaceKey,
            String accountName,
            long remoteId,
            String signature,
            long now) {
        if (context == null || remoteId <= 0L || TextUtils.isEmpty(signature)) {
            return Decision.STORAGE_ERROR;
        }
        SharedPreferences preferences = preferences(context);
        String key = buildKey(workspaceKey, accountName, remoteId);
        State state = State.decode(preferences.getString(key, null));
        Decision policy = evaluatePolicy(
                state != null ? state.signature : null,
                state != null ? state.windowStartedAt : 0L,
                state != null ? state.lastAttemptAt : 0L,
                state != null ? state.attemptCount : 0,
                signature,
                now);
        if (policy != Decision.ALLOWED) {
            return policy;
        }
        if (state == null
                || !signature.equals(state.signature)
                || now - state.windowStartedAt >= ATTEMPT_WINDOW_MS
                || now < state.windowStartedAt) {
            state = new State(signature, now, 0L, 0);
        }
        State updated = new State(
                signature, state.windowStartedAt, now, state.attemptCount + 1);
        return preferences.edit().putString(key, updated.encode()).commit()
                ? Decision.ALLOWED : Decision.STORAGE_ERROR;
    }

    static Decision evaluatePolicy(
            String storedSignature,
            long windowStartedAt,
            long lastAttemptAt,
            int attemptCount,
            String incomingSignature,
            long now) {
        if (incomingSignature == null || incomingSignature.isEmpty()) {
            return Decision.STORAGE_ERROR;
        }
        if (storedSignature == null
                || !incomingSignature.equals(storedSignature)
                || now < windowStartedAt
                || now - windowStartedAt >= ATTEMPT_WINDOW_MS) {
            return Decision.ALLOWED;
        }
        if (attemptCount >= MAX_ATTEMPTS_PER_SIGNATURE) {
            return Decision.LIMIT_REACHED;
        }
        if (lastAttemptAt > 0L && now - lastAttemptAt < MIN_RETRY_INTERVAL_MS) {
            return Decision.COOLDOWN;
        }
        return Decision.ALLOWED;
    }

    public static void clearForWorkspace(Context context, String workspaceKey) {
        if (context == null) {
            return;
        }
        SharedPreferences preferences = preferences(context);
        String prefix = KEY_PREFIX + stableHash(workspaceKey) + "_";
        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = false;
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                editor.remove(entry.getKey());
                changed = true;
            }
        }
        if (changed) {
            editor.apply();
        }
    }

    public static int blockedLayerCount(Context context, String workspaceKey) {
        if (context == null) {
            return 0;
        }
        String prefix = KEY_PREFIX + stableHash(workspaceKey) + "_";
        int count = 0;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ?> entry : preferences(context).getAll().entrySet()) {
            if (!entry.getKey().startsWith(prefix) || !(entry.getValue() instanceof String)) {
                continue;
            }
            State state = State.decode((String) entry.getValue());
            if (state != null
                    && now - state.windowStartedAt < ATTEMPT_WINDOW_MS
                    && state.attemptCount >= MAX_ATTEMPTS_PER_SIGNATURE) {
                count++;
            }
        }
        return count;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String buildKey(String workspaceKey, String accountName, long remoteId) {
        return KEY_PREFIX + stableHash(workspaceKey) + "_"
                + stableHash(accountName) + "_" + remoteId;
    }

    private static String stableHash(String value) {
        return Integer.toHexString(value != null ? value.hashCode() : 0);
    }

    private static final class State {
        final String signature;
        final long windowStartedAt;
        final long lastAttemptAt;
        final int attemptCount;

        State(String signature, long windowStartedAt, long lastAttemptAt, int attemptCount) {
            this.signature = signature;
            this.windowStartedAt = windowStartedAt;
            this.lastAttemptAt = lastAttemptAt;
            this.attemptCount = attemptCount;
        }

        String encode() {
            try {
                return new JSONObject()
                        .put("signature", signature)
                        .put("window_started_at", windowStartedAt)
                        .put("last_attempt_at", lastAttemptAt)
                        .put("attempt_count", attemptCount)
                        .toString();
            } catch (JSONException e) {
                return "";
            }
        }

        static State decode(String value) {
            if (TextUtils.isEmpty(value)) {
                return null;
            }
            try {
                JSONObject json = new JSONObject(value);
                String signature = json.optString("signature", null);
                long windowStartedAt = json.optLong("window_started_at", 0L);
                int attemptCount = json.optInt("attempt_count", -1);
                if (TextUtils.isEmpty(signature) || windowStartedAt <= 0L || attemptCount < 0) {
                    return null;
                }
                return new State(
                        signature,
                        windowStartedAt,
                        json.optLong("last_attempt_at", 0L),
                        attemptCount);
            } catch (JSONException e) {
                return null;
            }
        }
    }
}
