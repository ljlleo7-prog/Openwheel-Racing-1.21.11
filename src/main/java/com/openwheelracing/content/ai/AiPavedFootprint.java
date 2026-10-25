package com.openwheelracing.content.ai;

/** Clearance follows the sampled road height instead of the car's current (possibly lower) floor. */
public final class AiPavedFootprint {
    private AiPavedFootprint() { }

    public static double clearanceFloor(double[] supportHeights) {
        if (supportHeights.length == 0) return Double.NaN;
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (double height : supportHeights) {
            if (!Double.isFinite(height)) return Double.NaN;
            low = Math.min(low, height);
            high = Math.max(high, height);
        }
        return high - low <= 1.0 ? high : Double.NaN;
    }
}
