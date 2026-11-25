package com.nextgis.maplibui.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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

    @Test
    public void earlierFormFillAttemptsDoNotBlockTheFixedReplacement() {
        String exhausted = "{\"signature\":\"server_meta:current\",\"window_started_at\":100,"
                + "\"last_attempt_at\":120,\"attempt_count\":2}";
        assertNull(SchemaRebuildRetryGuard.State.decode(exhausted));

        SchemaRebuildRetryGuard.State state = SchemaRebuildRetryGuard.State.decode(
                new SchemaRebuildRetryGuard.State("server_meta:current", 100L, 120L, 2).encode());
        assertNotNull(state);
        assertEquals(SchemaRebuildRetryGuard.Decision.LIMIT_REACHED,
                SchemaRebuildRetryGuard.evaluatePolicy(state.signature, state.windowStartedAt,
                        state.lastAttemptAt, state.attemptCount, "server_meta:current", 130L));
    }
}
