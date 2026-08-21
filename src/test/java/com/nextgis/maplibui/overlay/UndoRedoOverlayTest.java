package com.nextgis.maplibui.overlay;

import com.nextgis.maplib.datasource.Feature;
import com.nextgis.maplib.datasource.GeoLinearRing;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.datasource.GeoPolygon;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.R;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UndoRedoOverlayTest {
    @Test
    public void duplicateGeometryConsumesNoUndoOrRedoStep() {
        UndoRedoOverlay history = new UndoRedoOverlay(null, null);
        Feature feature = new Feature();

        savePoint(history, feature, 0);
        savePoint(history, feature, 0);
        savePoint(history, feature, 1);
        savePoint(history, feature, 1);
        savePoint(history, feature, 1);

        assertTrue(history.onOptionsItemSelected(R.id.menu_edit_undo));
        assertCurrentX(history, 0);
        savePoint(history, feature, 0);
        assertTrue(history.onOptionsItemSelected(R.id.menu_edit_redo));
        assertCurrentX(history, 1);

        assertFalse(history.onOptionsItemSelected(R.id.menu_edit_redo));
        assertTrue(history.onOptionsItemSelected(R.id.menu_edit_undo));
        assertCurrentX(history, 0);
    }

    @Test
    public void sameCoordinatesWithUpdatedCrsConsumeNoUndoStep() {
        UndoRedoOverlay history = new UndoRedoOverlay(null, null);
        Feature feature = new Feature();

        savePolygon(history, feature, 0, GeoConstants.CRS_WGS84);
        savePolygon(history, feature, 0, GeoConstants.CRS_WEB_MERCATOR);
        savePolygon(history, feature, 1, GeoConstants.CRS_WEB_MERCATOR);

        assertTrue(history.onOptionsItemSelected(R.id.menu_edit_undo));
        GeoPolygon restored = (GeoPolygon) history.getFeature().getGeometry();
        assertEquals(0, restored.getOuterRing().getPoint(1).getX(), 0.0);
        assertEquals(
                GeoConstants.CRS_WEB_MERCATOR,
                restored.getOuterRing().getPoint(1).getCRS());
        assertFalse(history.onOptionsItemSelected(R.id.menu_edit_undo));
    }

    @Test
    public void retainsOneHundredUndoStepsWithoutNoOpAtCapacity() {
        UndoRedoOverlay history = new UndoRedoOverlay(null, null);
        Feature feature = new Feature();

        for (int i = 0; i <= UndoRedoOverlay.MAX_UNDO + 50; i++) {
            savePoint(history, feature, i);
        }

        for (int i = 0; i < UndoRedoOverlay.MAX_UNDO; i++) {
            assertTrue(history.onOptionsItemSelected(R.id.menu_edit_undo));
        }
        assertCurrentX(history, 50);
        assertFalse(history.onOptionsItemSelected(R.id.menu_edit_undo));

        assertTrue(history.onOptionsItemSelected(R.id.menu_edit_redo));
        assertCurrentX(history, 51);
    }

    private static void savePoint(UndoRedoOverlay history, Feature feature, double x) {
        savePoint(history, feature, x, GeoConstants.CRS_WEB_MERCATOR);
    }

    private static void savePoint(
            UndoRedoOverlay history, Feature feature, double x, int crs) {
        GeoPoint point = new GeoPoint(x, 0);
        point.setCRS(crs);
        feature.setGeometry(point);
        history.saveToHistory(feature);
    }

    private static void savePolygon(
            UndoRedoOverlay history, Feature feature, double x, int crs) {
        GeoLinearRing ring = new GeoLinearRing();
        ring.setCRS(crs);
        ring.add(point(0, 0, crs));
        ring.add(point(x, 10, crs));
        ring.add(point(10, 0, crs));

        GeoPolygon polygon = new GeoPolygon();
        polygon.setCRS(crs);
        polygon.setOuterRing(ring);
        feature.setGeometry(polygon);
        history.saveToHistory(feature);
    }

    private static GeoPoint point(double x, double y, int crs) {
        GeoPoint point = new GeoPoint(x, y);
        point.setCRS(crs);
        return point;
    }

    private static void assertCurrentX(UndoRedoOverlay history, double expected) {
        GeoPoint point = (GeoPoint) history.getFeature().getGeometry();
        assertEquals(expected, point.getX(), 0.0);
    }
}
