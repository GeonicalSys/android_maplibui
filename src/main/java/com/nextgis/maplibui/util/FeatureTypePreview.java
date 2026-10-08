package com.nextgis.maplibui.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import com.nextgis.maplib.datasource.*;
import com.nextgis.maplib.display.*;
import java.io.InputStream;

/** Render a private copy of the actual category symbol without a live map or renderer. */
public final class FeatureTypePreview extends GISDisplay {
    private FeatureTypePreview(Bitmap bitmap, float density) {
        super(null);
        mMainBitmap = bitmap; mMainCanvas = new Canvas(bitmap);
        mScale = 1d / density;
        mCurrentBounds = new GeoEnvelope(0, bitmap.getWidth(), 0, bitmap.getHeight());
    }

    public static Bitmap render(Context context, Style source) {
        float density = context.getResources().getDisplayMetrics().density;
        int size = Math.round(40 * density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        if (source == null) return bitmap;
        FeatureTypePreview display = new FeatureTypePreview(bitmap, density);
        try {
            Style style = source.clone();
            GeoGeometry geometry;
            if (style instanceof SimpleMarkerStyle) {
                SimpleMarkerStyle marker = (SimpleMarkerStyle)style;
                marker.setText(null);
                String image = marker.getIconImage();
                if (image != null && !image.contains("/") && !image.contains("\\")) {
                    try (InputStream input = context.getAssets().open("marker_icons/" + image + ".png")) {
                        Bitmap icon = BitmapFactory.decodeStream(input);
                        if (icon != null) {
                            float inset = 4 * density;
                            float ratio = Math.min((size - 2 * inset) / icon.getWidth(), (size - 2 * inset) / icon.getHeight());
                            float width = icon.getWidth() * ratio, height = icon.getHeight() * ratio;
                            display.mMainCanvas.rotate(marker.getIconRotate(), size / 2f, size / 2f);
                            display.mMainCanvas.drawBitmap(icon, null, new RectF((size-width)/2, (size-height)/2,
                                    (size+width)/2, (size+height)/2), new android.graphics.Paint(3));
                            return bitmap;
                        }
                    } catch (java.io.IOException ignored) { /* Render the configured primitive fallback. */ }
                }
                marker.setSize(Math.min(marker.getSize(), 13));
                geometry = new GeoPoint(size / 2d, size / 2d);
            } else if (style instanceof SimpleLineStyle) {
                ((SimpleLineStyle)style).setText(null);
                GeoLineString line = new GeoLineString();
                line.add(new GeoPoint(5*density, 28*density)); line.add(new GeoPoint(35*density, 12*density));
                geometry = line;
            } else {
                if (style instanceof SimplePolygonStyle) ((SimplePolygonStyle)style).setText(null);
                GeoLinearRing ring = new GeoLinearRing();
                ring.add(new GeoPoint(5*density, 5*density)); ring.add(new GeoPoint(35*density, 5*density));
                ring.add(new GeoPoint(35*density, 35*density)); ring.add(new GeoPoint(5*density, 35*density));
                ring.add(new GeoPoint(5*density, 5*density));
                GeoPolygon polygon = new GeoPolygon(); polygon.setOuterRing(ring); geometry = polygon;
            }
            style.onDraw(geometry, display);
            if (style instanceof SimplePolygonStyle) {
                SimplePolygonStyle polygon = (SimplePolygonStyle)style;
                if (polygon.isFill() && (polygon.getFillPattern() != PolygonPatternRegistry.FILL_PATTERN_NONE
                        || polygon.getFillPatternImage() != null)) {
                    Bitmap pattern = PolygonPatternRegistry.previewPattern(polygon.getFillPattern(), polygon.getFillPatternImage());
                    android.graphics.Paint paint = new android.graphics.Paint(3);
                    paint.setShader(new android.graphics.BitmapShader(pattern, android.graphics.Shader.TileMode.REPEAT,
                            android.graphics.Shader.TileMode.REPEAT));
                    paint.setAlpha(polygon.getAlpha());
                    display.mMainCanvas.drawRect(6*density,6*density,34*density,34*density,paint);
                }
            }
        } catch (CloneNotSupportedException error) { throw new IllegalStateException(error); }
        return bitmap;
    }
}
