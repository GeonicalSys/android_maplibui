package com.nextgis.maplibui.util;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.nextgis.maplibui.util.WalkSessionPolicy.*;

public class WalkSessionPolicyTest {
    @Test public void pointCreationBlocksEveryWalkCommandInEveryPhaseIncludingOldNotifications() {
        for (Phase phase : Phase.values()) for (Command command : Command.values()) {
            assertFalse(mayControl("walk-a", "walk-a", "point-from-chooser", phase, command));
            assertFalse(mayControl("walk-b", "walk-a", "", phase, command));
            assertFalse(mayControl("walk-a", null, "", phase, command));
        }
    }

    @Test public void finishingCannotStartAPointOrResumeAndFinishedGeometryCanBeDiscarded() {
        assertTrue(mayStartPoint("walk", "", Phase.RECORDING));
        assertFalse(mayStartPoint("walk", "point", Phase.RECORDING));
        assertFalse(mayStartPoint("walk", "", Phase.FINISHING));
        assertFalse(mayStartPoint("walk", "", Phase.FINISHED));
        assertFalse(mayControl("walk", "walk", "", Phase.FINISHING, Command.RESUME));
        assertFalse(mayControl("walk", "walk", "", Phase.FINISHED, Command.RESUME));
        assertFalse(mayControl("walk", "walk", "", Phase.FINISHED, Command.FINISH));
        assertTrue(mayControl("walk", "walk", "", Phase.FINISHED, Command.DISCARD));
        assertTrue(mayControl("walk", "walk", "", Phase.RECORDING, Command.FINISH));
    }
}
