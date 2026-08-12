package com.nextgis.maplibui.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LayerBackupManagerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void backupFeatures_rejectsInvalidLayer() {
        LayerBackupManager.BackupResult result = LayerBackupManager.backupFeatures(
                null, null, Arrays.asList(1L),
                LayerBackupManager.REASON_MANUAL_FEATURE_DELETE);
        assertFalse(result.isSuccess());

        result = LayerBackupManager.backupFeatures(
                null, null, Collections.<Long>emptyList(),
                LayerBackupManager.REASON_MANUAL_FEATURE_DELETE);
        assertFalse(result.isSuccess());
    }

    @Test
    public void enforceBackupQuota_deletesOldestFirst() throws Exception {
        File root = temporaryFolder.newFolder("LayerBackups");
        File oldest = writeZip(root, "a_old.zip", 40);
        Thread.sleep(30);
        writeZip(root, "b_mid.zip", 40);
        Thread.sleep(30);
        File newest = writeZip(root, "c_new.zip", 40);

        int deleted = LayerBackupManager.enforceBackupQuotaOnRoot(root, 50L, newest);
        assertTrue(deleted >= 1);
        assertFalse(oldest.exists());
        assertTrue(newest.exists());
        assertTrue(com.nextgis.maplib.util.FileUtil.getDirectorySize(root) <= 80L);
    }

    @Test
    public void enforceBackupQuota_skipsProtectWhileOlderRemain() throws Exception {
        File root = temporaryFolder.newFolder("quota");
        File older = writeZip(root, "older.zip", 30);
        Thread.sleep(30);
        File protect = writeZip(root, "protect.zip", 30);

        int deleted = LayerBackupManager.enforceBackupQuotaOnRoot(root, 10L, protect);
        assertEquals(1, deleted);
        assertFalse(older.exists());
        assertTrue(protect.exists());
    }

    @Test
    public void enforceBackupQuota_noOpWhenUnderLimit() throws Exception {
        File root = temporaryFolder.newFolder("under");
        File only = writeZip(root, "only.zip", 20);
        int deleted = LayerBackupManager.enforceBackupQuotaOnRoot(root, 100L, only);
        assertEquals(0, deleted);
        assertTrue(only.exists());
    }

    private File writeZip(File root, String name, int size) throws IOException {
        File file = new File(root, name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            byte[] payload = new byte[size];
            Arrays.fill(payload, (byte) 7);
            out.write(payload);
        }
        assertTrue(file.setLastModified(System.currentTimeMillis()));
        return file;
    }
}
