package com.nextgis.maplibui.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LayerFillStagingTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void findsOnlyMarkedUnreferencedLayerDirectories() throws Exception {
        File root = temporaryFolder.newFolder("project");
        File orphan = createLayerDirectory(root, "layer_orphan", true);
        File committed = createLayerDirectory(root, "layer_committed", true);
        createLayerDirectory(root, "layer_legacy_unmarked", false);
        createLayerDirectory(root, "not_a_layer", true);

        Set<String> referenced = new HashSet<>();
        referenced.add(committed.getCanonicalPath());

        List<File> result = LayerFillStaging.findMarkedOrphans(root, referenced);

        assertEquals(1, result.size());
        assertEquals(orphan.getCanonicalPath(), result.get(0).getCanonicalPath());
    }

    @Test
    public void leavesEveryLegacyUnmarkedDirectoryAlone() throws Exception {
        File root = temporaryFolder.newFolder("legacy-project");
        createLayerDirectory(root, "layer_empty", false);
        createLayerDirectory(root, "layer_with_form", false);

        assertTrue(LayerFillStaging.findMarkedOrphans(
                root, Collections.emptySet()).isEmpty());
    }

    private static File createLayerDirectory(File root, String name, boolean marked)
            throws Exception {
        File directory = new File(root, name);
        assertTrue(directory.mkdir());
        if (marked) {
            assertTrue(new File(directory, LayerFillStaging.MARKER_FILE_NAME).createNewFile());
        }
        return directory;
    }
}
