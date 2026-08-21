package com.nextgis.maplibui.service;

import com.nextgis.maplib.datasource.GeoLineString;
import com.nextgis.maplib.datasource.GeoPoint;

/** Keeps walk vertices contiguous directly after the vertex selected at walk start. */
final class WalkGeometryInsertion {
    private WalkGeometryInsertion() {
    }

    static int insert(GeoLineString geometry, int requestedIndex, GeoPoint point) {
        int index = Math.max(0, Math.min(requestedIndex, geometry.getPointCount()));
        geometry.getPoints().add(index, point);
        return index + 1;
    }
}
