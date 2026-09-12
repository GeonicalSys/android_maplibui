package com.nextgis.maplibui.util;

/** Command admission is shared by the UI and service, including stale notification intents. */
public final class WalkSessionPolicy {
    public enum Phase { RECORDING, FINISHING, FINISHED }
    public enum Command { RESUME, FINISH, DISCARD }

    private WalkSessionPolicy() { }

    public static boolean mayControl(String currentId, String commandId, String pointId,
                                     Phase phase, Command command) {
        if (currentId == null || currentId.isEmpty() || !currentId.equals(commandId)
                || phase == null || command == null || (pointId != null && !pointId.isEmpty())) return false;
        if (command == Command.DISCARD) return phase != Phase.FINISHING;
        if (command == Command.FINISH) return phase != Phase.FINISHED;
        return phase == Phase.RECORDING;
    }

    public static boolean mayStartPoint(String sessionId, String pointId, Phase phase) {
        return sessionId != null && !sessionId.isEmpty()
                && (pointId == null || pointId.isEmpty()) && phase == Phase.RECORDING;
    }
}
