/*
 * Project: NextGIS Mobile custom Collector workspace foundation.
 *
 * This registry is intentionally kept even when only one Collector project is loaded.
 * It is the stable boundary for future isolated multi-project switching, composition
 * sync, backup routing, and project-scoped manual NGW layers.
 */

package com.nextgis.maplibui.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.map.CollectorProjectMetadata;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.GISApplication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.nextgis.maplib.util.SettingsConstants.KEY_PREF_MAP;

public final class CollectorProjectRegistry {
    private static final Object LOCK = new Object();

    private static final int SCHEMA_VERSION = 1;
    private static final String REGISTRY_FILE_NAME = "collector_projects_registry.json";
    private static final String WORKSPACES_DIR_NAME = "collector_projects";
    private static final String WORKSPACE_MAP_NAME = "map";

    private static final String JSON_SCHEMA_VERSION = "schema_version";
    private static final String JSON_PROJECTS = "projects";
    private static final String JSON_PROJECT_UID = "project_uid";
    private static final String JSON_ACCOUNT = "account";
    private static final String JSON_PROJECT_REMOTE_ID = "project_remote_id";
    private static final String JSON_NAME = "name";
    private static final String JSON_DISTRICT = "district";
    private static final String JSON_MAP_PATH = "map_path";
    private static final String JSON_MAP_NAME = "map_name";
    private static final String JSON_CREATED_AT = "created_at";
    private static final String JSON_LAST_OPENED_AT = "last_opened_at";

    private CollectorProjectRegistry() {
    }

    public static final class ProjectInfo {
        private final String projectUid;
        private final String accountName;
        private final long projectRemoteId;
        private final String name;
        private final String district;
        private final String mapPath;
        private final String mapName;
        private final long createdAt;
        private final long lastOpenedAt;

        private ProjectInfo(
                String projectUid,
                String accountName,
                long projectRemoteId,
                String name,
                String district,
                String mapPath,
                String mapName,
                long createdAt,
                long lastOpenedAt) {
            this.projectUid = projectUid;
            this.accountName = accountName;
            this.projectRemoteId = projectRemoteId;
            this.name = name;
            this.district = district;
            this.mapPath = mapPath;
            this.mapName = mapName;
            this.createdAt = createdAt;
            this.lastOpenedAt = lastOpenedAt;
        }

        public String getProjectUid() {
            return projectUid;
        }

        public String getAccountName() {
            return accountName;
        }

        public long getProjectRemoteId() {
            return projectRemoteId;
        }

        public String getName() {
            return name;
        }

        public String getDistrict() {
            return district;
        }

        public String getMapPath() {
            return mapPath;
        }

        public String getMapName() {
            return mapName;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public long getLastOpenedAt() {
            return lastOpenedAt;
        }

        public boolean isActive(Context context) {
            if (context == null) {
                return false;
            }
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            return projectUid.equals(preferences.getString(
                    SettingsConstants.KEY_PREF_ACTIVE_COLLECTOR_PROJECT_UID, ""));
        }

        private JSONObject toJSON() throws JSONException {
            JSONObject json = new JSONObject();
            json.put(JSON_PROJECT_UID, projectUid);
            json.put(JSON_ACCOUNT, accountName);
            json.put(JSON_PROJECT_REMOTE_ID, projectRemoteId);
            json.put(JSON_NAME, name);
            if (!TextUtils.isEmpty(district)) {
                json.put(JSON_DISTRICT, district);
            }
            json.put(JSON_MAP_PATH, mapPath);
            json.put(JSON_MAP_NAME, mapName);
            json.put(JSON_CREATED_AT, createdAt);
            json.put(JSON_LAST_OPENED_AT, lastOpenedAt);
            return json;
        }

        private static ProjectInfo fromJSON(JSONObject json) {
            if (json == null) {
                return null;
            }
            String projectUid = json.optString(JSON_PROJECT_UID, null);
            String accountName = json.optString(JSON_ACCOUNT, null);
            long projectRemoteId = json.optLong(JSON_PROJECT_REMOTE_ID, 0L);
            String mapPath = json.optString(JSON_MAP_PATH, null);
            String mapName = json.optString(JSON_MAP_NAME, WORKSPACE_MAP_NAME);
            if (TextUtils.isEmpty(projectUid)
                    || TextUtils.isEmpty(accountName)
                    || projectRemoteId <= 0L
                    || TextUtils.isEmpty(mapPath)
                    || TextUtils.isEmpty(mapName)) {
                return null;
            }
            return new ProjectInfo(
                    projectUid,
                    accountName,
                    projectRemoteId,
                    json.optString(JSON_NAME, projectUid),
                    json.optString(JSON_DISTRICT, null),
                    mapPath,
                    mapName,
                    json.optLong(JSON_CREATED_AT, 0L),
                    json.optLong(JSON_LAST_OPENED_AT, 0L));
        }
    }

