package com.nextgis.maplibui.util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class FeatureFormDraftStoreTest {
    @Test
    public void roundTripPreservesCrashRecoveryIdentityAndPayload() throws Exception {
        FeatureFormDraftStore.Snapshot source = new FeatureFormDraftStore.Snapshot();
        source.layerId = 17;
        source.featureId = 4_294_967_300L;
        source.geometryChanged = true;
        source.geometryWkt = "LINESTRING (1 2, 3 4)";
        source.formPath = "C:/forms/roads_form.json";
        source.metaPath = "C:/forms/roads_meta.json";
        source.updatedAtMs = 123_456_789L;
        source.photoPaths = Arrays.asList("C:/photos/one.jpg", "content://photos/two");
        source.controlState = new JSONObject()
                .put("counter", new JSONObject()
                        .put("__bundle_type", "long")
                        .put("value", 7L))
                .put("choices", new JSONObject()
                        .put("__bundle_type", "int_list")
                        .put("value", new JSONArray().put(2).put(5)));

        FeatureFormDraftStore.Snapshot restored = FeatureFormDraftStore.decode(
                new JSONObject(FeatureFormDraftStore.encode(source).toString()));

        assertNotNull(restored);
        assertEquals(source.layerId, restored.layerId);
        assertEquals(source.featureId, restored.featureId);
        assertEquals(source.geometryChanged, restored.geometryChanged);
        assertEquals(source.geometryWkt, restored.geometryWkt);
        assertEquals(source.formPath, restored.formPath);
        assertEquals(source.metaPath, restored.metaPath);
        assertEquals(source.updatedAtMs, restored.updatedAtMs);
        assertEquals(source.photoPaths, restored.photoPaths);
        assertEquals("long",
                restored.controlState.getJSONObject("counter").getString("__bundle_type"));
        assertEquals(2,
                restored.controlState.getJSONObject("choices").getJSONArray("value").length());
    }

    @Test
    public void rejectsUnknownVersion() throws Exception {
        JSONObject unsupported = new JSONObject()
                .put("version", 999)
                .put("layer_id", 17);

        assertNull(FeatureFormDraftStore.decode(unsupported));
    }

    @Test
    public void defaultSnapshotIsNotRecoverable() {
        assertFalse(new FeatureFormDraftStore.Snapshot().isValid());
    }

    @Test
    public void typedBundleEncodingDoesNotNarrowLongOrGuessEmptyListType() throws Exception {
        JSONObject encodedLong = FeatureFormDraftStore.encodeBundleValue(7L);
        JSONObject encodedInts = FeatureFormDraftStore.encodeBundleValue(
                new ArrayList<>(Arrays.asList(2, 5)));

        assertEquals("long", encodedLong.getString("__bundle_type"));
        assertEquals(7L, encodedLong.getLong("value"));
        assertEquals("int_list", encodedInts.getString("__bundle_type"));
        assertEquals(5, encodedInts.getJSONArray("value").getInt(1));
        assertNull(FeatureFormDraftStore.encodeBundleValue(new ArrayList<>()));
    }
}
