package com.openwheelracing.content.entity;

import com.openwheelracing.content.car.PrototypeCarSetup;

public final class VehiclePhysics {
    public static final double KMH_PER_BLOCK_PER_TICK = 72.0;
    public static final double SPEED_TO_BLOCKS_PER_TICK = 1.0 / KMH_PER_BLOCK_PER_TICK;
    public static final double PIT_SPEED_LIMIT_KMH = 79.0;
    public static final double PIT_SPEED_LIMIT_BLOCKS_PER_TICK = PIT_SPEED_LIMIT_KMH * SPEED_TO_BLOCKS_PER_TICK;
    public static final double ASPHALT_GRIP = 1.00;
    public static final double ASPHALT_DRAG = 0.997;
    public static final double PIT_LANE_GRIP = ASPHALT_GRIP;
    public static final double PIT_LANE_DRAG = ASPHALT_DRAG;

    private static final double REVERSE_TOP_SPEED_KMH = 60.0;
    private static final double[] GEAR_TOP_SPEEDS_KMH = {0.0, 100.0, 135.0, 170.0, 205.0, 245.0, 280.0, 320.0, 360.0};

    private static final double CAR_MASS_KG = 769.0;
    private static final double GRAVITY = 9.81;
    private static final double ASPHALT_MU_LONGITUDINAL = 2.30;
    private static final double MAX_BRAKE_FORCE = 40_000.0;

    private VehiclePhysics() {
    }

    public static double speedKmhToBlocksPerTick(double speedKmh) {
        return speedKmh * SPEED_TO_BLOCKS_PER_TICK;
    }

    public static double gearTopSpeedKmh(int gear) {
        if (gear < 0) {
            return REVERSE_TOP_SPEED_KMH;
        }
        int clampedGear = Math.min(gear, GEAR_TOP_SPEEDS_KMH.length - 1);
        return GEAR_TOP_SPEEDS_KMH[clampedGear];
    }

    public static double gearTopSpeedKmh(int gear, PrototypeCarSetup setup) {
        return gearTopSpeedKmh(gear) * setup.topSpeedCoefficient();
    }

    public static double gearTopSpeedBlocksPerTick(int gear, PrototypeCarSetup setup) {
        return speedKmhToBlocksPerTick(gearTopSpeedKmh(gear, setup));
    }

    public static double brakingDistanceMeters(double initialSpeedMetersPerSecond, double surfaceGrip) {
        double normalLoad = CAR_MASS_KG * GRAVITY;
        double availableBrakeForce = ASPHALT_MU_LONGITUDINAL * surfaceGrip * normalLoad;
        double brakeAcceleration = Math.min(MAX_BRAKE_FORCE, availableBrakeForce) / CAR_MASS_KG;
        return initialSpeedMetersPerSecond * initialSpeedMetersPerSecond / (2.0 * brakeAcceleration);
    }
}
