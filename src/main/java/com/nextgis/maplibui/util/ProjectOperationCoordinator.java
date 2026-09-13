/*
 * Project: NextGIS Mobile project-workspace operation coordination.
 */

package com.nextgis.maplibui.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.SettingsConstants;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Process-wide ownership for operations that use a project workspace.
 *
 * <p>Background operations for the same workspace may overlap when one starts a dependent stage
 * (for example sync scheduling a staged layer fill). A project mutation is exclusive. This keeps
 * map preferences and the live {@code MapBase} identity stable until every database user has
 * released its lease.</p>
 */
public final class ProjectOperationCoordinator {
    public enum Kind {
        DATA_SYNC(false),
        LAYER_FILL(false),
        SCHEMA_REBUILD(false),
        PROJECT_SWITCH(true),
        PROJECT_CREATE(true),
        PROJECT_RENAME(true),
        UNDERLAY_MIGRATION(true),
        PROJECT_DELETE(true);

        private final boolean projectMutation;

        Kind(boolean projectMutation) {
            this.projectMutation = projectMutation;
        }

        public boolean isProjectMutation() {
            return projectMutation;
        }
    }

    public static final class Snapshot {
        private final boolean busy;
        private final Kind oldestKind;
        private final String workspaceKey;
        private final int operationCount;
        private final long oldestStartedAt;
        private final long lastHeartbeatAt;

        private Snapshot(
                boolean busy,
                Kind oldestKind,
                String workspaceKey,
                int operationCount,
                long oldestStartedAt,
                long lastHeartbeatAt) {
            this.busy = busy;
            this.oldestKind = oldestKind;
            this.workspaceKey = workspaceKey;
            this.operationCount = operationCount;
            this.oldestStartedAt = oldestStartedAt;
            this.lastHeartbeatAt = lastHeartbeatAt;
        }

        public boolean isBusy() {
            return busy;
        }

        public Kind getOldestKind() {
            return oldestKind;
        }

        public String getWorkspaceKey() {
            return workspaceKey;
        }

        public int getOperationCount() {
            return operationCount;
        }

        public long getOldestStartedAt() {
            return oldestStartedAt;
        }

        public long getLastHeartbeatAt() {
            return lastHeartbeatAt;
        }
    }

    public static final class Lease implements AutoCloseable {
        private final long id;
        private boolean closed;

        private Lease(long id) {
            this.id = id;
        }

        public void heartbeat() {
            synchronized (LOCK) {
                ActiveOperation operation = ACTIVE.get(id);
                if (operation != null) {
                    operation.lastHeartbeatAt = System.currentTimeMillis();
                }
            }
        }

