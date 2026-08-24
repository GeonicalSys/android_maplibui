/*
 * Project: NextGIS Mobile
 * Purpose: Audible health feedback for background geometry recording.
 */
package com.nextgis.maplibui.util;

/**
 * Throttles background recording sounds without depending on Android APIs.
 */
public final class BackgroundRecordingSoundPolicy {
    public static final long HEARTBEAT_INTERVAL_MS = 10_000L;
    public static final long LOCATION_FRESHNESS_MS = 8_000L;
    public static final long ERROR_INTERVAL_MS = 60_000L;

    private long mLastHeartbeatAt = Long.MIN_VALUE;
    private long mLastErrorAt = Long.MIN_VALUE;

    public boolean shouldPlayHeartbeat(
            boolean enabled,
            boolean appInBackground,
            long lastUsableLocationAtMs,
            long nowMs) {
        return enabled
                && appInBackground
                && isFresh(lastUsableLocationAtMs, nowMs, LOCATION_FRESHNESS_MS)
                && isDue(mLastHeartbeatAt, nowMs, HEARTBEAT_INTERVAL_MS);
    }

    public void recordHeartbeat(long nowMs) {
        mLastHeartbeatAt = nowMs;
    }

    public boolean shouldPlayError(boolean enabled, boolean appInBackground, long nowMs) {
        return enabled && appInBackground && isDue(mLastErrorAt, nowMs, ERROR_INTERVAL_MS);
    }

    public void recordError(long nowMs) {
        mLastErrorAt = nowMs;
    }

    private static boolean isDue(long lastAt, long nowMs, long intervalMs) {
        if (lastAt == Long.MIN_VALUE) {
            return true;
        }
        // A monotonic clock should not move backwards, but allow an immediate signal if it does.
        return nowMs < lastAt || nowMs - lastAt >= intervalMs;
    }

    private static boolean isFresh(long lastAt, long nowMs, long freshnessMs) {
        return lastAt != Long.MIN_VALUE
                && nowMs >= lastAt
                && nowMs - lastAt <= freshnessMs;
    }
}
