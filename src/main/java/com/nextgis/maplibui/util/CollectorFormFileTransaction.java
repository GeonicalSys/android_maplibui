/*
 * Project:  NextGIS Mobile
 * Purpose:  Crash-recoverable replacement of Collector NGFP sidecar files.
 */

package com.nextgis.maplibui.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Replaces the two files that make up an unpacked NGFP form as one recoverable transaction.
 *
 * <p>The filesystem cannot atomically rename two files together. A small marker and same-directory
 * backups therefore define the commit boundary. If the process stops before the marker is removed,
 * {@link #recover(File)} restores the previous pair. Once the marker is removed, the new pair is the
 * committed version and any remaining backup files are harmless cleanup leftovers.</p>
 */
public final class CollectorFormFileTransaction {
    static final String MARKER_NAME = ".collector_form_transaction.properties";
    private static final String MARKER_TEMP_NAME = MARKER_NAME + ".tmp";

    private static final String KEY_FORM_TARGET = "form_target";
    private static final String KEY_META_TARGET = "meta_target";
    private static final String KEY_FORM_STAGE = "form_stage";
    private static final String KEY_META_STAGE = "meta_stage";
    private static final String KEY_FORM_BACKUP = "form_backup";
    private static final String KEY_META_BACKUP = "meta_backup";
    private static final String KEY_FORM_HAD_OLD = "form_had_old";
    private static final String KEY_META_HAD_OLD = "meta_had_old";

    private CollectorFormFileTransaction() {
    }

    public static synchronized void install(
            File layerPath,
            File sourceForm,
            File sourceMeta,
            File targetForm,
            File targetMeta) throws IOException {
        requireDirectory(layerPath);
        requireSource(sourceForm);
        requireSource(sourceMeta);
        requireChild(layerPath, targetForm);
        requireChild(layerPath, targetMeta);

        recover(layerPath);

        String token = Long.toHexString(System.currentTimeMillis())
                + "_" + Long.toHexString(System.nanoTime());
        File formStage = new File(layerPath, ".collector_form_" + token + ".form.new");
        File metaStage = new File(layerPath, ".collector_form_" + token + ".meta.new");
        File formBackup = new File(layerPath, ".collector_form_" + token + ".form.bak");
        File metaBackup = new File(layerPath, ".collector_form_" + token + ".meta.bak");

        copyAndSync(sourceForm, formStage);
        copyAndSync(sourceMeta, metaStage);

        Properties state = new Properties();
        state.setProperty(KEY_FORM_TARGET, targetForm.getName());
        state.setProperty(KEY_META_TARGET, targetMeta.getName());
        state.setProperty(KEY_FORM_STAGE, formStage.getName());
        state.setProperty(KEY_META_STAGE, metaStage.getName());
        state.setProperty(KEY_FORM_BACKUP, formBackup.getName());
        state.setProperty(KEY_META_BACKUP, metaBackup.getName());
        state.setProperty(KEY_FORM_HAD_OLD, Boolean.toString(targetForm.exists()));
        state.setProperty(KEY_META_HAD_OLD, Boolean.toString(targetMeta.exists()));
        writeMarker(layerPath, state);

        try {
            moveExistingToBackup(targetForm, formBackup);
            moveExistingToBackup(targetMeta, metaBackup);

            // Form is the commit-visible payload. Install metadata first and the form last.
            renameChecked(metaStage, targetMeta);
            renameChecked(formStage, targetForm);
            requireSource(targetForm);
            requireSource(targetMeta);

            File marker = new File(layerPath, MARKER_NAME);
            if (!marker.delete()) {
                throw new IOException("Cannot commit Collector form transaction " + marker);
            }

            // Marker removal is the commit point. Leftover backups are ignored after a crash.
            deleteQuietly(formBackup);
            deleteQuietly(metaBackup);
            deleteQuietly(new File(layerPath, MARKER_TEMP_NAME));
        } catch (IOException error) {
            try {
                recover(layerPath);
            } catch (IOException recoveryError) {
                error.addSuppressed(recoveryError);
            }
            throw error;
        }
    }

    public static synchronized void recover(File layerPath) throws IOException {
        if (layerPath == null) {
            return;
        }
        File marker = new File(layerPath, MARKER_NAME);
        if (!marker.exists()) {
            deleteQuietly(new File(layerPath, MARKER_TEMP_NAME));
            cleanupCommittedBackups(layerPath);
            return;
        }

        Properties state = new Properties();
        try (InputStream in = new FileInputStream(marker)) {
            state.load(in);
        }

        restoreOne(
                child(layerPath, state, KEY_FORM_TARGET),
                child(layerPath, state, KEY_FORM_BACKUP),
                Boolean.parseBoolean(state.getProperty(KEY_FORM_HAD_OLD, "false")));
        restoreOne(
                child(layerPath, state, KEY_META_TARGET),
                child(layerPath, state, KEY_META_BACKUP),
                Boolean.parseBoolean(state.getProperty(KEY_META_HAD_OLD, "false")));

        deleteQuietly(child(layerPath, state, KEY_FORM_STAGE));
        deleteQuietly(child(layerPath, state, KEY_META_STAGE));
        deleteQuietly(child(layerPath, state, KEY_FORM_BACKUP));
        deleteQuietly(child(layerPath, state, KEY_META_BACKUP));
        if (!marker.delete() && marker.exists()) {
            throw new IOException("Cannot clear recovered Collector form transaction " + marker);
        }
        deleteQuietly(new File(layerPath, MARKER_TEMP_NAME));
    }

    private static void restoreOne(File target, File backup, boolean hadOld) throws IOException {
        if (hadOld) {
            if (backup != null && backup.exists()) {
                deleteChecked(target);
                renameChecked(backup, target);
            }
            // No backup means the process stopped before moving the original, or after commit
            // cleanup. In either case the existing target is the safest complete copy to keep.
        } else {
            deleteChecked(target);
        }
    }

    private static void moveExistingToBackup(File target, File backup) throws IOException {
        if (target.exists()) {
            renameChecked(target, backup);
        }
    }

    private static void writeMarker(File layerPath, Properties state) throws IOException {
        File temp = new File(layerPath, MARKER_TEMP_NAME);
        File marker = new File(layerPath, MARKER_NAME);
        try (FileOutputStream out = new FileOutputStream(temp, false)) {
            state.store(out, "Collector form transaction");
            out.getFD().sync();
        }
        deleteChecked(marker);
        renameChecked(temp, marker);
    }

    private static void copyAndSync(File source, File target) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target, false)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            out.getFD().sync();
        } catch (IOException error) {
            deleteQuietly(target);
            throw error;
        }
    }

    private static File child(File parent, Properties state, String key) throws IOException {
        String name = state.getProperty(key, "");
        if (name.isEmpty() || name.contains("/") || name.contains("\\")) {
            throw new IOException("Invalid Collector form transaction entry " + key);
        }
        return new File(parent, name);
    }

    private static void requireDirectory(File directory) throws IOException {
        if (directory == null || (!directory.isDirectory()
                && !directory.mkdirs() && !directory.isDirectory())) {
            throw new IOException("Cannot create Collector form directory " + directory);
        }
    }

    private static void requireSource(File source) throws IOException {
        if (source == null || !source.isFile() || source.length() <= 0L) {
            throw new IOException("Missing or empty Collector form file " + source);
        }
    }

    private static void requireChild(File parent, File child) throws IOException {
        if (child == null || child.getParentFile() == null
                || !parent.equals(child.getParentFile())) {
            throw new IOException("Collector form target is outside layer directory " + child);
        }
    }

    private static void renameChecked(File source, File target) throws IOException {
        if (source == null || target == null || !source.renameTo(target)) {
            throw new IOException("Cannot rename " + source + " to " + target);
        }
    }

    private static void deleteChecked(File file) throws IOException {
        if (file != null && file.exists() && !file.delete()) {
            throw new IOException("Cannot delete " + file);
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static void cleanupCommittedBackups(File layerPath) {
        File[] files = layerPath.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith(".collector_form_")
                    && (name.endsWith(".bak") || name.endsWith(".new"))) {
                deleteQuietly(file);
            }
        }
    }
}