        /**
         * Waits until this operation may touch project databases without overlapping a full sync.
         * A waiting lease still blocks project switching/deletion, so the workspace identity cannot
         * change between scheduling and execution.
         */
        public boolean awaitDatabaseAccess() {
            synchronized (LOCK) {
                while (!closed) {
                    ActiveOperation current = ACTIVE.get(id);
                    if (current == null) {
                        return false;
                    }
                    if (!hasDatabaseBlockerLocked(id, current)) {
                        current.lastHeartbeatAt = System.currentTimeMillis();
                        return true;
                    }
                    current.lastHeartbeatAt = System.currentTimeMillis();
                    try {
                        LOCK.wait(1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                return false;
            }
        }

        @Override
        public void close() {
            synchronized (LOCK) {
                if (closed) {
                    return;
                }
                closed = true;
                ActiveOperation operation = ACTIVE.remove(id);
                LOCK.notifyAll();
                if (operation != null && operation.logLifecycle) {
                    HyperLog.v(Constants.TAG, "Project operation finished kind="
                            + operation.kind + " workspace=" + operation.workspaceKey
                            + " remaining=" + ACTIVE.size());
                }
            }
        }
    }

    private static final class ActiveOperation {
        final Kind kind;
        final String workspaceKey;
        final long startedAt;
        final boolean logLifecycle;
        long lastHeartbeatAt;

        ActiveOperation(Kind kind, String workspaceKey, long now, boolean logLifecycle) {
            this.kind = kind;
            this.workspaceKey = workspaceKey;
            this.startedAt = now;
            this.logLifecycle = logLifecycle;
            this.lastHeartbeatAt = now;
        }
    }

    private static final Object LOCK = new Object();
    private static final Map<Long, ActiveOperation> ACTIVE = new LinkedHashMap<>();
    private static long nextId = 1L;

    private ProjectOperationCoordinator() {
    }

    public static Lease tryBegin(Context context, Kind kind) {
        if (kind != null && kind.isProjectMutation() && WalkSessionStore.load(context) != null) {
            HyperLog.w(Constants.TAG, "Project change deferred while a walk draft owns its workspace");
            return null;
        }
        return tryBeginInternal(kind, activeWorkspaceKey(context), true);
    }

    static Lease tryBegin(Kind kind, String workspaceKey) {
        return tryBeginInternal(kind, workspaceKey, false);
    }

    private static Lease tryBeginInternal(Kind kind, String workspaceKey, boolean logLifecycle) {
        if (kind == null) {
            return null;
        }
        String normalizedWorkspace = workspaceKey != null ? workspaceKey : "";
        synchronized (LOCK) {
            if (!canBeginLocked(kind, normalizedWorkspace)) {
                if (logLifecycle) {
                    HyperLog.w(Constants.TAG, "Project operation blocked kind=" + kind
                            + " workspace=" + normalizedWorkspace + " active=" + ACTIVE.size());
                }
                return null;
            }
            long id = nextId++;
            long now = System.currentTimeMillis();
            ACTIVE.put(id, new ActiveOperation(kind, normalizedWorkspace, now, logLifecycle));
            if (logLifecycle) {
                HyperLog.v(Constants.TAG, "Project operation started kind=" + kind
                        + " workspace=" + normalizedWorkspace + " active=" + ACTIVE.size());
            }
            return new Lease(id);
        }
    }

    public static boolean isBusy() {
        synchronized (LOCK) {
            return !ACTIVE.isEmpty();
        }
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            if (ACTIVE.isEmpty()) {
                return new Snapshot(false, null, null, 0, 0L, 0L);
            }
            ActiveOperation oldest = null;
            long latestHeartbeat = 0L;
            for (ActiveOperation operation : ACTIVE.values()) {
                if (oldest == null || operation.startedAt < oldest.startedAt) {
                    oldest = operation;
                }
                latestHeartbeat = Math.max(latestHeartbeat, operation.lastHeartbeatAt);
            }
            return new Snapshot(
                    true,
                    oldest != null ? oldest.kind : null,
                    oldest != null ? oldest.workspaceKey : null,
                    ACTIVE.size(),
                    oldest != null ? oldest.startedAt : 0L,
                    latestHeartbeat);
        }
    }

    public static String activeWorkspaceKey(Context context) {
        if (context == null) {
            return "";
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String projectUid = preferences.getString(
                SettingsConstants.KEY_PREF_ACTIVE_COLLECTOR_PROJECT_UID, "");
        String mapPath = preferences.getString(SettingsConstants.KEY_PREF_MAP_PATH, "");
        if (!isEmpty(mapPath)) {
            try {
                mapPath = new File(mapPath).getCanonicalPath();
            } catch (IOException ignored) {
                mapPath = new File(mapPath).getAbsolutePath();
            }
        }
        return (projectUid != null ? projectUid : "") + "|" + mapPath;
    }

    private static boolean canBeginLocked(Kind kind, String workspaceKey) {
        if (ACTIVE.isEmpty()) {
            return true;
        }
        if (kind.isProjectMutation()) {
            return false;
        }
        for (ActiveOperation operation : ACTIVE.values()) {
            if (operation.kind.isProjectMutation()) {
                return false;
            }
            // Layer fill may be a dependent sync stage, but two full sync passes must never use
            // the same project databases concurrently (for example after a double tap).
            if (kind == Kind.DATA_SYNC && operation.kind == Kind.DATA_SYNC) {
                return false;
            }
            if (kind == Kind.DATA_SYNC
                    && (operation.kind == Kind.LAYER_FILL
                            || operation.kind == Kind.SCHEMA_REBUILD)) {
                return false;
            }
            if (!isEmpty(operation.workspaceKey)
                    && !isEmpty(workspaceKey)
                    && !operation.workspaceKey.equals(workspaceKey)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasDatabaseBlockerLocked(long ownId, ActiveOperation current) {
        for (Map.Entry<Long, ActiveOperation> entry : ACTIVE.entrySet()) {
            if (entry.getKey() == ownId) {
                continue;
            }
            ActiveOperation other = entry.getValue();
            if (!isEmpty(current.workspaceKey)
                    && !isEmpty(other.workspaceKey)
                    && !current.workspaceKey.equals(other.workspaceKey)) {
                continue;
            }
            if (other.kind == Kind.DATA_SYNC) {
                return true;
            }
            if (current.kind == Kind.SCHEMA_REBUILD && other.kind == Kind.LAYER_FILL) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    static void resetForTests() {
        synchronized (LOCK) {
            ACTIVE.clear();
            nextId = 1L;
        }
    }
}
