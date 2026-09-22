package com.nextgis.maplibui.util;

/** Pure startup reconciliation rules for a durable walk session. */
public final class WalkSessionRecoveryPolicy {
    public enum Action {
        KEEP,
        DISCARD_UNCONFIRMED,
        FINALIZE_FINISHING,
        OFFER_CONTINUE_OR_DISCARD,
        OFFER_FOREIGN_MAP_RESET
    }

    private WalkSessionRecoveryPolicy() { }

    public static Action decide(boolean currentMap, boolean serviceRunning,
            boolean pointActive, WalkSessionPolicy.Phase phase,
            long revision, long updatedAt) {
        if (currentMap && (serviceRunning || pointActive))
            return Action.KEEP;
        if (!pointActive && phase == WalkSessionPolicy.Phase.RECORDING
                && revision <= 1 && updatedAt <= 0)
            return Action.DISCARD_UNCONFIRMED;
        if (!currentMap)
            return Action.OFFER_FOREIGN_MAP_RESET;
        if (serviceRunning || pointActive || phase == WalkSessionPolicy.Phase.FINISHED)
            return Action.KEEP;
        if (phase == WalkSessionPolicy.Phase.FINISHING)
            return Action.FINALIZE_FINISHING;
        if (phase == WalkSessionPolicy.Phase.RECORDING)
            return Action.OFFER_CONTINUE_OR_DISCARD;
        return Action.KEEP;
    }
}
