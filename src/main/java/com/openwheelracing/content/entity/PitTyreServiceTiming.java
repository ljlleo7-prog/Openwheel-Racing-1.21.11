package com.openwheelracing.content.entity;

/** Pure timing rules shared by all four physical tyre-service operations. */
public final class PitTyreServiceTiming {
    public static final int MIN_DURATION_TICKS = 6;
    public static final int MAX_DURATION_TICKS = 10;
    public static final int INPUT_BUFFER_TICKS = 2;
    public static final int EARLY_INPUT_SETBACK_TICKS = 2;
    public static final int RETURN_NONE = 0;
    public static final int RETURN_RESERVED_NEW = 1;
    public static final int RETURN_REMOVED_OLD = 2;

    private PitTyreServiceTiming() { }

    public static int durationFromRoll(int roll) {
        return MIN_DURATION_TICKS + Math.floorMod(roll, MAX_DURATION_TICKS - MIN_DURATION_TICKS + 1);
    }

    public static int applyEarlySetback(int remaining, int duration) {
        return Math.min(duration, remaining + EARLY_INPUT_SETBACK_TICKS);
    }

    public static int cancellationReturn(boolean installationCompleted, boolean newTyresReserved) {
        if (installationCompleted) return RETURN_REMOVED_OLD;
        return newTyresReserved ? RETURN_RESERVED_NEW : RETURN_NONE;
    }
}
