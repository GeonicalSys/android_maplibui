/*
 * Project:  NextGIS Mobile
 * Purpose:  Burn coordinates and optional time onto JPEG photo attachments.
 */

package com.nextgis.maplibui.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.media.ExifInterface;
import android.net.Uri;

import com.nextgis.maplib.util.LocationUtil;
import com.nextgis.maplibui.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class PhotoOverlayUtil {

    private static final int JPEG_QUALITY = 90;

    private PhotoOverlayUtil() {
    }

    public static boolean process(
            Context context,
            String path,
            OutputStream outputStream,
            PhotoOverlayData data,
            int coordFormat,
            int coordFraction)
            throws IOException
    {
        File outputFile = File.createTempFile("photo_overlay_out_", ".jpg", context.getCacheDir());
        try {
            if (!processToFile(context, path, outputFile, data, coordFormat, coordFraction)) {
                return false;
            }
            copyFileToStream(outputFile, outputStream);
            return true;
        } finally {
            outputFile.delete();
        }
    }

    public static boolean processToFile(
            Context context,
            String path,
            File outputFile,
            PhotoOverlayData data,
            int coordFormat,
            int coordFraction)
            throws IOException
    {
        if (data == null || !data.hasContent()) {
            return false;
        }

        File sourceFile = materializeSource(context, path);
        if (sourceFile == null) {
            return false;
        }

        boolean deleteSource = !path.startsWith("/");
        try {
            return processDecodedFileToOutput(
                    sourceFile.getAbsolutePath(),
                    outputFile,
                    data,
                    context,
                    coordFormat,
                    coordFraction);
        } finally {
            if (deleteSource) {
                sourceFile.delete();
            }
        }
    }

    private static boolean processDecodedFileToOutput(
            String sourcePath,
            File outputFile,
            PhotoOverlayData data,
            Context context,
            int coordFormat,
            int coordFraction)
            throws IOException
    {
        Bitmap bitmap = decodeOrientedBitmap(sourcePath);
        if (bitmap == null) {
            return false;
        }

        try {
            bitmap = ensureMutable(bitmap);
            drawOverlay(context, bitmap, data, coordFormat, coordFraction);

            FileOutputStream bitmapOut = new FileOutputStream(outputFile);
            try {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bitmapOut)) {
                    return false;
                }
            } finally {
                bitmapOut.close();
            }

            writeExif(outputFile, data);
            return outputFile.length() > 0;
        } finally {
            bitmap.recycle();
        }
    }

    private static Bitmap ensureMutable(Bitmap bitmap) {
        if (bitmap.isMutable()) {
            return bitmap;
        }
        Bitmap mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        if (mutable == null) {
            throw new IllegalStateException("Failed to create mutable bitmap copy");
        }
        bitmap.recycle();
        return mutable;
    }

    private static File materializeSource(Context context, String path)
            throws IOException
    {
        if (path.startsWith("/")) {
            File file = new File(path);
            return file.exists() ? file : null;
        }

        File tempFile = File.createTempFile("photo_overlay_in_", ".jpg", context.getCacheDir());
        InputStream inputStream = context.getContentResolver().openInputStream(Uri.parse(path));
        if (inputStream == null) {
            tempFile.delete();
            return null;
        }
        try {
            copyStream(inputStream, new FileOutputStream(tempFile));
        } finally {
            inputStream.close();
        }
        return tempFile;
    }

    private static Bitmap decodeOrientedBitmap(String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inMutable = true;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) {
            return null;
        }

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try {
            ExifInterface exif = new ExifInterface(path);
            orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) {
        }

        return rotateBitmap(bitmap, orientation);
    }

    private static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            case ExifInterface.ORIENTATION_NORMAL:
            default:
                return bitmap;
        }

        try {
            Bitmap rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (OutOfMemoryError error) {
            error.printStackTrace();
            return bitmap;
        }
    }

    private static void drawOverlay(
            Context context,
            Bitmap bitmap,
            PhotoOverlayData data,
            int coordFormat,
            int coordFraction)
    {
        List<String> lines = new ArrayList<>();
        if (data.hasCoordinates()) {
            String lat = context.getString(R.string.latitude_caption_short) + ": "
                    + LocationUtil.formatLatitude(
                    data.latitude, coordFormat, coordFraction, context.getResources());
            String lon = context.getString(R.string.longitude_caption_short) + ": "
                    + LocationUtil.formatLongitude(
                    data.longitude, coordFormat, coordFraction, context.getResources());
            lines.add(lat);
            lines.add(lon);
        }
        if (data.hasTimestamp()) {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date(data.timestamp));
            lines.add(time);
        }
        if (lines.isEmpty()) {
            return;
        }

        Canvas canvas = new Canvas(bitmap);
        float textSize = Math.max(24f, Math.min(bitmap.getWidth(), bitmap.getHeight()) / 28f);
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(textSize);
        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);

        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float lineHeight = metrics.descent - metrics.ascent;
        float padding = textSize * 0.35f;
        float maxWidth = 0f;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, textPaint.measureText(line));
        }

        float boxWidth = maxWidth + padding * 2f;
        float boxHeight = lineHeight * lines.size() + padding * 2f;
        float left = padding;
        float top = bitmap.getHeight() - boxHeight - padding;

        Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.argb(160, 0, 0, 0));
        canvas.drawRoundRect(
                new RectF(left, top, left + boxWidth, top + boxHeight),
                8f,
                8f,
                backgroundPaint);

        float textY = top + padding - metrics.ascent;
        for (String line : lines) {
            canvas.drawText(line, left + padding, textY, textPaint);
            textY += lineHeight;
        }
    }

    private static void writeExif(File outputFile, PhotoOverlayData data)
            throws IOException
    {
        if (data.hasCoordinates()) {
            Location location = LocationUtil.locationFromLatLon(data.latitude, data.longitude);
            LocationUtil.writeLocationToExif(outputFile, location);
        }
        if (data.hasTimestamp()) {
            LocationUtil.writeDateTimeToExif(outputFile, data.timestamp);
        }
        LocationUtil.setExifOrientationNormal(outputFile);
    }

    private static void copyFileToStream(File file, OutputStream outputStream)
            throws IOException
    {
        FileInputStream inputStream = new FileInputStream(file);
        try {
            copyStream(inputStream, outputStream);
        } finally {
            inputStream.close();
        }
    }

    private static void copyStream(InputStream inputStream, OutputStream outputStream)
            throws IOException
    {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, read);
        }
        outputStream.flush();
    }
}
