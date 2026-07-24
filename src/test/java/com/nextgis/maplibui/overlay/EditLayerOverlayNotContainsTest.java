package com.nextgis.maplibui.overlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.nextgis.maplib.datasource.GeoEnvelope;
import com.nextgis.maplib.datasource.GeoLineString;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.datasource.GeoPolygon;

import org.junit.Test;

public class EditLayerOverlayNotContainsTest {

    @Test
    public void line_emptyBboxCorner_isRejected() {
        GeoLineString line = new GeoLineString();
        line.add(new GeoPoint(0, 0));
        line.add(new GeoPoint(10, 0));
        line.add(new GeoPoint(10, 10));
        GeoEnvelope tap = new GeoEnvelope(1, 3, 7, 9);
        GeoPoint center = new GeoPoint(tap.getCenter().getX(), tap.getCenter().getY());
        assertTrue(EditLayerOverlay.notContains(line, center, tap));
    }

    @Test
    public void line_nearGeometry_isAccepted() {
        GeoLineString line = new GeoLineString();
        line.add(new GeoPoint(0, 0));
        line.add(new GeoPoint(10, 0));
        line.add(new GeoPoint(10, 10));
        GeoEnvelope tap = new GeoEnvelope(4, 6, -1, 1);
        GeoPoint center = new GeoPoint(tap.getCenter().getX(), tap.getCenter().getY());
        assertFalse(EditLayerOverlay.notContains(line, center, tap));
    }

    @Test
    public void polygon_stillUsesPointInPolygon() {
        GeoPolygon polygon = new GeoPolygon();
        polygon.getOuterRing().add(new GeoPoint(0, 0));
        polygon.getOuterRing().add(new GeoPoint(10, 0));
        polygon.getOuterRing().add(new GeoPoint(10, 10));
        polygon.getOuterRing().add(new GeoPoint(0, 10));
        GeoPoint inside = new GeoPoint(5, 5);
        GeoPoint outside = new GeoPoint(20, 20);
        GeoEnvelope tap = new GeoEnvelope(4, 6, 4, 6);
        assertFalse(EditLayerOverlay.notContains(polygon, inside, tap));
        assertTrue(EditLayerOverlay.notContains(polygon, outside, tap));
    }
}
