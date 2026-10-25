package com.openwheelracing.content.race.weekend;

/** Sporting rules shared by the persisted GP lifecycle and light controller. */
public final class FormationLapProcedure {
    public static final long LIGHT_INTERVAL_TICKS = 10L * 20L;
    public static final double HANDOVER_DISTANCE_METERS = 100.0;
    public static final int GREEN_LIGHT_PHASE = com.openwheelracing.content.race.RaceStartLights.FORMATION_GREEN;

    private FormationLapProcedure() {
    }

    public static int redLightCount(long remainingTicks) {
        return remainingTicks <= 0L ? 0 : (int) Math.min(5L, 1L + (remainingTicks - 1L) / LIGHT_INTERVAL_TICKS);
    }

    public static boolean leaderReturning(double absoluteProgressMeters, double routeLengthMeters) {
        // At most the last tenth on short circuits; never trigger at grid departure.
        double approach = Math.min(HANDOVER_DISTANCE_METERS, routeLengthMeters * 0.1);
        return routeLengthMeters > 0.0 && absoluteProgressMeters >= routeLengthMeters - approach;
    }
}