    public static List<ProjectInfo> listProjects(Context context) {
        synchronized (LOCK) {
            ArrayList<ProjectInfo> projects = loadProjectsLocked(context);
            Collections.sort(projects, new Comparator<ProjectInfo>() {
                @Override
                public int compare(ProjectInfo left, ProjectInfo right) {
                    return Long.compare(right.getLastOpenedAt(), left.getLastOpenedAt());
                }
            });
            return projects;
        }
    }

    public static ProjectInfo getActiveProject(Context context) {
        if (context == null) {
            return null;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String activeUid = preferences.getString(
                SettingsConstants.KEY_PREF_ACTIVE_COLLECTOR_PROJECT_UID, "");
        if (TextUtils.isEmpty(activeUid)) {
            return null;
        }
        synchronized (LOCK) {
            return findProject(loadProjectsLocked(context), activeUid);
        }
    }

    public static ProjectInfo ensureProject(Context context, CollectorProjectMetadata metadata) {
        if (context == null || metadata == null || !metadata.isValid()) {
            return null;
        }
        return ensureProject(
                context,
                metadata.getAccountName(),
                metadata.getProjectRemoteId(),
                metadata.getName(),
                metadata.getDistrict());
    }

    public static ProjectInfo ensureProject(
            Context context,
            String accountName,
            long projectRemoteId,
            String name,
            String district) {
        if (context == null || TextUtils.isEmpty(accountName) || projectRemoteId <= 0L) {
            return null;
        }
        String projectUid = CollectorProjectMetadata.buildProjectUid(accountName, projectRemoteId);
        if (TextUtils.isEmpty(projectUid)) {
            return null;
        }
        synchronized (LOCK) {
            ArrayList<ProjectInfo> projects = loadProjectsLocked(context);
            ProjectInfo existing = findProject(projects, projectUid);
            ProjectInfo updated = buildProjectInfo(context, existing, projectUid, accountName,
                    projectRemoteId, name, district);
            if (updated == null) {
                return null;
            }
            if (existing != null) {
                projects.remove(existing);
            }
            projects.add(updated);
            saveProjectsLocked(context, projects);
            return updated;
        }
    }

    public static boolean activateProject(Context context, String projectUid) {
        if (context == null || TextUtils.isEmpty(projectUid)) {
            return false;
        }
        synchronized (LOCK) {
            ArrayList<ProjectInfo> projects = loadProjectsLocked(context);
            ProjectInfo project = findProject(projects, projectUid);
            if (project == null) {
                return false;
            }
            File mapDir = new File(project.getMapPath());
            try {
                FileUtil.createDir(mapDir);
            } catch (RuntimeException e) {
                HyperLog.w(Constants.TAG, "CollectorProjectRegistry: failed to create workspace dir "
                        + mapDir.getPath() + ": " + e.getMessage(), e);
                return false;
            }
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            preferences.edit()
                    .putString(SettingsConstants.KEY_PREF_MAP_PATH, project.getMapPath())
                    .putString(SettingsConstantsUI.KEY_PREF_MAP_NAME, project.getMapName())
                    .putString(SettingsConstants.KEY_PREF_ACTIVE_COLLECTOR_PROJECT_UID,
                            project.getProjectUid())
                    .apply();

            projects.remove(project);
            ProjectInfo opened = new ProjectInfo(
                    project.getProjectUid(),
                    project.getAccountName(),
                    project.getProjectRemoteId(),
                    project.getName(),
                    project.getDistrict(),
                    project.getMapPath(),
                    project.getMapName(),
                    project.getCreatedAt(),
                    System.currentTimeMillis());
            projects.add(opened);
            saveProjectsLocked(context, projects);
            return true;
        }
    }

    public static LayerGroup prepareCollectorProjectWorkspace(
            Context context,
            CollectorProjectMetadata metadata) {
        if (context == null || metadata == null || !metadata.isValid()) {
            return null;
        }
        ProjectInfo project = ensureProject(context, metadata);
        if (project == null) {
            return null;
        }

        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof IGISApplication)) {
            return null;
        }
        IGISApplication app = (IGISApplication) appContext;
        try {
            MapBase currentMap = app.getMap();
            if (currentMap != null) {
                currentMap.save();
            }
        } catch (RuntimeException e) {
            HyperLog.w(Constants.TAG, "CollectorProjectRegistry: current map save before switch failed: "
                    + e.getMessage(), e);
        }

        if (!activateProject(context, project.getProjectUid())) {
            return null;
        }
        if (appContext instanceof GISApplication) {
            ((GISApplication) appContext).closeMapObj();
        }

        MapBase projectMap = app.getMap();
        if (projectMap == null) {
            return null;
        }

