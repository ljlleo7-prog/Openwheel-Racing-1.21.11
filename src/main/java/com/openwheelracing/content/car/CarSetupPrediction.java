package com.openwheelracing.content.car;

/** Pure setup preview model. Values are normalized from blue/low (0) to red/high (1). */
public final class CarSetupPrediction {
    private static final int BLUE = 0xFF4AA3FF;
    private static final int GREEN = 0xFF55D66B;
    private static final int RED = 0xFFFF5C5C;

    private CarSetupPrediction() { }

    public static Tradeoff tradeoff(int slot, double position) {
        double p = clamp(position);
        return switch (slot) {
            case 0 -> new Tradeoff("accel - " + level(p), p, "fuel use - " + level(p), p);
            case 1 -> new Tradeoff("front grip - " + level(p), p, "top speed - " + level(1.0 - p), 1.0 - p);
            case 2 -> new Tradeoff("rear grip - " + level(p), p, "top speed - " + level(1.0 - p), 1.0 - p);
            case 3 -> new Tradeoff("top speed - " + level(p), p, "accel - " + level(1.0 - p), 1.0 - p);
            case 4 -> new Tradeoff(balanceTerm(p), p, "rotation - " + level(1.0 - p), 1.0 - p);
            case 5 -> new Tradeoff("brake stability - " + level(p), p, "rotation - " + level(1.0 - p), 1.0 - p);
            default -> new Tradeoff("", 0.5, "", 0.5);
        };
    }

    public static Summary combined(double power, double gearing, double frontWing, double rearWing,
                                   double antiRoll, double brakeBias) {
        double aeroGrip = clamp(frontWing) * 0.45 + clamp(rearWing) * 0.55;
        double aeroDrag = clamp(frontWing) * 0.30 + clamp(rearWing) * 0.70;
        double acceleration = clamp(0.35 + 0.65 * (1.0 - clamp(gearing)));
        double topSpeed = clamp(0.60 * clamp(gearing) + 0.40 * (1.0 - aeroDrag));
        double balanceInfluence = 0.50 * (0.60 * (clamp(rearWing) - 0.5) - (clamp(frontWing) - 0.5))
            + 0.35 * ((clamp(antiRoll) - 0.5) * 2.0)
            + 0.15 * ((clamp(brakeBias) - 0.5) * 2.0);
        double balance = clamp(0.5 + balanceInfluence * 0.5);
        return new Summary(acceleration, topSpeed, aeroGrip, aeroDrag, balance);
    }

    public static String level(double position) {
        return position < 0.34 ? "low" : position > 0.66 ? "high" : "medium";
    }

    public static String balanceTerm(double position) {
        return position < 0.40 ? "balance - oversteer" : position > 0.60 ? "balance - understeer" : "balance - neutral";
    }

    public static int color(double position) {
        double t = clamp(position);
        int from = t <= 0.5 ? BLUE : GREEN;
        int to = t <= 0.5 ? GREEN : RED;
        double segment = t <= 0.5 ? t * 2.0 : (t - 0.5) * 2.0;
        int r = interpolate((from >> 16) & 0xFF, (to >> 16) & 0xFF, segment);
        int g = interpolate((from >> 8) & 0xFF, (to >> 8) & 0xFF, segment);
        int b = interpolate(from & 0xFF, to & 0xFF, segment);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static double clamp(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    private static int interpolate(int from, int to, double t) { return (int) Math.round(from + (to - from) * t); }

    public record Tradeoff(String primary, double primaryPosition, String secondary, double secondaryPosition) { }
    public record Summary(double acceleration, double topSpeed, double grip, double drag, double balance) { }
}
