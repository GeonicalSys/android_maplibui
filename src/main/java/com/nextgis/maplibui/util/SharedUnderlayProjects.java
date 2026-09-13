package com.nextgis.maplibui.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.LocalTMSLayer;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.util.*;
import com.nextgis.maplibui.mapui.LocalTMSLayerUI;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.FutureTask;

/** Cross-workspace operations run under the caller's UNDERLAY_MIGRATION lease. */
public final class SharedUnderlayProjects {
    private static boolean scheduled;
    private SharedUnderlayProjects() { }
    public static void scheduleMigration(Context context) {
        if (scheduled) return;
        Context app = context.getApplicationContext();
        android.content.SharedPreferences prefs = app.getSharedPreferences("shared_underlay_migration", Context.MODE_PRIVATE);
        if (prefs.getBoolean("complete_v1", false)) return;
        scheduled = true;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ProjectOperationCoordinator.Lease lease = ProjectOperationCoordinator.tryBegin(app,
                    ProjectOperationCoordinator.Kind.UNDERLAY_MIGRATION);
            if (lease == null) { scheduled = false; scheduleMigration(app); return; }
            new Thread(() -> {
                try { prepare(app); prefs.edit().putBoolean("complete_v1", true).commit(); }
                catch (Exception error) { com.hypertrack.hyperlog.HyperLog.w(Constants.TAG, "Underlay migration deferred", error); }
                finally { lease.close(); scheduled = false; }
            }, "UnderlayMigration").start();
        }, 3000);
    }
    public static Map<File, String> workspaces(Context context) throws IOException {
        Map<File, String> result = new LinkedHashMap<>();
        for (CollectorProjectRegistry.ProjectInfo project : CollectorProjectRegistry.listProjects(context))
            result.put(new File(project.getMapPath(), project.getMapName() + ".ngm").getCanonicalFile(), project.getName());
        MapBase map = ((IGISApplication) context.getApplicationContext()).getMap();
        if (map != null) result.put(new File(map.getPath(), map.getName() + ".ngm").getCanonicalFile(), map.getName());
        // The old standalone map may remain as a rollback workspace after project bootstrap.
        File root = SharedUnderlayStore.catalog(context).root().getParentFile();
        File[] legacy = root.listFiles((dir, name) -> name.endsWith(".ngm"));
        if (legacy != null) for (File file : legacy) result.putIfAbsent(file.getCanonicalFile(), file.getName());
        return result;
    }
    public static List<String> usages(Context context, String id) throws IOException {
        List<String> names = new ArrayList<>();
        for (Map.Entry<File, String> entry : workspaces(context).entrySet())
            if (UnderlayWorkspaceIndex.uses(entry.getKey(), id)) names.add(entry.getValue());
        return names;
    }
    public static boolean contains(LayerGroup map, String id) {
        for (ILayer layer : map.getLayers()) {
            if (layer instanceof LocalTMSLayer && id.equals(((LocalTMSLayer) layer).getSharedUnderlayId())) return true;
            if (layer instanceof LayerGroup && contains((LayerGroup) layer, id)) return true;
        }
        return false;
    }
    public static void prepare(Context context) throws Exception {
        onMain(() -> {
            MapBase map = ((IGISApplication) context.getApplicationContext()).getMap();
            if (map != null && !map.save()) throw new IOException("Cannot save active map");
            for (File file : workspaces(context).keySet()) SharedUnderlayStore.migrateWorkspace(context, file);
            // Reload only configurations changed by the bounded move, preserving live map objects.
            if (map != null) refreshReferences(map);
        });
        SharedUnderlayStore.catalog(context).inventory();
        onMain(() -> {
            SharedUnderlayStore.catalog(context).mergeDuplicates(new ArrayList<>(workspaces(context).keySet()));
            MapBase map = ((IGISApplication) context.getApplicationContext()).getMap();
            if (map != null) refreshReferences(map);
        });
        SharedUnderlayStore.catalog(context).cleanMigratedDuplicates();
    }
    private static void refreshReferences(LayerGroup group) throws Exception {
        for (ILayer layer : group.getLayers()) {
            if (layer instanceof LocalTMSLayer) {
                LocalTMSLayer local = (LocalTMSLayer) layer;
                String before = local.getSharedUnderlayId();
                local.fromJSON(UnderlayFiles.readJson(new File(layer.getPath(), "config.json")));
                if (!java.util.Objects.equals(before, local.getSharedUnderlayId())) local.notifyLayerChanged();
            }
            else if (layer instanceof LayerGroup) refreshReferences((LayerGroup) layer);
        }
    }
    public static boolean attach(Context context, String id) throws Exception {
        final boolean[] added = {false};
        onMain(() -> {
            MapBase map = ((IGISApplication) context.getApplicationContext()).getMap();
            if (map == null) throw new IOException("No active map");
            if (contains(map, id)) return;
            SharedUnderlayCatalog.Asset asset = SharedUnderlayStore.catalog(context).get(id);
            if (asset == null || !asset.isReady()) throw new IOException("Unavailable underlay");
            LocalTMSLayerUI layer = new LocalTMSLayerUI(context, map.createLayerStorage());
            layer.setName(asset.name); layer.setVisible(true);
            SharedUnderlayStore.attach(layer, asset);
            ILayer osm = map.getLayerByPathName("osm");
            map.insertLayer(osm == null ? 0 : map.getChildLayerIndex(osm) + 1, layer);
            if (!map.save()) { map.removeLayer(layer); throw new IOException("Cannot attach underlay"); }
            added[0] = true;
        });
        return added[0];
    }
    public static void delete(Context context, String id) throws Exception {
        SharedUnderlayCatalog catalog = SharedUnderlayStore.catalog(context);
        catalog.markDeleting(id);
        onMain(() -> {
            MapBase map = ((IGISApplication) context.getApplicationContext()).getMap();
            if (map != null) { remove(map, id); if (!map.save()) throw new IOException("Cannot unlink active map"); }
        });
        for (File mapFile : workspaces(context).keySet()) UnderlayWorkspaceIndex.unlink(mapFile, id);
        if (!usages(context, id).isEmpty()) throw new IOException("Underlay is still referenced");
        catalog.deleteUnlinked(id);
    }
    private static void remove(LayerGroup group, String id) {
        for (ILayer layer : new ArrayList<>(group.getLayers())) {
            if (layer instanceof LocalTMSLayer && id.equals(((LocalTMSLayer) layer).getSharedUnderlayId())) group.removeLayer(layer);
            else if (layer instanceof LayerGroup) remove((LayerGroup) layer, id);
        }
    }
    private interface Work { void run() throws Exception; }
    private static void onMain(Work work) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) { work.run(); return; }
        FutureTask<Void> task = new FutureTask<>(() -> { work.run(); return null; });
        new Handler(Looper.getMainLooper()).post(task);
        task.get();
    }
}
