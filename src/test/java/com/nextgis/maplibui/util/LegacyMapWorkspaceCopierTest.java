package com.nextgis.maplibui.util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyMapWorkspaceCopierTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void copiesOnlyMapOwnedLayersAndTrackDatabase() throws Exception {
        File source = temporaryFolder.newFolder("legacy");
        File destination = temporaryFolder.newFolder("autonomous");
        File layer = new File(source, "field_layer");
        assertTrue(layer.mkdir());
        write(new File(layer, "config.json"), "{\"name\":\"Field\"}");
        write(new File(layer, "features.sqlite"), "feature-data");
        write(new File(source, "layers.db"), "track-data");
        write(new File(source, "unrelated.txt"), "do-not-copy");

        JSONObject map = new JSONObject().put("layers", new JSONArray()
                .put(new JSONObject().put("path", "field_layer")));
        File sourceMap = new File(source, "default.ngm");
        write(sourceMap, map.toString());

        LegacyMapWorkspaceCopier.copy(sourceMap, destination, "map");

        assertTrue(new File(destination, "map.ngm").isFile());
        assertTrue(new File(destination, "field_layer/config.json").isFile());
        assertTrue(new File(destination, "field_layer/features.sqlite").isFile());
        assertArrayEquals(
                "track-data".getBytes(StandardCharsets.UTF_8),
                java.nio.file.Files.readAllBytes(new File(destination, "layers.db").toPath()));
        assertFalse(new File(destination, "unrelated.txt").exists());
        assertTrue(sourceMap.isFile());
    }

    @Test(expected = IOException.class)
    public void rejectsLayerPathOutsideLegacyMapDirectory() throws Exception {
        File source = temporaryFolder.newFolder("legacy_traversal");
        File destination = temporaryFolder.newFolder("autonomous_traversal");
        JSONObject map = new JSONObject().put("layers", new JSONArray()
                .put(new JSONObject().put("path", "../outside")));
        File sourceMap = new File(source, "default.ngm");
        write(sourceMap, map.toString());

        LegacyMapWorkspaceCopier.copy(sourceMap, destination, "map");
    }

    private static void write(File file, String value) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
