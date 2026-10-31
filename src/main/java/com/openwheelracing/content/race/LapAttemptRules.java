package com.openwheelracing.content.race;

/** Timing eligibility must not depend on racing-line localization confidence. */
public final class LapAttemptRules {
    private LapAttemptRules() { }

    public static boolean validStart(double distanceToStartLine, double verticalDelta, boolean forward) {
        return validStart(false, distanceToStartLine, verticalDelta, forward);
    }

    /** A detected forward gate crossing is authoritative; grace only repairs sampled starts. */
    public static boolean validStart(boolean forwardStartLineTriggered, double distanceToStartLine,
                                     double verticalDelta, boolean forward) {
        return validStart(forwardStartLineTriggered, forward && Double.isFinite(distanceToStartLine) && distanceToStartLine >= 0.0
            && distanceToStartLine <= 3.0 && Math.abs(verticalDelta) <= 3.0);
    }

    public static boolean validStart(boolean forwardStartLineTriggered, boolean sampledWithinStartGrace) {
        return forwardStartLineTriggered || sampledWithinStartGrace;
    }

    public static boolean shouldDiscard(int elapsedMillis, double traveledMeters) {
        return elapsedMillis < 200 || !Double.isFinite(traveledMeters) || traveledMeters < 10.0;
    }
}
