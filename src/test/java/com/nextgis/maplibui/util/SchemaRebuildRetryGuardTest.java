package com.nextgis.maplibui.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SchemaRebuildRetryGuardTest {
    @Test
    public void unchangedSignatureUsesCooldownThenStopsAfterLimit() {
        long now = 1_000_000L;
        assertEquals(
                SchemaRebuildRetryGuard.Decision.COOLDOWN,
                SchemaRebuildRetryGuard.evaluatePolicy(
                        "broken", now - 1_000L, now - 1_000L, 1, "broken", now));
        assertEquals(
                SchemaRebuildRetryGuard.Decision.LIMIT_REACHED,
                SchemaRebuildRetryGuard.evaluatePolicy(
                        "broken", now - 20_000L, now - 20_000L, 2, "broken", now));
    }

    @Test
    public void changedSignatureAndExpiredWindowCanTryAgain() {
        long now = SchemaRebuildRetryGuard.ATTEMPT_WINDOW_MS + 10_000L;
        assertEquals(
                SchemaRebuildRetryGuard.Decision.ALLOWED,
                SchemaRebuildRetryGuard.evaluatePolicy(
                        "old", 1L, 2L, 2, "new", now));
        assertEquals(
                SchemaRebuildRetryGuard.Decision.ALLOWED,
                SchemaRebuildRetryGuard.evaluatePolicy(
                        "same", 1L, 2L, 2, "same", now));
    }
}
