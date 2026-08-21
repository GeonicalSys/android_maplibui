package com.nextgis.maplibui.overlay;

import com.nextgis.maplib.datasource.Feature;
import com.nextgis.maplib.datasource.GeoPoint;
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
        feature.setGeometry(new GeoPoint(x, 0));
        history.saveToHistory(feature);
    }

    private static void assertCurrentX(UndoRedoOverlay history, double expected) {
        GeoPoint point = (GeoPoint) history.getFeature().getGeometry();
        assertEquals(expected, point.getX(), 0.0);
    }
}
