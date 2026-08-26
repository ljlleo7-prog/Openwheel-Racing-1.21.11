package com.openwheelracing.content.car;

/** Pure setup-to-physics mappings, kept independent of Minecraft serialization for unit testing. */
public final class CarSetupPhysics {
    private CarSetupPhysics() { }

    public static double downforceCoefficient(int frontWing, int rearWing) {
        double front = normalizedFrontWing(frontWing);
        double rear = normalizedRearWing(rearWing);
        return 1.0 + (front * 0.45 + rear * 0.55 - 0.5) * 0.55;
    }

    public static double dragCoefficient(int frontWing, int rearWing) {
        double front = normalizedFrontWing(frontWing);
        double rear = normalizedRearWing(rearWing);
        return 1.0 + (front * 0.30 + rear * 0.70 - 0.5) * 0.32;
    }

    public static double frontAeroBalanceAdjustment(int frontWing, int rearWing) {
        double front = normalizedFrontWing(frontWing) - 0.5;
        double rear = normalizedRearWing(rearWing) - 0.5;
        return (front * 0.70 - rear * 0.30) * 0.18;
    }

    public static double frontRollStiffnessShare(int antiRoll) {
        return 0.50 + (antiRoll - 5) * 0.025;
    }

    public static double brakeFrontBias(int brakeBiasPercent) {
        return brakeBiasPercent / 100.0;
    }

    public static double normalizedFrontWing(int angle) {
        return clamp01((angle - PrototypeCarSetup.FRONT_WING_MIN)
            / (double) (PrototypeCarSetup.FRONT_WING_MAX - PrototypeCarSetup.FRONT_WING_MIN));
    }

    public static double normalizedRearWing(int angle) {
        return clamp01((angle - PrototypeCarSetup.REAR_WING_MIN)
            / (double) (PrototypeCarSetup.REAR_WING_MAX - PrototypeCarSetup.REAR_WING_MIN));
    }

    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
