package com.openwheelracing.content.ai;

public final class BasicAiGripModel {
    private static final double GRAVITY = 9.81;
    private static final double AIR_DENSITY = 1.225;
    private static final double MASS_KG = 798.0;
    private static final double BASE_LATERAL_MU = 2.15;
    private static final double BASE_LONGITUDINAL_MU = 2.25;
    private static final double BASE_DOWNFORCE_AREA = 7.0;
    private static final double LOAD_SENSITIVITY = 0.035;
    private static final double BASE_POWER_WATTS = 780_000.0;
    private static final double BASE_DRAG_AREA = 1.05;
    private static final double MAX_BRAKE_ACCELERATION = 40_000.0 / MASS_KG;
    private static final double SPEED_BIN = 5.0;
    private static final int BIN_COUNT = 21;

    private BasicAiGripModel() {
    }

    public static State build(Input input) {
        double temperature = temperatureFactor(input.tyreTemperatureC(), input.workingTemperatureMinC(), input.workingTemperatureMaxC());
        double wear = Math.max(0.45, 1.0 - input.tyreWearPercent() / 180.0);
        double wheelDamage = Math.max(0.35, 1.0 - input.worstWheelDamagePercent() / 155.0);
        double condition = clamp(temperature * wear * wheelDamage * input.setupGripMultiplier() * input.surfaceGrip(), 0.10, 1.25);
        double aeroDamage = Math.sqrt(clamp(input.frontAeroFactor() * input.rearAeroFactor(), 0.15, 1.0));
        double powerFactor = clamp(input.powerFactor(), 0.10, 1.25);
        double dragFactor = Math.max(0.5, input.dragFactor());
        double[] lateral = new double[BIN_COUNT];
        double[] longitudinal = new double[BIN_COUNT];
        double[] drive = new double[BIN_COUNT];
        double[] brake = new double[BIN_COUNT];
        for (int index = 0; index < BIN_COUNT; index++) {
            double speed = index * SPEED_BIN;
            double downforce = 0.5 * AIR_DENSITY * BASE_DOWNFORCE_AREA * input.aeroMultiplier() * aeroDamage * speed * speed;
            double loadRatio = (MASS_KG * GRAVITY + downforce) / (MASS_KG * GRAVITY);
            double loadSensitivity = Math.max(0.78, 1.0 - LOAD_SENSITIVITY * (loadRatio - 1.0));
            lateral[index] = BASE_LATERAL_MU * condition * loadSensitivity * GRAVITY * loadRatio;
            longitudinal[index] = BASE_LONGITUDINAL_MU * condition * loadSensitivity * GRAVITY * loadRatio;
            double drag = 0.5 * AIR_DENSITY * BASE_DRAG_AREA * input.dragMultiplier() * dragFactor * speed * speed / MASS_KG;
            drive[index] = Math.max(0.0, Math.min(longitudinal[index], BASE_POWER_WATTS * input.powerMultiplier() * powerFactor / Math.max(5.0, speed) / MASS_KG - drag));
            brake[index] = Math.min(longitudinal[index], MAX_BRAKE_ACCELERATION) + drag;
        }
        return new State(lateral, longitudinal, drive, brake);
    }

    public static double temperatureFactor(double temperatureC, double minimumC, double maximumC) {
        double cold = clamp((minimumC - temperatureC) / 24.0, 0.0, 1.0);
        double hot = clamp((temperatureC - maximumC) / 22.0, 0.0, 1.0);
        return clamp((1.0 - 0.34 * cold * cold) * (1.0 - 0.26 * hot * hot), 0.62, 1.03);
    }

    private static double interpolate(double[] bins, double speed) {
        double scaled = clamp(speed / SPEED_BIN, 0.0, bins.length - 1.0);
        int lower = (int) Math.floor(scaled);
        int upper = Math.min(bins.length - 1, lower + 1);
        double t = scaled - lower;
        return bins[lower] + (bins[upper] - bins[lower]) * t;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Input(double tyreTemperatureC, double workingTemperatureMinC, double workingTemperatureMaxC,
                        double tyreWearPercent, double worstWheelDamagePercent, double setupGripMultiplier,
                        double surfaceGrip, double aeroMultiplier, double frontAeroFactor, double rearAeroFactor,
                        double powerMultiplier, double powerFactor, double dragMultiplier, double dragFactor) {
    }

    public record State(double[] lateralBins, double[] longitudinalBins, double[] driveBins, double[] brakeBins) {
        public double lateralAcceleration(double speed) { return interpolate(lateralBins, speed); }
        public double longitudinalAcceleration(double speed) { return interpolate(longitudinalBins, speed); }
        public double driveAcceleration(double speed) { return interpolate(driveBins, speed); }
        public double brakeAcceleration(double speed) { return interpolate(brakeBins, speed); }
    }
}
