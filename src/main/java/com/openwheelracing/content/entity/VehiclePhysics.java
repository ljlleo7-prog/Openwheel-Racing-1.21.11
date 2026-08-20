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

    public static final double TYRE_AMBIENT_TEMPERATURE_C = 33.0;
    public static final double TYRE_HEAT_CAPACITY_J_PER_C = 43_500.0;
    public static final double TYRE_SURFACE_HEAT_CAPACITY_J_PER_C = 20_000.0;
    public static final double TYRE_CARCASS_HEAT_CAPACITY_J_PER_C = TYRE_HEAT_CAPACITY_J_PER_C - TYRE_SURFACE_HEAT_CAPACITY_J_PER_C;
    public static final double TYRE_ROLLING_RESISTANCE_HEAT_FRACTION = 0.80;
    public static final double TYRE_SLIP_HEAT_FRACTION = 1.45;
    public static final double TYRE_STATIONARY_COOLING_PER_SECOND = 0.00240;
    public static final double TYRE_WIND_COOLING_PER_MPS_SECOND = 0.0000210;
    public static final double TYRE_HOT_COOLING_PER_SECOND = 0.000030;
    public static final double TYRE_HOT_COOLING_START_C = 110.0;
    public static final double TYRE_G_FORCE_HEAT_FACTOR = 0.61;
    public static final double TYRE_SURFACE_FRICTION_HEAT_FRACTION = 0.80;
    public static final double TYRE_CARCASS_FRICTION_HEAT_FRACTION = 1.0 - TYRE_SURFACE_FRICTION_HEAT_FRACTION;
    public static final double TYRE_BRAKE_TO_CARCASS_HEAT_FRACTION = 0.90;
    public static final double TYRE_CARCASS_TRANSFER_WATTS_PER_C = 600.0;
    public static final double TYRE_CARCASS_COOLING_FRACTION = 0.45;
    public static final double TYRE_EXPOSURE_ATTACK_PER_SECOND = 1.8;
    public static final double TYRE_EXPOSURE_RELEASE_PER_SECOND = 2.8;
    public static final double TYRE_EXPOSURE_TRANSFER_FLOOR = 0.05;

    private static final double REVERSE_TOP_SPEED_KMH = 60.0;
    private static final double[] GEAR_TOP_SPEEDS_KMH = {0.0, 100.0, 135.0, 170.0, 205.0, 245.0, 280.0, 320.0, 360.0};

    private static final double CAR_MASS_KG = 769.0;
    private static final double GRAVITY = 9.81;
    private static final double ASPHALT_MU_LONGITUDINAL = 2.25;
    private static final double MAX_BRAKE_FORCE = 40_000.0;

    private VehiclePhysics() {
    }

    public static boolean isNewerSequence(int candidate, int current) {
        return candidate != current && candidate - current > 0;
    }

    public static boolean exceedsSequenceGap(int candidate, int current, int allowedGap) {
        int gap = candidate - current;
        return gap > Math.max(0, allowedGap);
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

    public static double tyreBrakeHeatPowerPerTyre(double brakeInput, double totalBrakeHeatPower, double axleBias) {
        return Math.max(0.0, brakeInput) * Math.max(0.0, totalBrakeHeatPower) * clamp(axleBias, 0.0, 1.0) * 0.5;
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
        double excessHeat = Math.max(0.0, temperatureC - TYRE_HOT_COOLING_START_C);
        return TYRE_HOT_COOLING_PER_SECOND * excessHeat * excessHeat;
    }

    public static double nextTyreTemperatureC(double temperatureC, double heatPowerWatts, double compoundHeatGain, double longitudinalG, double lateralG, double speedMetersPerSecond, double dtSeconds) {
        double next = temperatureC + tyreHeatDeltaC(heatPowerWatts, compoundHeatGain, longitudinalG, lateralG, dtSeconds) - tyreCoolingDeltaC(temperatureC, speedMetersPerSecond, dtSeconds);
        return Math.max(TYRE_AMBIENT_TEMPERATURE_C, next);
    }

    public static double tyreOptimalTemperatureC(int compound) {
        return switch (compound) {
            case 0 -> 115.0;
            case 1 -> 112.5;
            case 2 -> 110.0;
            case 3 -> 107.5;
            default -> 105.0;
        };
    }

    public static double tyreTemperatureGripMultiplier(int compound, double temperatureC) {
        double offset = temperatureC - tyreOptimalTemperatureC(compound);
        if (offset <= -55.0) return interpolate(offset, -65.0, -55.0, 0.52, 0.55);
        if (offset <= -45.0) return interpolate(offset, -55.0, -45.0, 0.55, 0.62);
        if (offset <= -35.0) return interpolate(offset, -45.0, -35.0, 0.62, 0.72);
        if (offset <= -25.0) return interpolate(offset, -35.0, -25.0, 0.72, 0.84);
        if (offset <= -15.0) return interpolate(offset, -25.0, -15.0, 0.84, 0.94);
        if (offset <= -10.0) return interpolate(offset, -15.0, -10.0, 0.94, 0.98);
        if (offset <= -5.0) return interpolate(offset, -10.0, -5.0, 0.98, 1.00);
        if (offset <= 5.0) return 1.00;
        if (offset <= 10.0) return interpolate(offset, 5.0, 10.0, 1.00, 0.985);
        if (offset <= 15.0) return interpolate(offset, 10.0, 15.0, 0.985, 0.965);
        if (offset <= 20.0) return interpolate(offset, 15.0, 20.0, 0.965, 0.94);
        if (offset <= 25.0) return interpolate(offset, 20.0, 25.0, 0.94, 0.86);
        if (offset <= 30.0) return interpolate(offset, 25.0, 30.0, 0.86, 0.80);
        if (offset <= 35.0) return interpolate(offset, 30.0, 35.0, 0.80, 0.70);
        if (offset <= 40.0) return interpolate(offset, 35.0, 40.0, 0.70, 0.65);
        return interpolate(offset, 40.0, 45.0, 0.65, 0.60);
    }

    public static double tyreDirectionChangeSeverity(double previousLongitudinal, double previousLateral,
            double currentLongitudinal, double currentLateral) {
        double cross = previousLongitudinal * currentLateral - previousLateral * currentLongitudinal;
        double dot = previousLongitudinal * currentLongitudinal + previousLateral * currentLateral;
        double rotation = cross * cross * 4.0;
        double loadedReversal = Math.max(0.0, -dot) * 2.0;
        return clamp(rotation + loadedReversal, 0.0, 1.0);
    }

    public static TyreThermalState nextTyreThermalState(double surfaceTemperatureC, double carcassTemperatureC, double slipExposure,
            double frictionHeatPowerWatts, double brakeHeatPowerWatts, double compoundHeatGain, double speedMetersPerSecond,
            double surfaceCoolingMultiplier, double stationaryCoolingMultiplier, double windCoolingMultiplier,
            double exposureDemand, double slipAngleRadians, double dtSeconds, boolean groundContact) {
        return nextTyreThermalState(surfaceTemperatureC, carcassTemperatureC, slipExposure, frictionHeatPowerWatts,
            brakeHeatPowerWatts, compoundHeatGain, speedMetersPerSecond, surfaceCoolingMultiplier, 1.0,
            stationaryCoolingMultiplier, windCoolingMultiplier, exposureDemand, slipAngleRadians, dtSeconds, groundContact);
    }

    public static TyreThermalState nextTyreThermalState(double surfaceTemperatureC, double carcassTemperatureC, double slipExposure,
            double frictionHeatPowerWatts, double brakeHeatPowerWatts, double compoundHeatGain, double speedMetersPerSecond,
            double surfaceCoolingMultiplier, double carcassCoolingMultiplier, double stationaryCoolingMultiplier, double windCoolingMultiplier,
            double exposureDemand, double slipAngleRadians, double dtSeconds, boolean groundContact) {
        double dt = Math.max(0.0, dtSeconds);
        double abuseTarget = clamp(Math.max(0.0, exposureDemand - 0.82) * 3.0 + Math.abs(slipAngleRadians) / 0.16, 0.0, 1.0);
        double exposureRate = abuseTarget > slipExposure ? TYRE_EXPOSURE_ATTACK_PER_SECOND : TYRE_EXPOSURE_RELEASE_PER_SECOND;
        double nextExposure = moveToward(slipExposure, abuseTarget, exposureRate * dt);
        if (!groundContact) {
            nextExposure = moveToward(nextExposure, 0.0, TYRE_EXPOSURE_RELEASE_PER_SECOND * dt);
        }

        double frictionPower = groundContact ? Math.max(0.0, frictionHeatPowerWatts) * Math.max(0.0, compoundHeatGain) : 0.0;
        double brakePower = groundContact ? Math.max(0.0, brakeHeatPowerWatts) : 0.0;
        double surfacePower = frictionPower * TYRE_SURFACE_FRICTION_HEAT_FRACTION + brakePower * (1.0 - TYRE_BRAKE_TO_CARCASS_HEAT_FRACTION);
        double carcassPower = frictionPower * TYRE_CARCASS_FRICTION_HEAT_FRACTION + brakePower * TYRE_BRAKE_TO_CARCASS_HEAT_FRACTION;
        double transientTransfer = TYRE_EXPOSURE_TRANSFER_FLOOR + (1.0 - TYRE_EXPOSURE_TRANSFER_FLOOR) * nextExposure;
        double conductionPower = (surfaceTemperatureC - carcassTemperatureC) * TYRE_CARCASS_TRANSFER_WATTS_PER_C * transientTransfer;
        surfacePower -= conductionPower;
        carcassPower += conductionPower;

        double surfaceCooling = tyreCoolingDeltaC(surfaceTemperatureC, speedMetersPerSecond, dt)
            * Math.max(0.0, surfaceCoolingMultiplier)
            * Math.max(0.0, stationaryCoolingMultiplier)
            * Math.max(0.0, windCoolingMultiplier);
        double carcassCooling = tyreCoolingDeltaC(carcassTemperatureC, speedMetersPerSecond, dt)
            * TYRE_CARCASS_COOLING_FRACTION * Math.max(0.0, carcassCoolingMultiplier);
        double surface = clamp(surfaceTemperatureC + surfacePower * dt / TYRE_SURFACE_HEAT_CAPACITY_J_PER_C - surfaceCooling, TYRE_AMBIENT_TEMPERATURE_C, 145.0);
        double carcass = clamp(carcassTemperatureC + carcassPower * dt / TYRE_CARCASS_HEAT_CAPACITY_J_PER_C - carcassCooling, TYRE_AMBIENT_TEMPERATURE_C, 145.0);
        return new TyreThermalState(surface, carcass, nextExposure);
    }

    private static double moveToward(double value, double target, double amount) {
        if (value < target) return Math.min(target, value + amount);
        return Math.max(target, value - amount);
    }

    private static double interpolate(double value, double from, double to, double fromValue, double toValue) {
        double t = clamp((value - from) / Math.max(1.0E-9, to - from), 0.0, 1.0);
        return fromValue + (toValue - fromValue) * t;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record TyreThermalState(double surfaceTemperatureC, double carcassTemperatureC, double slipExposure) {
    }

    public static double simulateTyreEquilibriumC(double initialTemperatureC, double heatPowerWatts, double compoundHeatGain, double longitudinalG, double lateralG, double speedMetersPerSecond, int ticks) {
        double temperature = initialTemperatureC;
        for (int tick = 0; tick < ticks; tick++) {
            temperature = nextTyreTemperatureC(temperature, heatPowerWatts, compoundHeatGain, longitudinalG, lateralG, speedMetersPerSecond, 0.05);
        }
        return temperature;
    }
}
