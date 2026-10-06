package com.nextgis.maplibui.util;

/** Persisted track-only policy. Existing sessions keep the unrestricted behaviour. */
public enum TrackRecordingMode {
    PEDESTRIAN("pedestrian"),
    WALK_AND_DRIVE("walk_and_drive");

    public final String preferenceValue;

    TrackRecordingMode(String preferenceValue) {
        this.preferenceValue = preferenceValue;
    }

    public static TrackRecordingMode fromPreference(String value) {
        return PEDESTRIAN.preferenceValue.equals(value) ? PEDESTRIAN : WALK_AND_DRIVE;
    }
}
