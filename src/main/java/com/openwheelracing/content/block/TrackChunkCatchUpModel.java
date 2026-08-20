package com.openwheelracing.content.block;

public final class TrackChunkCatchUpModel {
    private static final double RANDOM_TICKS_PER_BLOCK_TICK = 3.0 / 4096.0;
    private static final double[] RAIN_CHANCE = {1.0, 0.40, 0.22, 0.0};
    private static final double[] DRY_CHANCE = {0.0, 0.12, 0.22, 0.45};

    private TrackChunkCatchUpModel() {
    }

    public static double transitionProbability(int currentLevel, boolean wetting, boolean thundering,
                                               boolean day, double eligibleTicks) {
        int level = Math.max(0, Math.min(3, currentLevel));
        double chancePerRandomTick = wetting ? RAIN_CHANCE[level] : DRY_CHANCE[level];
        if (wetting && thundering) chancePerRandomTick *= 1.5;
        if (!wetting) chancePerRandomTick *= day ? 1.3 : 0.65;
        // Two helpful neighbours is the neutral expectation for a coherent randomized front.
        chancePerRandomTick = Math.min(1.0, chancePerRandomTick * 0.75);
        return 1.0 - Math.exp(-Math.max(0.0, eligibleTicks) * RANDOM_TICKS_PER_BLOCK_TICK * chancePerRandomTick);
    }

    public static double eligibleTicks(double startProgress, double endProgress, long elapsedTicks,
                                       int transitionStage, double threshold, boolean wetting) {
        double boundary = transitionStage + Math.max(0.0, Math.min(1.0, threshold));
        double start = Math.max(0.0, Math.min(3.0, startProgress));
        double end = Math.max(0.0, Math.min(3.0, endProgress));
        double elapsed = Math.max(0L, elapsedTicks);
        if (end > start) {
            if (end < boundary) return 0.0;
            if (start >= boundary) return elapsed;
            return elapsed * (end - boundary) / (end - start);
        }
        if (end < start) {
            if (end > boundary) return 0.0;
            if (start <= boundary) return elapsed;
            return elapsed * (boundary - end) / (start - end);
        }
        return wetting ? end >= boundary ? elapsed : 0.0 : end <= boundary ? elapsed : 0.0;
    }
}
