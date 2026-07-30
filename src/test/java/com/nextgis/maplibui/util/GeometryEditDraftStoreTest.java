package com.nextgis.maplibui.util;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GeometryEditDraftStoreTest {
    @Test
    public void roundTripPreservesGeometrySessionIdentity() throws Exception {
        GeometryEditDraftStore.Snapshot source = new GeometryEditDraftStore.Snapshot();
        source.layerId = 17;
        source.featureId = 4_294_967_300L;
        source.editMode = GeometryEditDraftStore.MODE_EDIT;
        source.geometryWkt = "LINESTRING (1 2, 3 4)";
        source.mapPath = "C:/collector/project-a/map";
        source.updatedAtMs = 123_456_789L;

        GeometryEditDraftStore.Snapshot restored = GeometryEditDraftStore.decode(
                new JSONObject(GeometryEditDraftStore.encode(source).toString()));

        assertNotNull(restored);
        assertEquals(source.layerId, restored.layerId);
        assertEquals(source.featureId, restored.featureId);
        assertEquals(source.editMode, restored.editMode);
        assertEquals(source.geometryWkt, restored.geometryWkt);
        assertEquals(source.mapPath, restored.mapPath);
        assertEquals(source.updatedAtMs, restored.updatedAtMs);
        assertTrue(restored.isValid());
    }

    @Test
    public void newFeatureAndTouchModeAreValid() {
        GeometryEditDraftStore.Snapshot snapshot = validSnapshot();
        snapshot.featureId = -1L;
        snapshot.editMode = GeometryEditDraftStore.MODE_EDIT_BY_TOUCH;

        assertTrue(snapshot.isValid());
    }

    @Test
    public void rejectsUnknownVersion() throws Exception {
        JSONObject unsupported = new JSONObject()
                .put("version", 999)
                .put("layer_id", 17);

        assertNull(GeometryEditDraftStore.decode(unsupported));
    }

    @Test
    public void rejectsWalkModeAndMissingMapIdentity() {
        GeometryEditDraftStore.Snapshot snapshot = validSnapshot();
        snapshot.editMode = 4;
        assertFalse(snapshot.isValid());

        snapshot.editMode = GeometryEditDraftStore.MODE_EDIT;
        snapshot.mapPath = null;
        assertFalse(snapshot.isValid());
    }

    private static GeometryEditDraftStore.Snapshot validSnapshot() {
        GeometryEditDraftStore.Snapshot snapshot = new GeometryEditDraftStore.Snapshot();
        snapshot.layerId = 17;
        snapshot.featureId = 22L;
        snapshot.editMode = GeometryEditDraftStore.MODE_EDIT;
        snapshot.geometryWkt = "POINT (1 2)";
        snapshot.mapPath = "C:/collector/project-a/map";
        return snapshot;
    }
}
