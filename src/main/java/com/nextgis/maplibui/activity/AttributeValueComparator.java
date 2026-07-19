package com.nextgis.maplibui.activity;

final class AttributeValueComparator {
    private AttributeValueComparator() {
    }

    static boolean valuesDiffer(Object modified, String saved) {
        if (modified == null) {
            return saved != null;
        }
        return !modified.toString().equals(saved);
    }
}
