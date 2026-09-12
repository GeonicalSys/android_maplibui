package com.nextgis.maplibui.service;

import com.nextgis.maplib.datasource.*;
import com.nextgis.maplib.util.GeoConstants;
import org.junit.Test;
import static org.junit.Assert.*;

public class WalkGeometrySnapshotTest {
    @Test public void walkingAnInnerRingKeepsOtherPolygonMembersAndHolesAndDoesNotMutateTheEditor() {
        GeoMultiPolygon full = (GeoMultiPolygon) GeoGeometryFactory.fromWKT(
                "MULTIPOLYGON (((0 0, 100 0, 100 100, 0 0), (10 10, 20 10, 20 20, 10 10)),"
                        + " ((200 0, 300 0, 300 100, 200 0), (210 10, 220 10, 220 20, 210 10)))",
                GeoConstants.CRS_WEB_MERCATOR);
        String before = full.toWKT(true);
        GeoLineString recorded = new GeoLinearRing();
        recorded.setCRS(GeoConstants.CRS_WEB_MERCATOR);
        recorded.add(new GeoPoint(210, 10)); recorded.add(new GeoPoint(240, 20));
        GeoMultiPolygon updated = (GeoMultiPolygon) WalkGeometrySnapshot.replace(full, recorded, 1, 1);
        assertEquals(before, full.toWKT(true));
        assertEquals(full.get(0).toWKT(true), updated.get(0).toWKT(true));
        assertEquals(full.get(1).getOuterRing().toWKT(true), updated.get(1).getOuterRing().toWKT(true));
        assertEquals(2, updated.get(1).getInnerRing(0).getPointCount());
        assertEquals(240, updated.get(1).getInnerRing(0).getPoint(1).getX(), 0);
        recorded.getPoint(1).setX(999);
        assertEquals(240, updated.get(1).getInnerRing(0).getPoint(1).getX(), 0);
        GeoGeometry restored = GeoGeometryFactory.fromWKT(updated.toWKT(true), GeoConstants.CRS_WEB_MERCATOR);
        assertEquals(updated.toWKT(true), restored.toWKT(true));
    }

    @Test public void aOneNodeNewPolygonSurvivesTheDurableWktBeforeItBecomesValid() {
        GeoPolygon full = new GeoPolygon();
        full.setCRS(GeoConstants.CRS_WEB_MERCATOR);
        full.getOuterRing().add(new GeoPoint(10, 20));
        GeoGeometry restored = WalkGeometrySnapshot.restore(full.toWKT(true), 0, 0);
        assertEquals(1, WalkGeometrySnapshot.part(restored, 0, 0).getPointCount());
    }

    @Test(expected = IllegalArgumentException.class) public void anInvalidTargetCannotOverwriteAnotherPart() {
        WalkGeometrySnapshot.part(new GeoPolygon(), 0, 1);
    }
}
