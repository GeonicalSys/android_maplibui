/*
 * Project: NextGIS Mobile
 * Purpose: Crash-safe lifecycle for unpublished layer-fill storage.
 */

package com.nextgis.maplibui.util;

import android.database.sqlite.SQLiteDatabase;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.DatabaseContext;
import com.nextgis.maplib.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Marks storage reserved by {@code LayerFillService} until the layer is durably published.
 * Only folders carrying this app-created marker are eligible for automatic crash cleanup.
 */
public final class LayerFillStaging {
    static final String MARKER_FILE_NAME = ".layer-fill-partial";
    private static final String LAYER_PREFIX = "layer_";

    private LayerFillStaging() {
    }

    public static boolean mark(LayerGroup targetGroup, File layerPath) {
        if (!isDirectLayerStorage(targetGroup, layerPath)) {
            HyperLog.w(Constants.TAG, "Layer fill staging refused path outside target group");
            return false;
        }
        File marker = marker(layerPath);
        try {
            return marker.exists() || marker.createNewFile();
        } catch (IOException | SecurityException exception) {
            HyperLog.w(Constants.TAG, "Layer fill staging marker failed folder="
                    + layerPath.getName() + " cause=" + exception.getClass().getSimpleName());
            return false;
        }
    }

    public static void complete(LayerGroup targetGroup, File layerPath) {
        if (!isDirectLayerStorage(targetGroup, layerPath)) {
            return;
        }
        File marker = marker(layerPath);
        if (marker.exists() && !marker.delete()) {
            HyperLog.w(Constants.TAG, "Layer fill staging marker remained folder="
                    + layerPath.getName());
        }
    }

    /**
     * Removes only unpublished directories carrying the staging marker. A marked directory that
     * is already referenced by the loaded map is preserved and merely committed by removing the
     * marker; this covers process death between {@code map.ngm} save and marker removal.
     */
    public static int cleanupIncomplete(LayerGroup targetGroup) {
        if (targetGroup == null || targetGroup.getPath() == null) {
            return 0;
        }
        Set<String> referenced = new HashSet<>();
        collectReferencedPaths(targetGroup, referenced);

        for (File path : findMarkedDirectories(targetGroup.getPath())) {
            if (referenced.contains(canonicalPath(path))) {
                complete(targetGroup, path);
            }
        }

        List<File> orphans = findMarkedOrphans(targetGroup.getPath(), referenced);
        if (orphans.isEmpty()) {
            return 0;
        }

        SQLiteDatabase database = DatabaseContext.getDatabaseForLayer(targetGroup, false);
        int removed = 0;
        database.beginTransaction();
        try {
            for (File orphan : orphans) {
                String tableName = orphan.getName();
                if (!isSafeLayerTableName(tableName)) {
                    continue;
                }
                dropTable(database, tableName);
                dropTable(database, tableName + Constants.CHANGES_NAME_POSTFIX);
                dropTable(database, tableName + Constants.ATTACHMENTS_NAME_POSTFIX);
                if (FileUtil.deleteRecursive(orphan)) {
                    removed++;
                } else {
                    HyperLog.w(Constants.TAG, "Layer fill staging cleanup could not remove folder="
                            + tableName);
                }
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        if (removed > 0) {
            HyperLog.w(Constants.TAG, "Layer fill staging cleanup removed=" + removed
                    + " targetGroup=" + targetGroup.getId());
        }
        return removed;
    }

    static List<File> findMarkedOrphans(File root, Set<String> referencedCanonicalPaths) {
        List<File> result = new ArrayList<>();
        Set<String> referenced = referencedCanonicalPaths != null
                ? referencedCanonicalPaths : new HashSet<>();
        for (File directory : findMarkedDirectories(root)) {
            if (!referenced.contains(canonicalPath(directory))) {
                result.add(directory);
            }
        }
        return result;
    }

    private static List<File> findMarkedDirectories(File root) {
        List<File> result = new ArrayList<>();
        if (root == null) {
            return result;
        }
        File[] children = root.listFiles();
        if (children == null) {
            return result;
        }
        for (File child : children) {
            if (child != null && child.isDirectory()
                    && isSafeLayerTableName(child.getName())
                    && marker(child).isFile()) {
                result.add(child);
            }
        }
        return result;
    }

    private static void collectReferencedPaths(LayerGroup group, Set<String> output) {
        for (ILayer layer : group.getLayers()) {
            if (layer == null) {
                continue;
            }
            if (layer.getPath() != null) {
                output.add(canonicalPath(layer.getPath()));
            }
            if (layer instanceof LayerGroup) {
                collectReferencedPaths((LayerGroup) layer, output);
            }
        }
    }

    private static boolean isDirectLayerStorage(LayerGroup group, File layerPath) {
        if (group == null || group.getPath() == null || layerPath == null
                || !isSafeLayerTableName(layerPath.getName())) {
            return false;
        }
        File parent = layerPath.getParentFile();
        return parent != null
                && canonicalPath(parent).equals(canonicalPath(group.getPath()));
    }

    private static boolean isSafeLayerTableName(String value) {
        if (value == null || value.isEmpty() || !value.startsWith(LAYER_PREFIX)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!(character == '_' || Character.isLetterOrDigit(character))) {
                return false;
            }
        }
        return true;
    }

    private static void dropTable(SQLiteDatabase database, String tableName) {
        database.execSQL("DROP TABLE IF EXISTS \"" + tableName.replace("\"", "\"\"") + "\"");
    }

    private static File marker(File directory) {
        return new File(directory, MARKER_FILE_NAME);
    }

    private static String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException exception) {
            return file.getAbsolutePath();
        }
    }
}
