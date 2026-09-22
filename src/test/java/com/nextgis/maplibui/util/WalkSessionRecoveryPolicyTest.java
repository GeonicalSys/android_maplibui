package com.nextgis.maplibui.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WalkSessionRecoveryPolicyTest {
    @Test public void unconfirmedStoppedStartIsDiscardedSilently() {
        assertEquals(WalkSessionRecoveryPolicy.Action.DISCARD_UNCONFIRMED,
                WalkSessionRecoveryPolicy.decide(true, false, false,
                        WalkSessionPolicy.Phase.RECORDING, 1, 0));
        assertEquals(WalkSessionRecoveryPolicy.Action.DISCARD_UNCONFIRMED,
                WalkSessionRecoveryPolicy.decide(false, false, false,
                        WalkSessionPolicy.Phase.RECORDING, 1, 0));
    }

    @Test public void confirmedStoppedStartIsRecoverableOnItsMap() {
        assertEquals(WalkSessionRecoveryPolicy.Action.OFFER_CONTINUE_OR_DISCARD,
                WalkSessionRecoveryPolicy.decide(true, false, false,
                        WalkSessionPolicy.Phase.RECORDING, 2, 100));
    }

    @Test public void foreignMapNeverBecomesAnInvisibleGlobalLock() {
        assertEquals(WalkSessionRecoveryPolicy.Action.OFFER_FOREIGN_MAP_RESET,
                WalkSessionRecoveryPolicy.decide(false, false, false,
                        WalkSessionPolicy.Phase.RECORDING, 2, 100));
        assertEquals(WalkSessionRecoveryPolicy.Action.OFFER_FOREIGN_MAP_RESET,
                WalkSessionRecoveryPolicy.decide(false, true, true,
                        WalkSessionPolicy.Phase.RECORDING, 2, 100));
    }

    @Test public void stoppedFinishingSessionBecomesCompletable() {
        assertEquals(WalkSessionRecoveryPolicy.Action.FINALIZE_FINISHING,
                WalkSessionRecoveryPolicy.decide(true, false, false,
                        WalkSessionPolicy.Phase.FINISHING, 2, 100));
    }

    @Test public void runningPointAndFinishedSessionsKeepTheirOwners() {
        assertEquals(WalkSessionRecoveryPolicy.Action.KEEP,
                WalkSessionRecoveryPolicy.decide(true, true, false,
                        WalkSessionPolicy.Phase.RECORDING, 1, 0));
        assertEquals(WalkSessionRecoveryPolicy.Action.KEEP,
                WalkSessionRecoveryPolicy.decide(true, false, true,
                        WalkSessionPolicy.Phase.RECORDING, 2, 100));
        assertEquals(WalkSessionRecoveryPolicy.Action.KEEP,
                WalkSessionRecoveryPolicy.decide(true, false, false,
                        WalkSessionPolicy.Phase.FINISHED, 2, 100));
    }
}
