package com.openwheelracing.content.race;

/** Small, deliberately conservative estimates used by the Race Director BoP console. */
public final class BoPMath {
    private BoPMath() {}

    public static double multiplierFromPercent(double percent) {
        return 1.0 + percent / 100.0;
    }

    public static int estimateLapMillis(int averageLapMillis, double weightPercent, double powerPercent) {
        if (averageLapMillis <= 0) return 0;
        // Power has a little more effect than mass on a representative lap, while the
        // estimate remains bounded and intentionally less precise than simulation.
        double paceFactor = 1.0 + weightPercent * 0.22 / 100.0 - powerPercent * 0.30 / 100.0;
        return Math.max(1, (int) Math.round(averageLapMillis * paceFactor));
    }
}
