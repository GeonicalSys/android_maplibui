package com.nextgis.maplibui.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackgroundRecordingSoundPolicyTest {
    @Test
    public void heartbeatRequiresFreshLocationAndThenIsThrottled() {
        BackgroundRecordingSoundPolicy policy = new BackgroundRecordingSoundPolicy();

        assertFalse(policy.shouldPlayHeartbeat(true, true, Long.MIN_VALUE, 10_000L));
        assertTrue(policy.shouldPlayHeartbeat(true, true, 9_000L, 10_000L));
        policy.recordHeartbeat(10_000L);
        assertFalse(policy.shouldPlayHeartbeat(true, true, 19_000L, 19_999L));
        assertTrue(policy.shouldPlayHeartbeat(true, true, 19_000L, 20_000L));
    }

    @Test
    public void foregroundAndDisabledSettingSuppressHeartbeat() {
        BackgroundRecordingSoundPolicy policy = new BackgroundRecordingSoundPolicy();

        assertFalse(policy.shouldPlayHeartbeat(true, false, 9_000L, 10_000L));
        assertFalse(policy.shouldPlayHeartbeat(false, true, 9_000L, 10_000L));
    }

    @Test
    public void stationaryButFreshCallbacksKeepHeartbeatAlive() {
        BackgroundRecordingSoundPolicy policy = new BackgroundRecordingSoundPolicy();

        assertTrue(policy.shouldPlayHeartbeat(true, true, 9_500L, 10_000L));
        policy.recordHeartbeat(10_000L);
        assertTrue(policy.shouldPlayHeartbeat(true, true, 19_500L, 20_000L));
    }

    @Test
    public void staleCoordinatesSuppressHeartbeat() {
        BackgroundRecordingSoundPolicy policy = new BackgroundRecordingSoundPolicy();

        assertFalse(policy.shouldPlayHeartbeat(true, true, 1_999L, 10_000L));
        assertTrue(policy.shouldPlayHeartbeat(true, true, 2_000L, 10_000L));
    }

    @Test
    public void errorsHaveIndependentLongerThrottle() {
        BackgroundRecordingSoundPolicy policy = new BackgroundRecordingSoundPolicy();

        assertTrue(policy.shouldPlayError(true, true, 1_000L));
        policy.recordError(1_000L);
        assertFalse(policy.shouldPlayError(true, true, 60_999L));
        assertTrue(policy.shouldPlayError(true, true, 61_000L));
        assertTrue(policy.shouldPlayHeartbeat(true, true, 1_000L, 1_001L));
    }

    @Test
    public void clockRollbackAllowsNextSignal() {
        BackgroundRecordingSoundPolicy policy = new BackgroundRecordingSoundPolicy();
        policy.recordHeartbeat(100_000L);

        assertTrue(policy.shouldPlayHeartbeat(true, true, 499L, 500L));
    }

    @Test
    public void zeroOrMutedAlarmUsesVibrationFallback() {
        assertTrue(BackgroundRecordingSoundPolicy.shouldVibrate(0, false));
        assertTrue(BackgroundRecordingSoundPolicy.shouldVibrate(5, true));
        assertFalse(BackgroundRecordingSoundPolicy.shouldVibrate(1, false));
    }
}
