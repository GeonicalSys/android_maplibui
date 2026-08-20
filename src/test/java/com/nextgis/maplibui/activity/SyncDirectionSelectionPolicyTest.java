package com.nextgis.maplibui.activity;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SyncDirectionSelectionPolicyTest {
    @Test
    public void initialSpinnerCallback_preservesPersistedTwoWayDirection() {
        assertEquals(3, SyncDirectionSelectionPolicy.resolve(3, 3, true));
    }

    @Test
    public void configurableLayer_acceptsExplicitDirectionChange() {
        assertEquals(3, SyncDirectionSelectionPolicy.resolve(2, 3, true));
    }

    @Test
    public void lockedLayer_rejectsDirectionChange() {
        assertEquals(2, SyncDirectionSelectionPolicy.resolve(2, 3, false));
    }
}
