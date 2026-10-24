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
                if (FireflyMarkerRegistry.isFirefly(marker)) {
                    Bitmap sprite = FireflyMarkerRegistry.sprite(marker).bitmap();
                    float inset = 2 * density;
                    display.mMainCanvas.drawBitmap(sprite, null, new RectF(inset, inset, size - inset, size - inset),
                            new android.graphics.Paint(3));
                    if (marker.getIconImage() == null) return bitmap;
                }
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
                if (FireflyMarkerRegistry.isFirefly(marker)) return bitmap;
                marker.setSize(Math.min(marker.getSize(), 13));
                geometry = new GeoPoint(size / 2d, size / 2d);
            } else if (style instanceof SimpleLineStyle) {
                ((SimpleLineStyle)style).setText(null);
                GeoLineString line = new GeoLineString();
                line.add(new GeoPoint(5*density, 28*density)); line.add(new GeoPoint(35*density, 12*density));
                geometry = line;
            } else {
                if (style instanceof SimplePolygonStyle) {
                    drawPolygon(display.mMainCanvas, (SimplePolygonStyle) style, density, size);
                    return bitmap;
                }
                GeoLinearRing ring = new GeoLinearRing();
                ring.add(new GeoPoint(5*density, 5*density)); ring.add(new GeoPoint(35*density, 5*density));
                ring.add(new GeoPoint(35*density, 35*density)); ring.add(new GeoPoint(5*density, 35*density));
                ring.add(new GeoPoint(5*density, 5*density));
                GeoPolygon polygon = new GeoPolygon(); polygon.setOuterRing(ring); geometry = polygon;
            }
            style.onDraw(geometry, display);
        } catch (CloneNotSupportedException error) { throw new IllegalStateException(error); }
        return bitmap;
    }
    private static void drawPolygon(Canvas canvas, SimplePolygonStyle style, float density, int size) {
        android.graphics.Paint paint = new android.graphics.Paint(3);
        android.graphics.Path path = new android.graphics.Path();
        float inset = 6 * density;
        path.addRect(inset,inset,size-inset,size-inset,android.graphics.Path.Direction.CW);
        if (style.isFill()) {
            paint.setColor(style.getColor()); paint.setAlpha(style.getAlpha()); canvas.drawPath(path,paint);
        }
        if ((style.getFillPattern() != 0 || style.getFillPatternImage() != null) && style.getPatternOpacity() > 0) {
            Bitmap pattern = ConfigurablePolygonPatternRegistry.applies(style) ? ConfigurablePolygonPatternRegistry.bitmap(style)
                    : PolygonPatternRegistry.previewPattern(style.getFillPattern(),style.getFillPatternImage());
            android.graphics.BitmapShader shader = new android.graphics.BitmapShader(pattern,android.graphics.Shader.TileMode.REPEAT,android.graphics.Shader.TileMode.REPEAT);
            android.graphics.Matrix matrix = new android.graphics.Matrix(); matrix.setScale(density,density); shader.setLocalMatrix(matrix);
            paint.setShader(shader); paint.setAlpha(Math.round(style.getPatternOpacity()*255)); canvas.drawPath(path,paint); paint.setShader(null);
        }
        PolygonOutlineOptions outline = style.getOutlineOptions();
        if (outline != null && !outline.isEnabled()) outline = null;
        float width = com.nextgis.maplib.map.MPLFeaturesUtils.getMPLThinkness(style.getWidth()) * density;
        if (outline != null && outline.isCasing()) stroke(canvas,path,outline.getCasingColor(),outline.getCasingAlpha(),
                width+2*outline.getCasingWidth()*density,outline.getGap()*density,null,outline);
        Float[] values = outline == null ? null : outline.dashes();
        float[] dash = null;
        if (values != null) { dash = new float[values.length]; for (int i=0;i<dash.length;i++) dash[i]=values[i]*width; }
        stroke(canvas,path,style.getOutColor(),style.getOutAlpha(),width,outline == null ? 0 : outline.getGap()*density,dash,outline);
    }
    private static void stroke(Canvas canvas,android.graphics.Path path,int color,int alpha,float width,float gap,float[] dash,PolygonOutlineOptions options) {
        android.graphics.Paint paint = new android.graphics.Paint(3); paint.setColor(color); paint.setAlpha(alpha);
        paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(gap > 0 ? gap+2*width : width);
        paint.setStrokeCap(options == null || options.getCap() == MplStyleMapper.LINE_CAP_ROUND ? android.graphics.Paint.Cap.ROUND
                : options.getCap() == MplStyleMapper.LINE_CAP_SQUARE ? android.graphics.Paint.Cap.SQUARE : android.graphics.Paint.Cap.BUTT);
        paint.setStrokeJoin(options == null || options.getJoin() == MplStyleMapper.LINE_JOIN_ROUND ? android.graphics.Paint.Join.ROUND
                : options.getJoin() == MplStyleMapper.LINE_JOIN_MITER ? android.graphics.Paint.Join.MITER : android.graphics.Paint.Join.BEVEL);
        if (options != null) paint.setStrokeMiter(options.getMiter());
        if (dash != null) paint.setPathEffect(new android.graphics.DashPathEffect(dash,0));
        int layer = canvas.saveLayer(0,0,canvas.getWidth(),canvas.getHeight(),null);
        canvas.drawPath(path,paint);
        if (gap > 0) {
            paint.setPathEffect(null); paint.setStrokeWidth(gap); paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
            canvas.drawPath(path,paint);
        }
        canvas.restoreToCount(layer);
    }
}
