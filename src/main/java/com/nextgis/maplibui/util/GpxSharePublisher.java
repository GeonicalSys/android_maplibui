/*
 * Project: NextGIS Mobile
 * Purpose: Share exported GPX with a MediaStore display name that messengers can keep.
 */
package com.nextgis.maplibui.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Publishes a GPX file for ACTION_SEND. Android's MimeTypeMap has no GPX entry, so
 * {@code application/gpx+xml} becomes a Java {@code "null"} suffix in some messengers.
 * Downloads rows keep the {@code .gpx} display name and advertise {@code text/xml}.
 */
public final class GpxSharePublisher {
    public static final String SHARE_INTENT_TYPE = "application/gpx+xml";
    public static final String URI_MIME_TYPE = "text/xml";
    public static final int DOWNLOADS_MIN_SDK = 29;
    static final String DEFAULT_DOWNLOAD_FOLDER = "LISA";
    static final String DOWNLOAD_DIRECTORY = "Download";

    private GpxSharePublisher() {
    }

    public static boolean canPublishToDownloads(int sdkInt) {
        return sdkInt >= DOWNLOADS_MIN_SDK;
    }

    public static boolean isGpxName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".gpx");
    }

    static String downloadFolderName(CharSequence appLabel) {
        if (appLabel == null) {
            return DEFAULT_DOWNLOAD_FOLDER;
        }
        StringBuilder folder = new StringBuilder();
        CharSequence label = appLabel.toString().trim();
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (c < 32 || "/\\:*?\"<>|".indexOf(c) >= 0) {
                if (folder.length() > 0 && folder.charAt(folder.length() - 1) != '_') {
                    folder.append('_');
                }
                continue;
            }
            folder.append(c);
        }
        String name = folder.toString().trim();
        while (name.startsWith("_")) {
            name = name.substring(1);
        }
        while (name.endsWith("_")) {
            name = name.substring(0, name.length() - 1);
        }
        return name.isEmpty() ? DEFAULT_DOWNLOAD_FOLDER : name;
    }

    static String downloadRelativePath(CharSequence appLabel) {
        return DOWNLOAD_DIRECTORY + "/" + downloadFolderName(appLabel);
    }

    public static Uri uriForShare(Context context, File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        if (canPublishToDownloads(Build.VERSION.SDK_INT)) {
            Uri published = publishToDownloads(context, file);
            if (published != null) {
                return published;
            }
        }
        return fileProviderUri(context, file);
    }

    private static Uri publishToDownloads(Context context, File file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null;
        }
        ContentResolver resolver = context.getContentResolver();
        CharSequence label = context.getApplicationInfo().loadLabel(context.getPackageManager());
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
        values.put(MediaStore.MediaColumns.MIME_TYPE, URI_MIME_TYPE);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, downloadRelativePath(label));
        Uri uri = null;
        try {
            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                return null;
            }
            try (InputStream in = new FileInputStream(file);
                 OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) {
                    resolver.delete(uri, null, null);
                    return null;
                }
                FileUtil.copy(in, out);
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return uri;
        } catch (RuntimeException | IOException error) {
            HyperLog.w(Constants.TAG, "GPX Downloads publish failed, using FileProvider", error);
            if (uri != null) {
                try {
                    resolver.delete(uri, null, null);
                } catch (RuntimeException ignored) {
                }
            }
            return null;
        }
    }

    private static Uri fileProviderUri(Context context, File file) {
        try {
            String authority = context.getPackageName() + FileUtil.AUTHORITY;
            return FileProvider.getUriForFile(context, authority, file);
        } catch (IllegalArgumentException error) {
            HyperLog.w(Constants.TAG, "GPX FileProvider URI failed", error);
            return null;
        }
    }
}
