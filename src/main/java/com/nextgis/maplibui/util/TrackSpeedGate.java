package com.nextgis.maplibui.util;

/** Track-only gate after shared GNSS validation and before sampling/queued writes. */
public final class TrackSpeedGate {
    public static final float PEDESTRIAN_MAX_SPEED_MPS = 30f / 3.6f;
    private final TrackRecordingMode mode;

    public TrackSpeedGate(TrackRecordingMode mode) {
        this.mode = mode;
    }

    /**
     * Prefer receiver speed. Without it, use consecutive validated fixes (including skipped
     * ones), never the last recorded point across a drive. Unknown speed waits for a new pair.
     */
    public boolean shouldRecord(float speedMps, float distanceMetres, long elapsedNanos,
                                long maxPairAgeNanos) {
        if (mode != TrackRecordingMode.PEDESTRIAN) return true;
        if (!Float.isFinite(speedMps) || speedMps < 0) {
            if (elapsedNanos <= 0 || elapsedNanos > maxPairAgeNanos
                    || !Float.isFinite(distanceMetres) || distanceMetres < 0) return false;
            speedMps = (float) (distanceMetres * 1_000_000_000d / elapsedNanos);
        }
        return speedMps <= PEDESTRIAN_MAX_SPEED_MPS;
    }
}
