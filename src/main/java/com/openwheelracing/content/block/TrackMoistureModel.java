package com.openwheelracing.content.block;

public final class TrackMoistureModel {
    // Random ticks average roughly 4096 game ticks between attempts per block.
    // Calibrate rain transitions to multi-minute stages instead of near-instant wetting.
    private static final double[] RAIN_CHANCE = {0.0, 0.016, 0.012, 0.0};
    private static final double[] DRY_CHANCE = {0.0, 0.008, 0.006, 0.004};

    private TrackMoistureModel() {
    }

    public static double transitionChance(TrackMoisture current, boolean raining, boolean thundering,
                                           boolean day, boolean canSeeSky, int relevantNeighbors) {
        double chance = raining ? RAIN_CHANCE[current.level()] : DRY_CHANCE[current.level()];
        if (raining && thundering) chance *= 1.5;
        if (!raining) chance *= canSeeSky ? (day ? 1.3 : 0.65) : 0.25;
        return Math.min(1.0, chance * (0.35 + 0.20 * Math.max(0, Math.min(4, relevantNeighbors))));
    }

    public static TrackMoisture transitioned(TrackMoisture current, boolean raining) {
        return raining ? current.wetter() : current.drier();
    }
}
