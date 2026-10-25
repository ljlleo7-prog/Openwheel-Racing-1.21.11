package com.openwheelracing.content.race;

/** Interpolates finish crossings within a 50ms server tick, retaining integer milliseconds. */
public final class LapTimingClock {
    private LapTimingClock() { }

    public static double crossingTime(long gameTick, double movementFraction) {
        return gameTick - (1.0 - Math.clamp(movementFraction, 0.0, 1.0));
    }

    public static int elapsedMillis(double startedAt, double finishedAt) {
        return startedAt < 0.0 ? 0 : Math.max(0, (int) Math.round((finishedAt - startedAt) * 50.0));
    }
}
