package com.openwheelracing.client.input;

public final class AutoShiftPolicy {
    static final int COOLDOWN_TICKS = 8;
    private static final double UPSHIFT_RATIO = 0.92;
    private static final double DOWNSHIFT_RATIO = 0.58;
    private static final double MAX_LOWER_GEAR_RATIO = 0.88;

    private AutoShiftPolicy() {
    }

    public static Decision decide(int gear, int maxGear, int rpm, int redlineRpm, int projectedLowerRpm,
            float throttle, float brake, int cooldownTicks) {
        if (cooldownTicks > 0) {
            return new Decision(0, cooldownTicks - 1);
        }
        if (gear > 0 && gear < maxGear && rpm >= redlineRpm * UPSHIFT_RATIO) {
            return new Decision(1, COOLDOWN_TICKS);
        }
        if (gear > 1 && rpm <= redlineRpm * DOWNSHIFT_RATIO
                && projectedLowerRpm <= redlineRpm * MAX_LOWER_GEAR_RATIO) {
            return new Decision(-1, COOLDOWN_TICKS);
        }
        return new Decision(0, 0);
    }

    public record Decision(int direction, int cooldownTicks) {
    }
}
