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
import android.util.AtomicFile;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.map.CollectorProjectMetadata;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.LocalVectorTileServer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.service.TrackerService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.nextgis.maplib.util.SettingsConstants.KEY_PREF_MAP;

public final class CollectorProjectRegistry {
    private static final Object LOCK = new Object();

    private static final int SCHEMA_VERSION = 2;
    private static final String REGISTRY_FILE_NAME = "collector_projects_registry.json";
    private static final String WORKSPACES_DIR_NAME = "collector_projects";
    private static final String WORKSPACE_MAP_NAME = "map";
    private static final String WORKSPACE_INFO_NAME = "project.json";
    private static final String INITIAL_LOCAL_PROJECT_UID = "local:initial";
    private static final String INITIAL_LOCAL_WORKSPACE_NAME = "local_initial";
    private static final String KEY_INITIAL_LOCAL_PROJECT_CREATED =
            "collector_initial_local_project_created_v1";

    private static final String JSON_SCHEMA_VERSION = "schema_version";
    private static final String JSON_PROJECTS = "projects";
    private static final String JSON_PROJECT_UID = "project_uid";
    private static final String JSON_PROJECT_TYPE = "project_type";
    private static final String JSON_ACCOUNT = "account";
    private static final String JSON_PROJECT_REMOTE_ID = "project_remote_id";
    private static final String JSON_NAME = "name";
    private static final String JSON_SOURCE_NAME = "source_name";
    private static final String JSON_DISTRICT = "district";
    private static final String JSON_MAP_PATH = "map_path";
    private static final String JSON_MAP_NAME = "map_name";
    private static final String JSON_CREATED_AT = "created_at";
    private static final String JSON_LAST_OPENED_AT = "last_opened_at";

    private CollectorProjectRegistry() {
    }

    public enum ProjectType {
        WEBGIS,
        LOCAL
    }

    public interface DestructiveBackupGate {
        boolean prepareBackup();
    }

    public static final class DeleteResult {
        public enum Status {
            SUCCESS,
            BUSY,
            NOT_FOUND,
            NOT_ACTIVE,
            TRACKING,
            BACKUP_FAILED,
            STORAGE_FAILED
        }

        private final Status status;
        private final String fallbackProjectUid;

        private DeleteResult(Status status, String fallbackProjectUid) {
            this.status = status;
            this.fallbackProjectUid = fallbackProjectUid;
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }

        public Status getStatus() {
            return status;
        }

        public String getFallbackProjectUid() {
            return fallbackProjectUid;
        }
    }

    public static final class PrepareWorkspaceResult {
        public enum Status {
            SUCCESS,
            BUSY,
            INVALID,
            FAILED
        }

        private final Status status;
        private final LayerGroup workspace;

        private PrepareWorkspaceResult(Status status, LayerGroup workspace) {
            this.status = status;
            this.workspace = workspace;
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS && workspace != null;
        }

        public boolean isBusy() {
            return status == Status.BUSY;
        }

        public Status getStatus() {
            return status;
        }

        public LayerGroup getWorkspace() {
            return workspace;
        }
    }

    public static final class ProjectInfo {
        private final String projectUid;
        private final ProjectType projectType;
        private final String accountName;
        private final long projectRemoteId;
        private final String name;
        private final String sourceName;
        private final String district;
        private final String mapPath;
        private final String mapName;
        private final long createdAt;
        private final long lastOpenedAt;