        CollectorProjectMetadata freshMetadata = CollectorProjectMetadata.create(
                metadata.getAccountName(),
                metadata.getProjectRemoteId(),
                metadata.getName(),
                metadata.getDistrict());
        projectMap.setName(!TextUtils.isEmpty(metadata.getName())
                ? metadata.getName()
                : project.getProjectUid());
        projectMap.setCollectorProjectMetadata(freshMetadata);
        projectMap.save();
        HyperLog.v(Constants.TAG, "CollectorProjectRegistry: activated workspace uid="
                + project.getProjectUid() + " mapPath=" + project.getMapPath());
        return projectMap;
    }

    private static ProjectInfo buildProjectInfo(
            Context context,
            ProjectInfo existing,
            String projectUid,
            String accountName,
            long projectRemoteId,
            String name,
            String district) {
        long now = System.currentTimeMillis();
        File workspaceDir = getProjectWorkspaceDir(context, projectUid, projectRemoteId);
        String projectName = TextUtils.isEmpty(name) ? projectUid : name;
        String projectDistrict = TextUtils.isEmpty(district) ? null : district.trim();
        long createdAt = existing != null && existing.getCreatedAt() > 0L
                ? existing.getCreatedAt()
                : now;
        long lastOpenedAt = existing != null ? existing.getLastOpenedAt() : 0L;
        return new ProjectInfo(
                projectUid,
                accountName,
                projectRemoteId,
                projectName,
                projectDistrict,
                workspaceDir.getAbsolutePath(),
                WORKSPACE_MAP_NAME,
                createdAt,
                lastOpenedAt);
    }

    private static ProjectInfo findProject(List<ProjectInfo> projects, String projectUid) {
        if (projects == null || TextUtils.isEmpty(projectUid)) {
            return null;
        }
        for (ProjectInfo project : projects) {
            if (projectUid.equals(project.getProjectUid())) {
                return project;
            }
        }
        return null;
    }

    private static ArrayList<ProjectInfo> loadProjectsLocked(Context context) {
        ArrayList<ProjectInfo> projects = new ArrayList<>();
        File registryFile = getRegistryFile(context);
        if (registryFile == null || !registryFile.exists()) {
            return projects;
        }
        try {
            JSONObject root = new JSONObject(FileUtil.readFromFile(registryFile));
            JSONArray items = root.optJSONArray(JSON_PROJECTS);
            if (items == null) {
                return projects;
            }
            for (int i = 0; i < items.length(); i++) {
                ProjectInfo project = ProjectInfo.fromJSON(items.optJSONObject(i));
                if (project != null) {
                    projects.add(project);
                }
            }
        } catch (IOException | JSONException e) {
            HyperLog.w(Constants.TAG, "CollectorProjectRegistry: failed to read registry: "
                    + e.getMessage(), e);
        }
        return projects;
    }

    private static void saveProjectsLocked(Context context, List<ProjectInfo> projects) {
        File registryFile = getRegistryFile(context);
        if (registryFile == null) {
            return;
        }
        File parent = registryFile.getParentFile();
        if (parent != null) {
            try {
                FileUtil.createDir(parent);
            } catch (RuntimeException e) {
                HyperLog.w(Constants.TAG, "CollectorProjectRegistry: failed to create registry dir "
                        + parent.getPath() + ": " + e.getMessage(), e);
                return;
            }
        }
        try {
            JSONObject root = new JSONObject();
            root.put(JSON_SCHEMA_VERSION, SCHEMA_VERSION);
            JSONArray array = new JSONArray();
            if (projects != null) {
                for (ProjectInfo project : projects) {
                    if (project != null) {
                        array.put(project.toJSON());
                    }
                }
            }
            root.put(JSON_PROJECTS, array);
            FileUtil.writeToFile(registryFile, root.toString());
        } catch (IOException | JSONException e) {
            HyperLog.w(Constants.TAG, "CollectorProjectRegistry: failed to save registry: "
                    + e.getMessage(), e);
        }
    }

    private static File getRegistryFile(Context context) {
        File root = getWorkspacesRootDir(context);
        return root != null ? new File(root, REGISTRY_FILE_NAME) : null;
    }

    private static File getProjectWorkspaceDir(
            Context context,
            String projectUid,
            long projectRemoteId) {
        return new File(getWorkspacesRootDir(context), buildWorkspaceDirName(projectUid, projectRemoteId));
    }

    private static File getWorkspacesRootDir(Context context) {
        File mapRoot = context.getExternalFilesDir(KEY_PREF_MAP);
        if (mapRoot == null) {
            mapRoot = new File(context.getFilesDir(), KEY_PREF_MAP);
        }
        return new File(mapRoot, WORKSPACES_DIR_NAME);
    }

    private static String buildWorkspaceDirName(String projectUid, long projectRemoteId) {
        String hash = Integer.toHexString(projectUid.hashCode());
        return "collector_" + projectRemoteId + "_" + hash;
    }
}
