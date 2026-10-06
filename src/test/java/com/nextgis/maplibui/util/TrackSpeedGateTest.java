package com.nextgis.maplibui.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrackSpeedGateTest {
    private static final long SECOND = 1_000_000_000L;
    private static final long MAX_GAP = 8 * SECOND;
    private final TrackSpeedGate pedestrian = new TrackSpeedGate(TrackRecordingMode.PEDESTRIAN);

    @Test public void thresholdIsThirtyKmhAndOnlyFasterFixesAreSkipped() {
        assertTrue(pedestrian.shouldRecord(0, 0, SECOND, MAX_GAP));
        assertTrue(pedestrian.shouldRecord(30f / 3.6f, 0, SECOND, MAX_GAP));
        assertFalse(pedestrian.shouldRecord(30.01f / 3.6f, 0, SECOND, MAX_GAP));
        assertFalse(pedestrian.shouldRecord(100f / 3.6f, 0, SECOND, MAX_GAP));
    }

    @Test public void receiverSpeedWinsOverCoordinateNoise() {
        assertTrue(pedestrian.shouldRecord(1, 100, SECOND, MAX_GAP));
        assertFalse(pedestrian.shouldRecord(20, 0, SECOND, MAX_GAP));
    }

    @Test public void missingSpeedUsesSuccessiveFixesAndResumesAfterDriving() {
        assertTrue(pedestrian.shouldRecord(Float.NaN, 1, SECOND, MAX_GAP));
        assertFalse(pedestrian.shouldRecord(Float.NaN, 20, SECOND, MAX_GAP));
        assertFalse(pedestrian.shouldRecord(Float.NaN, 20, SECOND, MAX_GAP));
        assertTrue(pedestrian.shouldRecord(Float.NaN, 1, SECOND, MAX_GAP));
    }

    @Test public void unknownOrStalePairCannotStartPedestrianRecording() {
        assertFalse(pedestrian.shouldRecord(Float.NaN, Float.NaN, 0, MAX_GAP));
        assertFalse(pedestrian.shouldRecord(Float.NaN, 1, 9 * SECOND, MAX_GAP));
        assertFalse(pedestrian.shouldRecord(Float.NaN, 1, -SECOND, MAX_GAP));
        assertFalse(pedestrian.shouldRecord(Float.NaN, Float.POSITIVE_INFINITY, SECOND, MAX_GAP));
        assertTrue(pedestrian.shouldRecord(-1, 1, SECOND, MAX_GAP));
    }

    @Test public void mixedModeAndLegacySessionsKeepRecordingCars() {
        TrackSpeedGate mixed = new TrackSpeedGate(TrackRecordingMode.WALK_AND_DRIVE);
        assertTrue(mixed.shouldRecord(160f / 3.6f, 0, SECOND, MAX_GAP));
        assertTrue(mixed.shouldRecord(Float.NaN, Float.NaN, 0, MAX_GAP));
        assertEquals(TrackRecordingMode.WALK_AND_DRIVE, TrackRecordingMode.fromPreference(null));
        assertEquals(TrackRecordingMode.WALK_AND_DRIVE, TrackRecordingMode.fromPreference("unknown"));
        for (TrackRecordingMode mode : TrackRecordingMode.values())
            assertEquals(mode, TrackRecordingMode.fromPreference(mode.preferenceValue));
    }
}
