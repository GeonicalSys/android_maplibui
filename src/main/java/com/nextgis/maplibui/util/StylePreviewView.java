package com.nextgis.maplibui.util;

import android.content.Context;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import com.nextgis.maplib.display.*;
import com.nextgis.maplib.map.MplFeatureStyleProps;
import com.nextgis.maplib.map.RuleGeometryLayouts;
import com.nextgis.maplib.map.mpl.*;
import com.nextgis.maplib.util.GeoConstants;
import org.maplibre.android.maps.*;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.*;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.geojson.*;
import java.util.*;
import java.util.function.Supplier;

/** Live, offline native rendering with exactly the production geometry factories and textures. */
public final class StylePreviewView extends FrameLayout {
    private static final String SOURCE = "style-preview-source";
    private final MapView mapView;
    private final Supplier<com.nextgis.maplib.display.Style> supplier;
    private MapLibreMap map;
    private org.maplibre.android.maps.Style nativeStyle;
    private String appliedJson;
    private boolean destroyed, started;
    private int revision;
    private final ViewTreeObserver.OnPreDrawListener changes = () -> { refresh(); return true; };
    public StylePreviewView(Context context, Supplier<com.nextgis.maplib.display.Style> supplier) {
        super(context); this.supplier = supplier;
        setContentDescription(context.getString(com.nextgis.maplibui.R.string.style_live_preview));
        mapView = new MapView(context, new MapLibreMapOptions().textureMode(true));
        addView(mapView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        mapView.onCreate(null);
        mapView.getMapAsync(ready -> {
            if (destroyed) return;
            map = ready; map.getUiSettings().setAllGesturesEnabled(false);
            map.getUiSettings().setCompassEnabled(false); map.getUiSettings().setLogoEnabled(false);
            map.getUiSettings().setAttributionEnabled(false);
            map.setStyle(new org.maplibre.android.maps.Style.Builder().fromJson(
                    "{\"version\":8,\"sources\":{},\"layers\":[{\"id\":\"preview-background\",\"type\":\"background\",\"paint\":{\"background-color\":\"#e8e8e8\"}}]}"), style -> {
                if (destroyed) return;
                nativeStyle = style; style.addSource(new GeoJsonSource(SOURCE, FeatureCollection.fromFeatures(new Feature[0])));
                refresh(); fitSample();
            });
        });
        addOnLayoutChangeListener((v,l,t,right,b,ol,ot,or,ob) -> { if (right-l != or-ol || b-t != ob-ot) fitSample(); });
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); getViewTreeObserver().addOnPreDrawListener(changes); }
    @Override protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(changes); super.onDetachedFromWindow();
    }
    public void start() { if (!destroyed && !started) { started = true; mapView.onStart(); mapView.onResume(); } }
    public void stop() { if (!destroyed && started) { started = false; mapView.onPause(); mapView.onStop(); } }
    public void destroy() {
        if (destroyed) return; stop(); destroyed = true;
        getViewTreeObserver().removeOnPreDrawListener(changes);
        clearLayers(); mapView.onDestroy(); nativeStyle = null; map = null;
    }
    public int getRevision() { return revision; }
    public MapLibreMap getPreviewMap() { return map; }
    private void fitSample() {
        if (map != null && getWidth() > 0 && getHeight() > 0) map.moveCamera(CameraUpdateFactory.newLatLngBounds(
                new LatLngBounds.Builder().include(new LatLng(-.00065,-.0022)).include(new LatLng(.00065,.0022)).build(),
                Math.round(12 * getResources().getDisplayMetrics().density)));
    }
    private void clearLayers() {
        if (nativeStyle == null) return;
        for (Layer layer : nativeStyle.getLayers()) if (layer.getId().startsWith("preview-layer-")) nativeStyle.removeLayer(layer);
        FireflyMarkerRegistry.remove(nativeStyle, "preview-layer-1");
        ConfigurablePolygonPatternRegistry.remove(nativeStyle, "preview-layer-1");
    }
    private void refresh() {
        if (destroyed || nativeStyle == null) return;
        com.nextgis.maplib.display.Style value = supplier.get(); if (value == null) return;
        try {
            String json = value.toJSON().toString(); if (json.equals(appliedJson)) return;
            com.nextgis.maplib.display.Style style = value.clone();
            if (style instanceof SimpleMarkerStyle) ((SimpleMarkerStyle) style).setText(null);
            if (style instanceof SimpleLineStyle) ((SimpleLineStyle) style).setText(null);
            if (style instanceof SimplePolygonStyle) ((SimplePolygonStyle) style).setText(null);
            int geometryType = style instanceof SimpleMarkerStyle ? GeoConstants.GTPoint
                    : style instanceof SimpleLineStyle ? GeoConstants.GTLineString : GeoConstants.GTPolygon;
            Geometry geometry = geometryType == GeoConstants.GTPoint ? Point.fromLngLat(0,0)
                    : geometryType == GeoConstants.GTLineString ? LineString.fromLngLats(Arrays.asList(
                    Point.fromLngLat(-.0018,-.0004), Point.fromLngLat(-.0006,.0004), Point.fromLngLat(.0006,-.0004), Point.fromLngLat(.0018,.0004)))
                    : Polygon.fromLngLats(Collections.singletonList(Arrays.asList(Point.fromLngLat(-.0018,-.0004),
                    Point.fromLngLat(.0018,-.0004), Point.fromLngLat(.0018,.0004), Point.fromLngLat(-.0018,.0004), Point.fromLngLat(-.0018,-.0004))));
            Feature feature = Feature.fromGeometry(geometry); MplFeatureStyleProps.apply(style, feature, geometryType);
            feature.addNumberProperty(MplFeatureStyleProps.ORDER, 1);
            clearLayers();
            GeoJsonSource source = nativeStyle.getSourceAs(SOURCE); source.setGeoJson(FeatureCollection.fromFeatures(new Feature[]{feature}));
            MplLayerBuildContext ctx = new MplLayerBuildContext(1, geometryType, nativeStyle, false, SOURCE,
                    "preview-", 1, MplLayerStyleVars.from(style, geometryType), getContext().getAssets(), false,
                    null, new HashMap<>(), new HashMap<>(), null);
            MplLayerBuildResult result = new MplLayerBuildResult();
            if (geometryType == GeoConstants.GTPoint) PointLayerFactory.build(ctx,result);
            else if (geometryType == GeoConstants.GTLineString) LineLayerFactory.build(ctx,result);
            else PolygonLayerFactory.build(ctx,result);
            if (geometryType != GeoConstants.GTPolygon && result.outlineLayer != null) nativeStyle.addLayer(result.outlineLayer);
            nativeStyle.addLayer(result.mainLayer);
            if (result.patternFillLayer != null) nativeStyle.addLayer(result.patternFillLayer);
            if (geometryType == GeoConstants.GTPolygon && result.outlineLayer != null) nativeStyle.addLayer(result.outlineLayer);
            RuleGeometryLayouts.sync(nativeStyle,result.mainLayer,null,style);
            if (geometryType == GeoConstants.GTPolygon) PolygonOutlineLayerFactory.sync(ctx,(LineLayer) result.outlineLayer,null,style);
            if (geometryType == GeoConstants.GTPoint) FireflyLayerFactory.sync(ctx,result.mainLayer,null,style);
            if (result.markerIconLayer != null) nativeStyle.addLayer(result.markerIconLayer);
            appliedJson = json; revision++; map.triggerRepaint();
        } catch (org.json.JSONException | CloneNotSupportedException error) {
            Log.e("StylePreview", "Unable to copy style for native preview", error);
        }
    }
}
