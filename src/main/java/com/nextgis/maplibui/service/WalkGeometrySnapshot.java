package com.nextgis.maplibui.service;

import com.nextgis.maplib.datasource.GeoGeometry;
import com.nextgis.maplib.datasource.GeoGeometryCollection;
import com.nextgis.maplib.datasource.GeoLineString;
import com.nextgis.maplib.datasource.GeoLinearRing;
import com.nextgis.maplib.datasource.GeoMultiLineString;
import com.nextgis.maplib.datasource.GeoMultiPolygon;
import com.nextgis.maplib.datasource.GeoPolygon;

/** A walk owns a complete private geometry, including every untouched part and hole. */
public final class WalkGeometrySnapshot {
    private WalkGeometrySnapshot() { }

    public static GeoGeometry restore(String wkt, int member, int ring) {
        GeoGeometry full = com.nextgis.maplib.datasource.GeoGeometryFactory.fromWKT(
                wkt, com.nextgis.maplib.util.GeoConstants.CRS_WEB_MERCATOR);
        GeoLineString target = part(full, member, ring);
        // GeoLinearRing.toWKT adds a closing duplicate, even to a one-node draft.
        // The walk cursor operates on an open ring; its closing edge is preview-only.
        if (target instanceof GeoLinearRing && target.getPointCount() > 1
                && target.getPoint(0).equals(target.getPoint(target.getPointCount() - 1))) {
            target.remove(target.getPointCount() - 1);
        }
        return full;
    }

    public static GeoLineString part(GeoGeometry geometry, int member, int ring) {
        if (member < 0 || ring < 0) throw new IllegalArgumentException("Negative walk target");
        GeoGeometry selected = geometry;
        if (geometry instanceof GeoMultiLineString || geometry instanceof GeoMultiPolygon) {
            GeoGeometryCollection collection = (GeoGeometryCollection) geometry;
            if (member >= collection.size()) throw new IllegalArgumentException("Missing walk member");
            selected = collection.get(member);
        } else if (member != 0) {
            throw new IllegalArgumentException("Unexpected walk member");
        }
        if (selected instanceof GeoPolygon) {
            GeoPolygon polygon = (GeoPolygon) selected;
            if (ring > polygon.getInnerRingCount()) throw new IllegalArgumentException("Missing walk ring");
            return ring == 0 ? polygon.getOuterRing() : polygon.getInnerRing(ring - 1);
        }
        if (selected instanceof GeoLineString && ring == 0) return (GeoLineString) selected;
        throw new IllegalArgumentException("Unsupported walk target");
    }

    public static GeoGeometry replace(GeoGeometry base, GeoLineString recorded, int member, int ring) {
        GeoGeometry result = base.copy();
        result.setCRS(base.getCRS());
        GeoLineString target = part(result, member, ring);
        target.clear();
        for (int i = 0; i < recorded.getPointCount(); i++) {
            target.add((com.nextgis.maplib.datasource.GeoPoint) recorded.getPoint(i).copy());
        }
        target.setCRS(recorded.getCRS());
        return result;
    }
}
