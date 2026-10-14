package com.nextgis.maplibui.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import com.nextgis.maplib.map.SharedTrackStore;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.util.SettingsConstantsUI;
import java.io.File;
import java.io.IOException;

/** Imports registered histories without opening or switching any map. */
public final class SharedTrackProjects {
    private SharedTrackProjects() { }

    public static synchronized void prepare(Context context) throws IOException {
        SharedTrackStore store = SharedTrackStore.get(context);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        File fallback = context.getExternalFilesDir(SettingsConstants.KEY_PREF_MAP);
        if (fallback == null) fallback = new File(context.getFilesDir(), SettingsConstants.KEY_PREF_MAP);
        File active = new File(preferences.getString(SettingsConstants.KEY_PREF_MAP_PATH, fallback.getPath()));
        SharedPreferences temporary = context.getSharedPreferences("tracks_temp", Context.MODE_PRIVATE);
        String savedOwner = temporary.getString("track_map", null);
        String savedUri = temporary.getString("track_uri", null);
        File recordingOwner = savedOwner == null ? active : new File(savedOwner);
        boolean legacySession = !recordingOwner.getCanonicalFile().equals(store.getDirectory().getCanonicalFile());
        if (legacySession) store.migrateWorkspace(recordingOwner);
        store.migrateWorkspace(active);
        for (CollectorProjectRegistry.ProjectInfo project : CollectorProjectRegistry.listProjects(context))
            store.migrateWorkspace(new File(project.getMapPath()));

        if (legacySession && savedUri != null) {
            Uri uri = Uri.parse(savedUri);
            long oldId;
            try { oldId = Long.parseLong(uri.getLastPathSegment()); }
            catch (RuntimeException error) { throw new IOException("Invalid saved track session", error); }
            long id = store.migratedTrackId(recordingOwner, oldId);
            if (id == Constants.NOT_FOUND) throw new IOException("Saved track session was not migrated");
            Uri migrated = uri.buildUpon().path("/tracks/" + id).build();
            if (!temporary.edit().putString("track_uri", migrated.toString())
                    .putString("track_map", store.getDirectory().getAbsolutePath()).commit())
                throw new IOException("Cannot checkpoint migrated track session");
        }
    }
}
