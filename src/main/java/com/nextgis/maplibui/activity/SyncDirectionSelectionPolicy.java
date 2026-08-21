package com.nextgis.maplibui.activity;

/** Keeps programmatic Spinner selection separate from an authorized user change. */
final class SyncDirectionSelectionPolicy {
    private SyncDirectionSelectionPolicy() {
    }

    static int resolve(int currentDirection, int selectedDirection, boolean configurable) {
        if (!configurable || selectedDirection == currentDirection) {
            return currentDirection;
        }
        return selectedDirection;
    }
}
