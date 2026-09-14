/*
 * Project: NextGIS Mobile
 * Purpose: Share exported GPX with a MimeTypeMap-known URI type.
 */
package com.nextgis.maplibui.util;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;

import java.io.File;
import java.util.Locale;

/**
 * Shares a GPX file for ACTION_SEND. Android's MimeTypeMap has no GPX entry, so
 * {@code application/gpx+xml} becomes a Java {@code "null"} suffix in some messengers.
 * The share Intent keeps that type for chooser filtering; the URI advertises
 * {@code text/xml} so MimeTypeMap clients append {@code .xml} instead of {@code .null}.
 */
public final class GpxSharePublisher {
    public static final String SHARE_INTENT_TYPE = "application/gpx+xml";
    public static final String URI_MIME_TYPE = "text/xml";

    private GpxSharePublisher() {
    }

    public static boolean isGpxName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".gpx");
    }

    public static Uri uriForShare(Context context, File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            String authority = context.getPackageName() + FileUtil.AUTHORITY;
            return FileProvider.getUriForFile(context, authority, file);
        } catch (IllegalArgumentException error) {
            HyperLog.w(Constants.TAG, "GPX FileProvider URI failed", error);
            return null;
        }
    }
}
