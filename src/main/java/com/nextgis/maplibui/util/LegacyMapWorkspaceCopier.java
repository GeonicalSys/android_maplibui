package com.nextgis.maplibui.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/** Copies the files owned by one legacy map into an isolated project workspace. */
final class LegacyMapWorkspaceCopier {
    private static final String JSON_LAYERS = "layers";
    private static final String JSON_PATH = "path";
    private static final String[] DATABASE_FILES = {
            "layers.db", "layers.db-journal", "layers.db-wal", "layers.db-shm"
    };

    private LegacyMapWorkspaceCopier() {
    }

    static void copy(File sourceMapFile, File destinationDir, String destinationMapName)
            throws IOException, JSONException {
        if (sourceMapFile == null || !sourceMapFile.isFile()) {
            throw new IOException("Legacy map file is missing");
        }
        if (destinationDir == null || destinationMapName == null
                || destinationMapName.trim().isEmpty()) {
            throw new IOException("Destination workspace is invalid");
        }
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw new IOException("Cannot create destination workspace");
        }

        File sourceDir = sourceMapFile.getParentFile();
        if (sourceDir == null) {
            throw new IOException("Legacy map directory is missing");
        }
        String sourceRoot = sourceDir.getCanonicalPath();
        String destinationRoot = destinationDir.getCanonicalPath();
        if (sourceRoot.equals(destinationRoot)) {
            throw new IOException("Source and destination workspaces are the same");
        }

        JSONObject mapJson = new JSONObject(readUtf8(sourceMapFile));
        JSONArray layers = mapJson.getJSONArray(JSON_LAYERS);
        Set<String> ownedEntries = new LinkedHashSet<>();
        for (int i = 0; i < layers.length(); i++) {
            JSONObject layer = layers.getJSONObject(i);
            String path = layer.getString(JSON_PATH);
            if (path == null || path.trim().isEmpty()) {
                throw new IOException("Legacy map contains an empty layer path");
            }
            ownedEntries.add(path);
        }

        for (String databaseFile : DATABASE_FILES) {
            if (new File(sourceDir, databaseFile).exists()) {
                ownedEntries.add(databaseFile);
            }
        }

        for (String entry : ownedEntries) {
            File source = resolveChild(sourceDir, sourceRoot, entry);
            if (!source.exists()) {
                continue;
            }
            File destination = resolveChild(destinationDir, destinationRoot, entry);
            copyRecursive(source, destination, sourceRoot, destinationRoot);
        }

        // The map config is copied last so an interrupted migration is never mistaken for a
        // complete workspace by the recovery scanner.
        File destinationMap = resolveChild(
                destinationDir, destinationRoot, destinationMapName + ".ngm");
        copyFile(sourceMapFile, destinationMap);
    }

    private static File resolveChild(File root, String canonicalRoot, String relativePath)
            throws IOException {
        File child = new File(root, relativePath);
        String canonicalChild = child.getCanonicalPath();
        if (!canonicalChild.startsWith(canonicalRoot + File.separator)) {
            throw new IOException("Workspace entry escapes its map directory");
        }
        return child;
    }

    private static void copyRecursive(
            File source,
            File destination,
            String sourceRoot,
            String destinationRoot) throws IOException {
        String canonicalSource = source.getCanonicalPath();
        String canonicalDestination = destination.getCanonicalPath();
        if (!canonicalSource.startsWith(sourceRoot + File.separator)
                || !canonicalDestination.startsWith(destinationRoot + File.separator)) {
            throw new IOException("Workspace copy escaped its expected directory");
        }
        if (source.isDirectory()) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw new IOException("Cannot create workspace directory");
            }
            File[] children = source.listFiles();
            if (children == null) {
                throw new IOException("Cannot read legacy workspace directory");
            }
            for (File child : children) {
                copyRecursive(
                        child,
                        new File(destination, child.getName()),
                        sourceRoot,
                        destinationRoot);
            }
            return;
        }
        copyFile(source, destination);
    }

    private static void copyFile(File source, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create workspace file directory");
        }
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            output.flush();
            output.getFD().sync();
        }
    }

    private static String readUtf8(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
