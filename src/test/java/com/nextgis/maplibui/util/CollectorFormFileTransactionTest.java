package com.nextgis.maplibui.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CollectorFormFileTransactionTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void installReplacesBothFiles() throws Exception {
        File layer = temporaryFolder.newFolder("layer");
        File oldForm = write(layer, "42_form.json", "old-form");
        File oldMeta = write(layer, "42_ngfp_meta.json", "old-meta");
        File sourceDir = temporaryFolder.newFolder("source");
        File newForm = write(sourceDir, "form.json", "new-form");
        File newMeta = write(sourceDir, "meta.json", "new-meta");

        CollectorFormFileTransaction.install(layer, newForm, newMeta, oldForm, oldMeta);

        assertEquals("new-form", read(oldForm));
        assertEquals("new-meta", read(oldMeta));
        assertFalse(new File(layer, CollectorFormFileTransaction.MARKER_NAME).exists());
    }

    @Test
    public void recoverRestoresOriginalPairBeforeCommit() throws Exception {
        File layer = temporaryFolder.newFolder("layer");
        File targetForm = write(layer, "42_form.json", "new-form");
        File targetMeta = write(layer, "42_ngfp_meta.json", "new-meta");
        File backupForm = write(layer, ".collector_form_x.form.bak", "old-form");
        File backupMeta = write(layer, ".collector_form_x.meta.bak", "old-meta");
        File stageForm = write(layer, ".collector_form_x.form.new", "stage-form");
        File stageMeta = write(layer, ".collector_form_x.meta.new", "stage-meta");
        writeMarker(layer, targetForm, targetMeta, stageForm, stageMeta,
                backupForm, backupMeta, true, true);

        CollectorFormFileTransaction.recover(layer);

        assertEquals("old-form", read(targetForm));
        assertEquals("old-meta", read(targetMeta));
        assertFalse(stageForm.exists());
        assertFalse(stageMeta.exists());
        assertFalse(new File(layer, CollectorFormFileTransaction.MARKER_NAME).exists());
    }

    @Test
    public void recoverRemovesPartialTargetsThatDidNotExistBefore() throws Exception {
        File layer = temporaryFolder.newFolder("layer");
        File targetForm = write(layer, "77_form.json", "partial-new-form");
        File targetMeta = new File(layer, "77_ngfp_meta.json");
        File stageForm = write(layer, ".collector_form_y.form.new", "stage-form");
        File stageMeta = write(layer, ".collector_form_y.meta.new", "stage-meta");
        File backupForm = new File(layer, ".collector_form_y.form.bak");
        File backupMeta = new File(layer, ".collector_form_y.meta.bak");
        writeMarker(layer, targetForm, targetMeta, stageForm, stageMeta,
                backupForm, backupMeta, false, false);

        CollectorFormFileTransaction.recover(layer);

        assertFalse(targetForm.exists());
        assertFalse(targetMeta.exists());
        assertFalse(new File(layer, CollectorFormFileTransaction.MARKER_NAME).exists());
    }

    @Test
    public void invalidSourceLeavesOldPairUntouched() throws Exception {
        File layer = temporaryFolder.newFolder("layer");
        File targetForm = write(layer, "42_form.json", "old-form");
        File targetMeta = write(layer, "42_ngfp_meta.json", "old-meta");
        File sourceDir = temporaryFolder.newFolder("source");
        File newForm = write(sourceDir, "form.json", "new-form");
        File emptyMeta = new File(sourceDir, "meta.json");
        assertTrue(emptyMeta.createNewFile());

        try {
            CollectorFormFileTransaction.install(
                    layer, newForm, emptyMeta, targetForm, targetMeta);
        } catch (Exception expected) {
            // Expected validation failure.
        }

        assertEquals("old-form", read(targetForm));
        assertEquals("old-meta", read(targetMeta));
    }

    private static void writeMarker(
            File layer,
            File targetForm,
            File targetMeta,
            File stageForm,
            File stageMeta,
            File backupForm,
            File backupMeta,
            boolean formHadOld,
            boolean metaHadOld) throws Exception {
        Properties state = new Properties();
        state.setProperty("form_target", targetForm.getName());
        state.setProperty("meta_target", targetMeta.getName());
        state.setProperty("form_stage", stageForm.getName());
        state.setProperty("meta_stage", stageMeta.getName());
        state.setProperty("form_backup", backupForm.getName());
        state.setProperty("meta_backup", backupMeta.getName());
        state.setProperty("form_had_old", Boolean.toString(formHadOld));
        state.setProperty("meta_had_old", Boolean.toString(metaHadOld));
        try (FileOutputStream out = new FileOutputStream(
                new File(layer, CollectorFormFileTransaction.MARKER_NAME))) {
            state.store(out, "test");
        }
    }

    private static File write(File parent, String name, String value) throws Exception {
        File file = new File(parent, name);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(value);
        }
        return file;
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