        private ProjectInfo(
                String projectUid,
                ProjectType projectType,
                String accountName,
                long projectRemoteId,
                String name,
                String sourceName,
                String district,
                String mapPath,
                String mapName,
                long createdAt,
                long lastOpenedAt) {
            this.projectUid = projectUid;
            this.projectType = projectType != null ? projectType : ProjectType.WEBGIS;
            this.accountName = accountName;
            this.projectRemoteId = projectRemoteId;
            this.name = name;
            this.sourceName = sourceName;
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

        public ProjectType getProjectType() {
            return projectType;
        }

        public boolean isLocal() {
            return projectType == ProjectType.LOCAL;
        }

        public long getProjectRemoteId() {
            return projectRemoteId;
        }

        public String getName() {
            return name;
        }

        public String getSourceName() {
            return sourceName;
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
            json.put(JSON_PROJECT_TYPE, projectType.name());
            if (!TextUtils.isEmpty(accountName)) {
                json.put(JSON_ACCOUNT, accountName);
            }
            if (projectRemoteId > 0L) {
                json.put(JSON_PROJECT_REMOTE_ID, projectRemoteId);
            }
            json.put(JSON_NAME, name);
            if (!TextUtils.isEmpty(sourceName)) {
                json.put(JSON_SOURCE_NAME, sourceName);
            }
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
            ProjectType projectType;
            try {
                projectType = ProjectType.valueOf(
                        json.optString(JSON_PROJECT_TYPE, ProjectType.WEBGIS.name()));
            } catch (IllegalArgumentException e) {
                return null;
            }
            String accountName = json.optString(JSON_ACCOUNT, null);
            long projectRemoteId = json.optLong(JSON_PROJECT_REMOTE_ID, 0L);
            String mapPath = json.optString(JSON_MAP_PATH, null);
            String mapName = json.optString(JSON_MAP_NAME, WORKSPACE_MAP_NAME);
            boolean invalidWebProject = projectType == ProjectType.WEBGIS
                    && (TextUtils.isEmpty(accountName) || projectRemoteId <= 0L);
            if (TextUtils.isEmpty(projectUid)
                    || invalidWebProject
                    || TextUtils.isEmpty(mapPath)
                    || TextUtils.isEmpty(mapName)) {
                return null;
            }
            return new ProjectInfo(
                    projectUid,
                    projectType,
                    accountName,
                    projectRemoteId,
                    json.optString(JSON_NAME, projectUid),
                    json.optString(JSON_SOURCE_NAME,
                            json.optString(JSON_NAME, projectUid)),
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

    /**
     * Ensures that map startup always belongs to a registered project. On upgrade, the legacy
     * standalone map is copied into the initial local workspace once and remains untouched as a
     * rollback copy. This method must run before GISApplication opens its first MapDrawable.
     */
    public static ProjectInfo ensureInitialLocalProject(Context context, String requestedName) {
        if (context == null || TextUtils.isEmpty(requestedName)
                || TextUtils.isEmpty(requestedName.trim())) {
            return null;
        }
        synchronized (LOCK) {
            SharedPreferences preferences =
                    PreferenceManager.getDefaultSharedPreferences(context);
            ArrayList<ProjectInfo> projects = loadProjectsLocked(context);
            ProjectInfo active = resolveConfiguredProject(preferences, projects);
            boolean alreadyInitialized = preferences.getBoolean(
                    KEY_INITIAL_LOCAL_PROJECT_CREATED, false);

            ProjectInfo initial = findProject(projects, INITIAL_LOCAL_PROJECT_UID);
            if (!alreadyInitialized && initial == null) {
                initial = buildInitialLocalProjectInfoLocked(
                        context, projects, requestedName.trim());
                if (initial == null) {
                    return active;
                }
                File legacyMap = findLegacyMap(context, preferences);
                if (legacyMap != null) {
                    try {
                        com.nextgis.maplib.util.SharedUnderlayStore.migrateWorkspace(context, legacyMap);
                        LegacyMapWorkspaceCopier.copy(
                                legacyMap, new File(initial.getMapPath()), initial.getMapName());
                        HyperLog.i(Constants.TAG,
                                "CollectorProjectRegistry: legacy map copied into initial local project");
                    } catch (IOException | JSONException | RuntimeException e) {
                        HyperLog.w(Constants.TAG,
                                "CollectorProjectRegistry: legacy map migration failed: "
                                        + e.getMessage(), e);
                        try {
                            FileUtil.deleteRecursive(new File(initial.getMapPath()));
                        } catch (RuntimeException cleanupError) {
                            HyperLog.w(Constants.TAG,
                                    "CollectorProjectRegistry: incomplete migration cleanup failed: "
                                            + cleanupError.getMessage(), cleanupError);
                        }
                        return active;
                    }
                }
                projects.add(initial);
            }

            if (alreadyInitialized && initial == null && projects.isEmpty()) {
                initial = buildInitialLocalProjectInfoLocked(
                        context, projects, requestedName.trim());
                if (initial == null) {
                    return null;
                }
                projects.add(initial);
            }

            ProjectInfo startupProject = active;
            if (startupProject == null) {
                startupProject = initial != null
                        ? initial : mostRecentlyOpenedProjectExcept(projects, null);
            }
            if (startupProject == null) {
                return null;
            }

            projects.remove(startupProject);
            ProjectInfo opened = copyProject(
                    startupProject, startupProject.getName(), System.currentTimeMillis());
            projects.add(opened);
            if (!saveProjectsLocked(context, projects)) {
                return active;
            }

            SharedPreferences.Editor editor = preferences.edit()
                    .putBoolean(KEY_INITIAL_LOCAL_PROJECT_CREATED, true);
            if (active == null) {
                editor.putString(SettingsConstants.KEY_PREF_MAP_PATH, opened.getMapPath())
                        .putString(SettingsConstantsUI.KEY_PREF_MAP_NAME, opened.getMapName())
                        .putString(SettingsConstants.KEY_PREF_ACTIVE_COLLECTOR_PROJECT_UID,
                                opened.getProjectUid());
            }
            if (!editor.commit()) {
                HyperLog.w(Constants.TAG,
                        "CollectorProjectRegistry: failed to persist initial project preferences");
                return active;
            }
            return opened;
        }
    }

    public static ProjectInfo createLocalProject(Context context, String requestedName) {
        if (context == null || TextUtils.isEmpty(requestedName)
                || TextUtils.isEmpty(requestedName.trim())) {
            return null;
        }
        ProjectOperationCoordinator.Lease operationLease =
                ProjectOperationCoordinator.tryBegin(
                        context, ProjectOperationCoordinator.Kind.PROJECT_CREATE);
        if (operationLease == null) {
            return null;
        }
        try {
            synchronized (LOCK) {
                ArrayList<ProjectInfo> projects = loadProjectsLocked(context);
                ProjectInfo project = buildLocalProjectInfoLocked(
                        context, projects, requestedName.trim());
                if (project == null) {
                    return null;
                }
                projects.add(project);
                if (!saveProjectsLocked(context, projects)) {
                    FileUtil.deleteRecursive(new File(project.getMapPath()));
                    return null;
                }
                return project;
            }
        } finally {
            operationLease.close();
        }
    }

    public static ProjectInfo renameProject(Context context, String projectUid, String requestedName) {
        if (context == null || TextUtils.isEmpty(projectUid)
                || TextUtils.isEmpty(requestedName)
                || TextUtils.isEmpty(requestedName.trim())) {
            return null;
        }
        ProjectOperationCoordinator.Lease operationLease =
                ProjectOperationCoordinator.tryBegin(
                        context, ProjectOperationCoordinator.Kind.PROJECT_RENAME);
        if (operationLease == null) {
            return null;
        }
        try {
            synchronized (LOCK) {
                ArrayList<ProjectInfo> projects = loadProjectsLocked(context);
                ProjectInfo existing = findProject(projects, projectUid);
                if (existing == null) {
                    return null;
                }
                String displayName = uniqueDisplayName(
                        projects, requestedName.trim(), projectUid);
                ProjectInfo renamed = copyProject(existing, displayName, existing.getLastOpenedAt());
                projects.remove(existing);
                projects.add(renamed);
                return saveProjectsLocked(context, projects) ? renamed : null;
            }
        } finally {
            operationLease.close();
        }
    }

    /**
     * Deletes the active local workspace only. The remote Web GIS resource and Android account are
     * never modified. If this is the last project, an empty local fallback is created first.
     */
    public static DeleteResult deleteActiveProject(
            Context context,
            String projectUid,
            String fallbackLocalName,
            DestructiveBackupGate backupGate) {
        if (context == null || TextUtils.isEmpty(projectUid)) {
            return new DeleteResult(DeleteResult.Status.NOT_FOUND, null);
        }
        ProjectOperationCoordinator.Lease operationLease =
                ProjectOperationCoordinator.tryBegin(
                        context, ProjectOperationCoordinator.Kind.PROJECT_DELETE);
        if (operationLease == null) {
            return new DeleteResult(DeleteResult.Status.BUSY, null);
        }
        try {
            if (TrackerService.isTrackerServiceRunning(context)) {
                return new DeleteResult(DeleteResult.Status.TRACKING, null);
            }

            ProjectInfo target;
            synchronized (LOCK) {
                target = findProject(loadProjectsLocked(context), projectUid);
                if (target == null) {
                    return new DeleteResult(DeleteResult.Status.NOT_FOUND, null);
                }
                if (!target.isActive(context)) {
                    return new DeleteResult(DeleteResult.Status.NOT_ACTIVE, null);
                }
            }

            if (backupGate != null && !backupGate.prepareBackup()) {
                return new DeleteResult(DeleteResult.Status.BACKUP_FAILED, null);
            }
            if (TrackerService.isTrackerServiceRunning(context)) {
                return new DeleteResult(DeleteResult.Status.TRACKING, null);
            }

            synchronized (LOCK) {
                ArrayList<ProjectInfo> projects = loadProjectsLocked(context);
                target = findProject(projects, projectUid);
                if (target == null || !target.isActive(context)) {
                    return new DeleteResult(DeleteResult.Status.NOT_ACTIVE, null);
                }
                if (!isWorkspacePathSafe(context, target.getMapPath())) {
                    return new DeleteResult(DeleteResult.Status.STORAGE_FAILED, null);
                }

                Context appContext = context.getApplicationContext();
                GISApplication gisApplication = appContext instanceof GISApplication
                        ? (GISApplication) appContext : null;
                if (gisApplication != null && gisApplication.getMap() != null) {
                    gisApplication.getMap().save();
                }

                try {
                    com.nextgis.maplib.util.SharedUnderlayStore.migrateWorkspace(context,
                            new File(target.getMapPath(), target.getMapName() + ".ngm"));
                } catch (IOException e) {
                    HyperLog.w(Constants.TAG, "Cannot preserve project underlays before deletion", e);
                    return new DeleteResult(DeleteResult.Status.STORAGE_FAILED, null);
                }

                ProjectInfo fallback = mostRecentlyOpenedProjectExcept(projects, projectUid);
                boolean createdFallback = false;
                if (fallback == null) {
                    String localName = TextUtils.isEmpty(fallbackLocalName)
                            ? "Local project" : fallbackLocalName.trim();
                    fallback = buildLocalProjectInfoLocked(context, projects, localName);
                    if (fallback == null) {
                        return new DeleteResult(DeleteResult.Status.STORAGE_FAILED, null);
                    }
                    projects.add(fallback);
                    createdFallback = true;
                }

                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                if (!persistActiveProjectPreferences(preferences, fallback)) {
                    if (createdFallback) {
                        FileUtil.deleteRecursive(new File(fallback.getMapPath()));
                    }
                    return new DeleteResult(DeleteResult.Status.STORAGE_FAILED, null);
                }
                if (gisApplication != null) {
                    gisApplication.closeMapObj();
                }

                File targetDir = new File(target.getMapPath());
                File tombstone = new File(
                        getWorkspacesRootDir(context),
                        ".deleting_" + UUID.randomUUID().toString());
                boolean moved = !targetDir.exists() || targetDir.renameTo(tombstone);
                if (!moved) {
                    persistActiveProjectPreferences(preferences, target);
                    if (gisApplication != null) {
                        gisApplication.closeMapObj();
                    }
                    if (createdFallback) {
                        projects.remove(fallback);
                        FileUtil.deleteRecursive(new File(fallback.getMapPath()));
                    }
                    return new DeleteResult(DeleteResult.Status.STORAGE_FAILED, null);
                }

                projects.remove(target);
                projects.remove(fallback);
                ProjectInfo openedFallback = copyProject(
                        fallback, fallback.getName(), System.currentTimeMillis());
                projects.add(openedFallback);
                if (!saveProjectsLocked(context, projects)) {
                    if (tombstone.exists()) {
                        tombstone.renameTo(targetDir);
                    }
                    persistActiveProjectPreferences(preferences, target);
                    if (gisApplication != null) {
                        gisApplication.closeMapObj();
                    }
                    return new DeleteResult(DeleteResult.Status.STORAGE_FAILED, null);
                }

                CollectorImportJournal.Snapshot journal = CollectorImportJournal.load(context);
                if (journal != null && projectUid.equals(journal.projectUid)) {
                    CollectorImportJournal.clear(context);
                }
                if (tombstone.exists() && !FileUtil.deleteRecursive(tombstone)) {
                    HyperLog.w(Constants.TAG,
                            "Project deletion: tombstone cleanup deferred " + tombstone.getPath());
                }
                return new DeleteResult(DeleteResult.Status.SUCCESS,
                        openedFallback.getProjectUid());
            }
        } catch (RuntimeException e) {
            HyperLog.w(Constants.TAG, "Project deletion failed: " + e.getMessage(), e);
            return new DeleteResult(DeleteResult.Status.STORAGE_FAILED, null);
        } finally {
            operationLease.close();
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
        ProjectOperationCoordinator.Lease operationLease =
                ProjectOperationCoordinator.tryBegin(
                        context, ProjectOperationCoordinator.Kind.PROJECT_SWITCH);
        if (operationLease == null) {
            return false;
        }
        try {
            return activateProjectUnderLease(context, projectUid);
        } finally {
            operationLease.close();
        }
    }

    private static boolean activateProjectUnderLease(Context context, String projectUid) {
        synchronized (LOCK) {
            ArrayList<ProjectInfo> projects = loadProjectsLocked(context);
            ProjectInfo project = findProject(projects, projectUid);
            if (project == null) {
                return false;
            }
            if (!isWorkspacePathSafe(context, project.getMapPath())) {
                HyperLog.e(Constants.TAG,
                        "CollectorProjectRegistry: refusing unsafe workspace path "
                                + project.getMapPath());
                return false;
            }
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            String activeProjectUid = preferences.getString(
                    SettingsConstants.KEY_PREF_ACTIVE_COLLECTOR_PROJECT_UID, null);
            if (!projectUid.equals(activeProjectUid)
                    && TrackerService.isTrackerServiceRunning(context)) {
                HyperLog.w(Constants.TAG,
                        "CollectorProjectRegistry: refusing project switch while track recording is active"
                                + " from=" + activeProjectUid + " to=" + projectUid);
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
            Context appContext = context.getApplicationContext();
            GISApplication gisApplication = appContext instanceof GISApplication
                    ? (GISApplication) appContext
                    : null;
            boolean activated;
            if (gisApplication != null) {
                // GISApplication#getMap() uses the same monitor. Keep preference publication and
                // invalidation of the old map atomic so a provider/background thread cannot observe
                // the new project identity while still receiving the previous workspace instance.
                synchronized (gisApplication) {
                    try {
                        MapBase currentMap = gisApplication.getMap();
                        if (currentMap != null && !currentMap.save()) {
                            HyperLog.w(Constants.TAG,
                                    "CollectorProjectRegistry: current map save returned false");
                            return false;
                        }
                    } catch (RuntimeException e) {
                        HyperLog.w(Constants.TAG,
                                "CollectorProjectRegistry: current map save before switch failed: "
                                        + e.getMessage(), e);
                        return false;
                    }
                    activated = persistActiveProjectPreferences(preferences, project);
                    if (activated) {
                        // Invalidate queued tile requests before publishing a new MapBase singleton.
                        // Active requests keep their old layer owner and finish against the old DB;
                        // their generation can no longer enqueue or apply more work.
                        LocalVectorTileServer.getInstance().clearLayers();
                        gisApplication.closeMapObj();
                        MapBase activatedMap = gisApplication.getMap();
                        if (activatedMap == null) {
                            HyperLog.e(Constants.TAG,
                                    "CollectorProjectRegistry: activated workspace map is unavailable");
                            return false;
                        }
                    }
                }
            } else {
                activated = persistActiveProjectPreferences(preferences, project);
            }
            if (!activated) {
                HyperLog.e(Constants.TAG,
                        "CollectorProjectRegistry: failed to persist active workspace preferences");
                return false;
            }

            projects.remove(project);
            ProjectInfo opened = new ProjectInfo(
                    project.getProjectUid(),
                    project.getProjectType(),
                    project.getAccountName(),
                    project.getProjectRemoteId(),
                    project.getName(),
                    project.getSourceName(),
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
        return prepareCollectorProjectWorkspaceResult(context, metadata).getWorkspace();
    }

    public static PrepareWorkspaceResult prepareCollectorProjectWorkspaceResult(
            Context context,
            CollectorProjectMetadata metadata) {
        if (context == null || metadata == null || !metadata.isValid()) {
            return new PrepareWorkspaceResult(PrepareWorkspaceResult.Status.INVALID, null);
        }
        ProjectOperationCoordinator.Lease operationLease =
                ProjectOperationCoordinator.tryBegin(
                        context, ProjectOperationCoordinator.Kind.PROJECT_SWITCH);
        if (operationLease == null) {
            return new PrepareWorkspaceResult(PrepareWorkspaceResult.Status.BUSY, null);
        }
        try {
            ProjectInfo project = ensureProject(context, metadata);
            if (project == null) {
                return new PrepareWorkspaceResult(PrepareWorkspaceResult.Status.FAILED, null);
            }

            Context appContext = context.getApplicationContext();
            if (!(appContext instanceof IGISApplication)) {
                return new PrepareWorkspaceResult(PrepareWorkspaceResult.Status.FAILED, null);
            }
            IGISApplication app = (IGISApplication) appContext;
            if (!activateProjectUnderLease(context, project.getProjectUid())) {
                return new PrepareWorkspaceResult(PrepareWorkspaceResult.Status.FAILED, null);
            }
            MapBase projectMap = app.getMap();
            if (projectMap == null) {
                return new PrepareWorkspaceResult(PrepareWorkspaceResult.Status.FAILED, null);
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
            if (!projectMap.save()) {
                return new PrepareWorkspaceResult(PrepareWorkspaceResult.Status.FAILED, null);
            }
            HyperLog.v(Constants.TAG, "CollectorProjectRegistry: activated workspace uid="
                    + project.getProjectUid() + " mapPath=" + project.getMapPath());
            return new PrepareWorkspaceResult(PrepareWorkspaceResult.Status.SUCCESS, projectMap);
        } catch (RuntimeException e) {
            HyperLog.w(Constants.TAG,
                    "CollectorProjectRegistry: workspace preparation failed: " + e.getMessage(), e);
            return new PrepareWorkspaceResult(PrepareWorkspaceResult.Status.FAILED, null);
        } finally {
            operationLease.close();
        }
    }

    private static boolean persistActiveProjectPreferences(
            SharedPreferences preferences,
            ProjectInfo project) {
        return preferences.edit()
                .putString(SettingsConstants.KEY_PREF_MAP_PATH, project.getMapPath())
                .putString(SettingsConstantsUI.KEY_PREF_MAP_NAME, project.getMapName())
                .putString(SettingsConstants.KEY_PREF_ACTIVE_COLLECTOR_PROJECT_UID,
                        project.getProjectUid())
                .commit();
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
        String sourceName = TextUtils.isEmpty(name) ? projectUid : name.trim();
        String projectName = existing != null && !TextUtils.isEmpty(existing.getName())
                ? existing.getName()
                : uniqueDisplayNameLocked(context, sourceName, projectUid);
        String projectDistrict = TextUtils.isEmpty(district) ? null : district.trim();
        long createdAt = existing != null && existing.getCreatedAt() > 0L
                ? existing.getCreatedAt()
                : now;
        long lastOpenedAt = existing != null ? existing.getLastOpenedAt() : 0L;
        return new ProjectInfo(
                projectUid,
                ProjectType.WEBGIS,
                accountName,
                projectRemoteId,
                projectName,
                sourceName,
                projectDistrict,
                workspaceDir.getAbsolutePath(),
                WORKSPACE_MAP_NAME,
                createdAt,
                lastOpenedAt);
    }

    private static ProjectInfo buildLocalProjectInfoLocked(
            Context context,
            List<ProjectInfo> projects,
            String requestedName) {
        String uuid = UUID.randomUUID().toString();
        String projectUid = "local:" + uuid;
        File workspaceDir = new File(getWorkspacesRootDir(context), "local_" + uuid);
        try {
            FileUtil.createDir(workspaceDir);
        } catch (RuntimeException e) {
            HyperLog.w(Constants.TAG, "Local project workspace creation failed: "
                    + e.getMessage(), e);
            return null;
        }
        long now = System.currentTimeMillis();
        String displayName = uniqueDisplayName(projects, requestedName, projectUid);
        return new ProjectInfo(
                projectUid,
                ProjectType.LOCAL,
                null,
                0L,
                displayName,
                displayName,
                null,
                workspaceDir.getAbsolutePath(),
                WORKSPACE_MAP_NAME,
                now,
                0L);
    }

    private static ProjectInfo buildInitialLocalProjectInfoLocked(
            Context context,
            List<ProjectInfo> projects,
            String requestedName) {
        File workspaceDir = new File(
                getWorkspacesRootDir(context), INITIAL_LOCAL_WORKSPACE_NAME);
        try {
            FileUtil.createDir(workspaceDir);
        } catch (RuntimeException e) {
            HyperLog.w(Constants.TAG, "Initial local project workspace creation failed: "
                    + e.getMessage(), e);
            return null;
        }
        long now = System.currentTimeMillis();
        String displayName = uniqueDisplayName(
                projects, requestedName, INITIAL_LOCAL_PROJECT_UID);
        return new ProjectInfo(
                INITIAL_LOCAL_PROJECT_UID,
                ProjectType.LOCAL,
                null,
                0L,
                displayName,
                displayName,
                null,
                workspaceDir.getAbsolutePath(),
                WORKSPACE_MAP_NAME,
                now,
                0L);
    }

    private static ProjectInfo resolveConfiguredProject(
            SharedPreferences preferences,
            List<ProjectInfo> projects) {
        String activeUid = preferences.getString(
                SettingsConstants.KEY_PREF_ACTIVE_COLLECTOR_PROJECT_UID, "");
        ProjectInfo active = findProject(projects, activeUid);
        if (active != null) {
            return active;
        }
        String mapPath = preferences.getString(SettingsConstants.KEY_PREF_MAP_PATH, "");
        String mapName = preferences.getString(SettingsConstantsUI.KEY_PREF_MAP_NAME, "");
        for (ProjectInfo project : projects) {
            if (project != null && project.getMapPath().equals(mapPath)
                    && project.getMapName().equals(mapName)) {
                return project;
            }
        }
        return null;
    }

    private static File findLegacyMap(
            Context context,
            SharedPreferences preferences) {
        File defaultMapRoot = context.getExternalFilesDir(KEY_PREF_MAP);
        if (defaultMapRoot == null) {
            defaultMapRoot = new File(context.getFilesDir(), KEY_PREF_MAP);
        }
        String configuredPath = preferences.getString(
                SettingsConstants.KEY_PREF_MAP_PATH, defaultMapRoot.getPath());
        String configuredName = preferences.getString(
                SettingsConstantsUI.KEY_PREF_MAP_NAME, "default");
        File configuredMap = new File(configuredPath, configuredName + Constants.MAP_EXT);
        if (!isWorkspacePathSafe(context, configuredPath) && configuredMap.isFile()) {
            return configuredMap;
        }
        File defaultMap = new File(defaultMapRoot, "default" + Constants.MAP_EXT);
        return defaultMap.isFile() ? defaultMap : null;
    }

    private static ProjectInfo copyProject(
            ProjectInfo source,
            String displayName,
            long lastOpenedAt) {
        return new ProjectInfo(
                source.getProjectUid(),
                source.getProjectType(),
                source.getAccountName(),
                source.getProjectRemoteId(),
                displayName,
                source.getSourceName(),
                source.getDistrict(),
                source.getMapPath(),
                source.getMapName(),
                source.getCreatedAt(),
                lastOpenedAt);
    }

    private static ProjectInfo mostRecentlyOpenedProjectExcept(
            List<ProjectInfo> projects,
            String excludedUid) {
        ProjectInfo result = null;
        if (projects == null) {
            return null;
        }
        for (ProjectInfo project : projects) {
            if (project == null || project.getProjectUid().equals(excludedUid)) {
                continue;
            }
            if (result == null || project.getLastOpenedAt() > result.getLastOpenedAt()) {
                result = project;
            }
        }
        return result;
    }

    private static String uniqueDisplayNameLocked(
            Context context,
            String requestedName,
            String excludedUid) {
        return uniqueDisplayName(loadProjectsLocked(context), requestedName, excludedUid);
    }

    static String uniqueDisplayName(
            List<ProjectInfo> projects,
            String requestedName,
            String excludedUid) {
        String base = TextUtils.isEmpty(requestedName) ? "Project" : requestedName.trim();
        String candidate = base;
        int suffix = 2;
        while (containsDisplayName(projects, candidate, excludedUid)) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private static boolean containsDisplayName(
            List<ProjectInfo> projects,
            String candidate,
            String excludedUid) {
        if (projects == null) {
            return false;
        }
        for (ProjectInfo project : projects) {
            if (project == null
                    || (!TextUtils.isEmpty(excludedUid)
                            && excludedUid.equals(project.getProjectUid()))) {
                continue;
            }
            if (candidate.equalsIgnoreCase(project.getName())) {
                return true;
            }
        }
        return false;
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
        boolean rewriteRegistry = false;
        if (registryFile != null && (registryFile.exists()
                || new File(registryFile.getPath() + ".bak").exists())) {
            try {
            JSONObject root = new JSONObject(readAtomicFile(registryFile));
            int storedSchemaVersion = root.optInt(JSON_SCHEMA_VERSION, -1);
            if (storedSchemaVersion != 1 && storedSchemaVersion != SCHEMA_VERSION) {
                throw new JSONException("unsupported schema_version="
                        + storedSchemaVersion);
            }
            rewriteRegistry = storedSchemaVersion != SCHEMA_VERSION;
            JSONArray items = root.optJSONArray(JSON_PROJECTS);
            if (items == null) {
                throw new JSONException("projects array missing");
            }
            for (int i = 0; i < items.length(); i++) {
                ProjectInfo project = ProjectInfo.fromJSON(items.optJSONObject(i));
                if (project != null && isWorkspacePathSafe(context, project.getMapPath())) {
                    projects.add(project);
                } else {
                    rewriteRegistry = true;
                }
            }
            } catch (IOException | JSONException e) {
            HyperLog.w(Constants.TAG, "CollectorProjectRegistry: failed to read registry: "
                    + e.getMessage(), e);
                projects.clear();
                rewriteRegistry = true;
            }
        }

        ArrayList<ProjectInfo> recovered = scanWorkspacesLocked(context);
        for (ProjectInfo candidate : recovered) {
            if (findProject(projects, candidate.getProjectUid()) == null) {
                projects.add(candidate);
                rewriteRegistry = true;
                HyperLog.w(Constants.TAG, "CollectorProjectRegistry: recovered workspace uid="
                        + candidate.getProjectUid() + " path=" + candidate.getMapPath());
            }
        }
        if (rewriteRegistry && !projects.isEmpty()) {
            saveProjectsLocked(context, projects);
        }
        return projects;
    }

    private static boolean saveProjectsLocked(Context context, List<ProjectInfo> projects) {
        File registryFile = getRegistryFile(context);
        if (registryFile == null) {
            return false;
        }
        File parent = registryFile.getParentFile();
        if (parent != null) {
            try {
                FileUtil.createDir(parent);
            } catch (RuntimeException e) {
                HyperLog.w(Constants.TAG, "CollectorProjectRegistry: failed to create registry dir "
                        + parent.getPath() + ": " + e.getMessage(), e);
                return false;
            }
        }
        try {
            JSONObject root = new JSONObject();
            root.put(JSON_SCHEMA_VERSION, SCHEMA_VERSION);
            JSONArray array = new JSONArray();
            if (projects != null) {
                for (ProjectInfo project : projects) {
                    if (project != null) {
                        JSONObject projectJson = project.toJSON();
                        File workspaceDir = new File(project.getMapPath());
                        FileUtil.createDir(workspaceDir);
                        writeAtomicFile(
                                new File(workspaceDir, WORKSPACE_INFO_NAME),
                                projectJson.toString());
                        array.put(projectJson);
                    }
                }
            }
            root.put(JSON_PROJECTS, array);
            writeAtomicFile(registryFile, root.toString());
            return true;
        } catch (IOException | JSONException | RuntimeException e) {
            HyperLog.w(Constants.TAG, "CollectorProjectRegistry: failed to save registry: "
                    + e.getMessage(), e);
            return false;
        }
    }

    private static String readAtomicFile(File file) throws IOException {
        AtomicFile atomicFile = new AtomicFile(file);
        try (FileInputStream input = atomicFile.openRead();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void writeAtomicFile(File file, String value) throws IOException {
        AtomicFile atomicFile = new AtomicFile(file);
        FileOutputStream output = null;
        try {
            output = atomicFile.startWrite();
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
            atomicFile.finishWrite(output);
        } catch (IOException | RuntimeException e) {
            if (output != null) {
                atomicFile.failWrite(output);
            }
            throw e;
        }
    }

    private static ArrayList<ProjectInfo> scanWorkspacesLocked(Context context) {
        ArrayList<ProjectInfo> recovered = new ArrayList<>();
        File root = getWorkspacesRootDir(context);
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) {
            return recovered;
        }
        for (File dir : dirs) {
            if (dir.getName().startsWith(".deleting_")) {
                continue;
            }
            File workspaceInfo = new File(dir, WORKSPACE_INFO_NAME);
            if (workspaceInfo.isFile()) {
                try {
                    ProjectInfo project = ProjectInfo.fromJSON(
                            new JSONObject(readAtomicFile(workspaceInfo)));
                    if (project != null && isWorkspacePathSafe(context, project.getMapPath())) {
                        recovered.add(project);
                        continue;
                    }
                } catch (IOException | JSONException e) {
                    HyperLog.w(Constants.TAG,
                            "CollectorProjectRegistry: project sidecar skipped "
                                    + workspaceInfo.getPath() + ": " + e.getMessage());
                }
            }
            File mapFile = new File(dir, WORKSPACE_MAP_NAME + Constants.MAP_EXT);
            if (!mapFile.isFile()) {
                continue;
            }
            try {
                JSONObject mapJson = new JSONObject(FileUtil.readFromFile(mapFile));
                CollectorProjectMetadata metadata = CollectorProjectMetadata.fromJSON(
                        mapJson.optJSONObject("collector_project"));
                if (metadata == null || !metadata.isValid()) {
                    continue;
                }
                long timestamp = Math.max(dir.lastModified(), mapFile.lastModified());
                recovered.add(new ProjectInfo(
                        metadata.getProjectUid(),
                        ProjectType.WEBGIS,
                        metadata.getAccountName(),
                        metadata.getProjectRemoteId(),
                        metadata.getName(),
                        metadata.getName(),
                        metadata.getDistrict(),
                        dir.getAbsolutePath(),
                        WORKSPACE_MAP_NAME,
                        timestamp,
                        timestamp));
            } catch (IOException | JSONException e) {
                HyperLog.w(Constants.TAG,
                        "CollectorProjectRegistry: workspace scan skipped " + mapFile.getPath()
                                + ": " + e.getMessage());
            }
        }
        return recovered;
    }

    private static boolean isWorkspacePathSafe(Context context, String path) {
        if (context == null || TextUtils.isEmpty(path)) {
            return false;
        }
        try {
            String root = getWorkspacesRootDir(context).getCanonicalPath();
            String candidate = new File(path).getCanonicalPath();
            return candidate.startsWith(root + File.separator);
        } catch (IOException e) {
            return false;
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
