package com.nextgis.maplibui.util;

import com.nextgis.maplib.datasource.ngw.*;
import org.junit.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

public class NgwResourceSelectionStateTest {
    private static class TestGroup extends ResourceGroup {
        TestGroup(long id, Connection connection) {
            super(id, connection);
            mChildrenLoaded = true;
            mType = Connection.NGWResourceTypeResourceGroup;
            mDescription = "large metadata should never enter the saved state".repeat(100);
        }
        TestGroup add(long id) {
            TestGroup child = new TestGroup(id, mConnection);
            child.setParent(this);
            mChildren.add(child);
            return child;
        }
        @Override public void loadChildren(boolean skip) { }
    }

    private static class TestConnection extends Connection {
        TestConnection(String account, String url) {
            super(account, "private-login", "private-password", url);
            mRootResource = new TestGroup(0, this);
            mRootResource.setParent(this);
            mIsConnected = true;
        }
        TestGroup root() { return (TestGroup) mRootResource; }
    }

    private static Connections accounts(Connection... accounts) {
        Connections result = new Connections("accounts");
        for (Connection account : accounts) result.add(account);
        return result;
    }

    @Test public void loadedTreeAndCredentialsDoNotEnterSavedState() throws Exception {
        TestConnection account = new TestConnection("one", "https://example.invalid");
        Connections connections = accounts(account);
        String before = NgwResourceSelectionState.save(connections, account, Collections.emptyList());
        for (int i = 1; i <= 10000; i++) account.root().add(i);
        String after = NgwResourceSelectionState.save(connections, account, Collections.emptyList());
        assertEquals(before, after);
        assertTrue(after.length() < 1024);
        assertFalse(after.contains("private-login"));
        assertFalse(after.contains("private-password"));
        assertFalse(after.contains("large metadata"));
    }

    @Test public void restoresRemotePathsAndChecksWithNewProcessLocalIds() throws Exception {
        TestConnection old = new TestConnection("one", "https://example.invalid");
        TestGroup group = old.root().add(10);
        TestGroup selected = group.add(20);
        String saved = NgwResourceSelectionState.save(accounts(old), group,
                Arrays.asList(new CheckState(selected.getId(), true, true), new CheckState(group.getId(), false, false)));
        TestConnection fresh = new TestConnection("one", "https://example.invalid");
        TestGroup freshGroup = fresh.root().add(10);
        TestGroup freshSelected = freshGroup.add(20);
        NgwResourceSelectionState.Restored restored = NgwResourceSelectionState.restore(accounts(fresh), saved, false);
        assertSame(freshGroup, restored.current);
        assertEquals(1, restored.checks.size());
        assertNotEquals(selected.getId(), restored.checks.get(0).getId());
        assertEquals(freshSelected.getId(), restored.checks.get(0).getId());
        assertTrue(restored.checks.get(0).isCheckState1());
        assertTrue(restored.checks.get(0).isCheckState2());
    }

    @Test public void deletedAccountOrChangedServerFailsWithoutDroppingSelection() throws Exception {
        TestConnection old = new TestConnection("one", "https://example.invalid");
        String saved = NgwResourceSelectionState.forConnection(accounts(old), old);
        assertThrows(IOException.class, () -> NgwResourceSelectionState.restore(accounts(), saved, false));
        assertThrows(IOException.class, () -> NgwResourceSelectionState.restore(
                accounts(new TestConnection("one", "https://other.invalid")), saved, false));
    }

    @Test public void missingResourceAndFailedChildrenLoadAreNotSuccessfulRestores() throws Exception {
        TestConnection old = new TestConnection("one", "https://example.invalid");
        TestGroup selected = old.root().add(20);
        String saved = NgwResourceSelectionState.save(accounts(old), selected, Collections.emptyList());
        TestConnection fresh = new TestConnection("one", "https://example.invalid");
        assertThrows(IOException.class, () -> NgwResourceSelectionState.restore(accounts(fresh), saved, false));
        String accountState = NgwResourceSelectionState.forConnection(accounts(fresh), fresh);
        fresh.root().setLoadChildren(false);
        assertThrows(IOException.class, () -> NgwResourceSelectionState.restore(accounts(fresh), accountState, false));
    }

    @Test public void sameRemoteIdInDifferentAccountsDoesNotCrossRestore() throws Exception {
        TestConnection first = new TestConnection("one", "https://one.invalid");
        TestConnection second = new TestConnection("two", "https://two.invalid");
        TestGroup a = first.root().add(20);
        TestGroup b = second.root().add(20);
        String saved = NgwResourceSelectionState.save(accounts(first, second), second,
                Arrays.asList(new CheckState(a.getId(), true, false), new CheckState(b.getId(), false, true)));
        TestConnection newFirst = new TestConnection("one", "https://one.invalid");
        TestConnection newSecond = new TestConnection("two", "https://two.invalid");
        TestGroup newA = newFirst.root().add(20);
        TestGroup newB = newSecond.root().add(20);
        NgwResourceSelectionState.Restored restored = NgwResourceSelectionState.restore(
                accounts(newSecond, newFirst), saved, false);
        assertSame(newSecond, restored.current);
        assertEquals(newA.getId(), restored.checks.get(0).getId());
        assertEquals(newB.getId(), restored.checks.get(1).getId());
    }

    @Test public void restoreHonorsCancellationWithoutClearingIt() throws Exception {
        TestConnection account = new TestConnection("one", "https://example.invalid");
        String saved = NgwResourceSelectionState.forConnection(accounts(account), account);
        Thread.currentThread().interrupt();
        try {
            assertThrows(java.io.InterruptedIOException.class,
                    () -> NgwResourceSelectionState.restore(accounts(account), saved, false));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
