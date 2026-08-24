package com.nextgis.maplibui.util;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProjectOperationCoordinatorTest {
    @After
    public void tearDown() {
        ProjectOperationCoordinator.resetForTests();
    }

    @Test
    public void backgroundStagesForSameWorkspaceBlockProjectSwitch() {
        ProjectOperationCoordinator.Lease sync = ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.DATA_SYNC, "project-a");
        ProjectOperationCoordinator.Lease fill = ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.LAYER_FILL, "project-a");

        assertNotNull(sync);
        assertNotNull(fill);
        assertNull(ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.PROJECT_SWITCH, "project-b"));

        sync.close();
        assertTrue(ProjectOperationCoordinator.isBusy());
        fill.close();
        assertFalse(ProjectOperationCoordinator.isBusy());
    }

    @Test
    public void differentWorkspaceCannotJoinBackgroundOperation() {
        ProjectOperationCoordinator.Lease sync = ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.DATA_SYNC, "project-a");

        assertNotNull(sync);
        assertNull(ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.LAYER_FILL, "project-b"));
        sync.close();
    }

    @Test
    public void projectMutationIsExclusive() {
        ProjectOperationCoordinator.Lease mutation = ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.PROJECT_DELETE, "project-a");

        assertNotNull(mutation);
        assertNull(ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.DATA_SYNC, "project-a"));
        assertNull(ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.PROJECT_CREATE, "project-a"));
        mutation.close();
    }

    @Test
    public void underlayMigrationIsExclusiveProjectMutation() {
        ProjectOperationCoordinator.Lease migration = ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.UNDERLAY_MIGRATION, "project-a");

        assertNotNull(migration);
        assertNull(ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.DATA_SYNC, "project-a"));
        assertNull(ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.PROJECT_SWITCH, "project-b"));
        migration.close();
    }

    @Test
    public void duplicateDataSyncIsRejectedButDependentFillIsAllowed() {
        ProjectOperationCoordinator.Lease sync = ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.DATA_SYNC, "project-a");
        assertNotNull(sync);
        assertNull(ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.DATA_SYNC, "project-a"));

        ProjectOperationCoordinator.Lease fill = ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.LAYER_FILL, "project-a");
        assertNotNull(fill);
        fill.close();
        sync.close();
    }

    @Test
    public void dependentFillWaitsUntilDataSyncReleasesDatabase() throws Exception {
        ProjectOperationCoordinator.Lease sync = ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.DATA_SYNC, "project-a");
        ProjectOperationCoordinator.Lease fill = ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.LAYER_FILL, "project-a");
        assertNotNull(sync);
        assertNotNull(fill);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            entered.countDown();
            if (fill.awaitDatabaseAccess()) {
                ready.countDown();
            }
        });
        waiter.start();
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        assertFalse(ready.await(100, TimeUnit.MILLISECONDS));

        sync.close();
        assertTrue(ready.await(1, TimeUnit.SECONDS));
        assertNull(ProjectOperationCoordinator.tryBegin(
                ProjectOperationCoordinator.Kind.DATA_SYNC, "project-a"));
        fill.close();
        waiter.join(1000L);
    }
}
