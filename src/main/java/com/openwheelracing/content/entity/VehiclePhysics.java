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

    public static final double TYRE_AMBIENT_TEMPERATURE_C = 28.0;
    public static final double TYRE_HEAT_CAPACITY_J_PER_C = 43_500.0;
    public static final double TYRE_ROLLING_RESISTANCE_HEAT_FRACTION = 0.80;
    public static final double TYRE_SLIP_HEAT_FRACTION = 1.43;
    public static final double TYRE_STATIONARY_COOLING_PER_SECOND = 0.00209;
    public static final double TYRE_WIND_COOLING_PER_MPS_SECOND = 0.00001848;
    public static final double TYRE_HOT_COOLING_PER_SECOND = 0.00250;
    public static final double TYRE_HOT_COOLING_SCALE_C = 150.0;
    public static final double TYRE_G_FORCE_HEAT_FACTOR = 0.61;

    private static final double REVERSE_TOP_SPEED_KMH = 60.0;
    private static final double[] GEAR_TOP_SPEEDS_KMH = {0.0, 100.0, 135.0, 170.0, 205.0, 245.0, 280.0, 320.0, 360.0};

    private static final double CAR_MASS_KG = 769.0;
    private static final double GRAVITY = 9.81;
    private static final double ASPHALT_MU_LONGITUDINAL = 2.25;
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

    public static double tyreRollingHeatPowerWatts(double normalLoad, double speedMetersPerSecond, double rollingResistance) {
        return Math.max(0.0, rollingResistance * normalLoad * Math.max(0.0, speedMetersPerSecond) * TYRE_ROLLING_RESISTANCE_HEAT_FRACTION);
    }

    public static double tyreLateralNearSaturation(double lateralForce, double normalLoad) {
        double lateralUtilization = Math.abs(lateralForce) / Math.max(1.0, normalLoad * ASPHALT_MU_LONGITUDINAL);
        return Math.max(0.0, lateralUtilization - 0.88);
    }

    public static double tyreSlipHeatPowerWatts(double longitudinalForce, double lateralForce, double normalLoad, double speedMetersPerSecond, double demand, double slipAngleRadians) {
        double safeNormalLoad = Math.max(1.0, normalLoad);
        double speed = Math.max(0.0, speedMetersPerSecond);
        double longitudinalUtilization = Math.min(1.0, Math.abs(longitudinalForce) / (safeNormalLoad * ASPHALT_MU_LONGITUDINAL));
        double lateralUtilization = Math.min(1.0, Math.abs(lateralForce) / (safeNormalLoad * ASPHALT_MU_LONGITUDINAL));
        double slipAngle = Math.abs(slipAngleRadians);
        double longitudinalWorkSpeed = longitudinalUtilization * speed * 0.055;
        double lateralScrubSpeed = Math.sin(slipAngle) * speed * 0.210;
        double lateralGripWorkSpeed = lateralUtilization * speed * 0.0060;
        double slipFrictionPower = safeNormalLoad * speed * slipAngle * slipAngle * (0.72 + lateralUtilization * 0.48);
        double saturation = Math.max(0.0, demand - 1.0);
        double limitForce = safeNormalLoad * ASPHALT_MU_LONGITUDINAL;
        double kineticSlipVelocity = saturation * saturation * speed * 2.40;
        double kineticSlipPower = limitForce * kineticSlipVelocity;
        return (Math.abs(longitudinalForce) * longitudinalWorkSpeed + Math.abs(lateralForce) * (lateralScrubSpeed + lateralGripWorkSpeed) + slipFrictionPower + kineticSlipPower) * TYRE_SLIP_HEAT_FRACTION;
    }

    public static double tyreLoadHeatMultiplier(double longitudinalForce, double lateralForce, double normalLoad) {
        double safeNormalLoad = Math.max(1.0, normalLoad);
        double longitudinalG = Math.abs(longitudinalForce) / safeNormalLoad;
        double lateralG = Math.abs(lateralForce) / safeNormalLoad;
        double combinedG = Math.sqrt(longitudinalG * longitudinalG * 0.8 + lateralG * lateralG);
        return 1.0 + Math.max(0.0, combinedG - 0.08) * TYRE_G_FORCE_HEAT_FACTOR;
    }

    public static double tyreGForceHeatMultiplier(double longitudinalG, double lateralG) {
        double combinedG = Math.sqrt(longitudinalG * longitudinalG + lateralG * lateralG);
        return 1.0 + Math.max(0.0, combinedG - 0.08) * TYRE_G_FORCE_HEAT_FACTOR;
    }

    public static double tyreHeatDeltaC(double heatPowerWatts, double compoundHeatGain, double longitudinalG, double lateralG, double dtSeconds) {
        double heatEnergy = Math.max(0.0, heatPowerWatts) * tyreGForceHeatMultiplier(longitudinalG, lateralG) * Math.max(0.0, compoundHeatGain) * dtSeconds;
        return heatEnergy / TYRE_HEAT_CAPACITY_J_PER_C;
    }

    public static double tyreHeatDeltaC(double heatPowerWatts, double compoundHeatGain, double longitudinalForce, double lateralForce, double normalLoad, double dtSeconds) {
        double heatEnergy = Math.max(0.0, heatPowerWatts) * tyreLoadHeatMultiplier(longitudinalForce, lateralForce, normalLoad) * Math.max(0.0, compoundHeatGain) * dtSeconds;
        return heatEnergy / TYRE_HEAT_CAPACITY_J_PER_C;
    }

    public static double tyreCoolingDeltaC(double temperatureC, double speedMetersPerSecond, double dtSeconds) {
        if (temperatureC <= TYRE_AMBIENT_TEMPERATURE_C) {
            return 0.0;
        }
        double coolingRate = TYRE_STATIONARY_COOLING_PER_SECOND + TYRE_WIND_COOLING_PER_MPS_SECOND * Math.max(0.0, speedMetersPerSecond) + tyreHotCoolingRate(temperatureC);
        return (temperatureC - TYRE_AMBIENT_TEMPERATURE_C) * (1.0 - Math.exp(-coolingRate * dtSeconds));
    }

    public static double tyreHotCoolingRate(double temperatureC) {
        double normalizedHeat = Math.max(0.0, temperatureC - TYRE_AMBIENT_TEMPERATURE_C) / TYRE_HOT_COOLING_SCALE_C;
        return TYRE_HOT_COOLING_PER_SECOND * Math.pow(normalizedHeat, 8.0);
    }

    public static double nextTyreTemperatureC(double temperatureC, double heatPowerWatts, double compoundHeatGain, double longitudinalG, double lateralG, double speedMetersPerSecond, double dtSeconds) {
        double next = temperatureC + tyreHeatDeltaC(heatPowerWatts, compoundHeatGain, longitudinalG, lateralG, dtSeconds) - tyreCoolingDeltaC(temperatureC, speedMetersPerSecond, dtSeconds);
        return Math.max(TYRE_AMBIENT_TEMPERATURE_C, next);
    }

    public static double simulateTyreEquilibriumC(double initialTemperatureC, double heatPowerWatts, double compoundHeatGain, double longitudinalG, double lateralG, double speedMetersPerSecond, int ticks) {
        double temperature = initialTemperatureC;
        for (int tick = 0; tick < ticks; tick++) {
            temperature = nextTyreTemperatureC(temperature, heatPowerWatts, compoundHeatGain, longitudinalG, lateralG, speedMetersPerSecond, 0.05);
        }
        return temperature;
    }
}
