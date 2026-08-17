package com.openwheelracing.content.ai;

public final class BasicAiTrainingMath {
    public static final double MIN_TARGET_SCALE = 0.70;
    public static final double MAX_TARGET_SCALE = 1.05;
    public static final double MIN_BRAKING_SCALE = 0.75;
    public static final double MAX_BRAKING_SCALE = 1.50;
    public static final double MIN_STEERING_SCALE = 0.75;
    public static final double MAX_STEERING_SCALE = 1.25;

    private BasicAiTrainingMath() {
    }

    public static Update update(double targetScale, double brakingScale, double steeringScale,
                                int validLaps, int rejectedLaps, int recoveryCount,
                                double baselineMillis, double lapMillis, boolean valid, boolean raceMode) {
        if (!valid || !raceMode || !(lapMillis > 0.0) || !(baselineMillis > 0.0)) {
            return new Update(clamp(targetScale, MIN_TARGET_SCALE, MAX_TARGET_SCALE),
                clamp(brakingScale, MIN_BRAKING_SCALE, MAX_BRAKING_SCALE),
                clamp(steeringScale, MIN_STEERING_SCALE, MAX_STEERING_SCALE),
                validLaps, rejectedLaps + 1, recoveryCount, false);
        }
        double error = clamp((baselineMillis - lapMillis) / baselineMillis, -0.05, 0.05);
        double nextTarget = clamp(targetScale + error * 0.004, MIN_TARGET_SCALE, MAX_TARGET_SCALE);
        double nextBraking = clamp(brakingScale - error * 0.006, MIN_BRAKING_SCALE, MAX_BRAKING_SCALE);
        return new Update(nextTarget, nextBraking,
            clamp(steeringScale, MIN_STEERING_SCALE, MAX_STEERING_SCALE), validLaps + 1, rejectedLaps, recoveryCount, true);
    }

    public static boolean stalled(double speedMetersPerSecond, long lowSpeedTicks) {
        return speedMetersPerSecond < 1.0 && lowSpeedTicks >= 60L;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Update(double targetScale, double brakingScale, double steeringScale, int validLaps,
                         int rejectedLaps, int recoveryCount, boolean accepted) {
    }
}
