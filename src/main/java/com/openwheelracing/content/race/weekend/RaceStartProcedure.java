package com.openwheelracing.content.race.weekend;

/** Traditional sequence: reds illuminate one per second, then all go out after the final-light delay. */
public final class RaceStartProcedure {
    public static final long LIGHT_INTERVAL_TICKS = 20L;
    public static final int RED_LIGHT_COUNT = 5;
    public static final long RED_BUILDUP_TICKS = (RED_LIGHT_COUNT - 1L) * LIGHT_INTERVAL_TICKS;
    public static final int MAX_RANDOM_HOLD_TICKS = 2 * 20;
    // First red is immediate. Four one-second advances plus the legacy minimum
    // one-second lights-out delay give the earliest release at five seconds.
    public static final long RED_SEQUENCE_TICKS = RED_BUILDUP_TICKS + LIGHT_INTERVAL_TICKS;

    private RaceStartProcedure() { }

    /** The legacy delay belongs to phase five, after the last red has illuminated. */
    public static long nextPhaseDelayTicks(int phase, int randomDelayTicks) {
        return LIGHT_INTERVAL_TICKS + (phase == RED_LIGHT_COUNT ? Math.clamp(randomDelayTicks, 0, MAX_RANDOM_HOLD_TICKS) : 0);
    }

    public static long durationTicks(int randomDelayTicks) {
        return RED_BUILDUP_TICKS + nextPhaseDelayTicks(RED_LIGHT_COUNT, randomDelayTicks);
    }

    public static int redLightCount(long elapsedTicks) {
        return (int) Math.clamp(1L + Math.max(0L, elapsedTicks) / LIGHT_INTERVAL_TICKS, 1L, RED_LIGHT_COUNT);
    }
}
