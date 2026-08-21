package com.openwheelracing.content.entity;

import com.openwheelracing.content.block.TrackMoisture;
import com.openwheelracing.content.car.TyreType;

public final class WetTyrePhysics {
    private static final double[][] GRIP = {
        {1.00, 0.84, 0.58, 0.32},
        {1.00, 0.93, 0.74, 0.45},
        {1.00, 0.89, 0.82, 0.58}
    };
    private static final double[] BASE_COOLING = {1.0, 1.35, 2.2, 3.5};
    private static final double[] HOT_COOLING = {1.0, 3.5, 8.0, 15.0};
    private static final double[] RESISTANCE = {0.0, 0.001, 0.004, 0.012};
    private static final double[] RESISTANCE_TYPE = {1.0, 0.80, 0.62};
    private static final double[] SOAKING_ONSET_KMH = {65.0, 115.0, 190.0};
    private static final double[] FULL_SPAN_KMH = {85.0, 95.0, 120.0};

    private WetTyrePhysics() {
    }

    public static double wetGrip(TyreType type, TrackMoisture moisture) {
        return wetGrip(type, moisture.level());
    }

    public static double wetGrip(TyreType type, int moistureLevel) {
        return GRIP[type.id()][Math.max(0, Math.min(3, moistureLevel))];
    }

    public static double coolingMultiplier(TrackMoisture moisture, double surfaceTemperatureC) {
        return coolingMultiplier(moisture.level(), surfaceTemperatureC);
    }

    public static double coolingMultiplier(TyreType type, TrackMoisture moisture, double temperatureC) {
        return coolingMultiplier(type, moisture.level(), temperatureC);
    }

    public static double coolingMultiplier(TyreType type, int moistureLevel, double temperatureC) {
        if (type == TyreType.SLICK) return coolingMultiplier(moistureLevel, temperatureC);
        int level = Math.max(0, Math.min(3, moistureLevel));
        double hotStart = type == TyreType.INTERMEDIATE ? 80.0 : 52.0;
        double hotSpan = type == TyreType.INTERMEDIATE ? 15.0 : 16.0;
        double hot = smoothstep((temperatureC - hotStart) / hotSpan);
        double waterCooling = lerp(BASE_COOLING[level], HOT_COOLING[level], hot);
        double treadCoupling = type == TyreType.INTERMEDIATE ? 1.25 : 1.50;
        return 1.0 + (waterCooling - 1.0) * treadCoupling;
    }

    public static double coolingMultiplier(int moistureLevel, double surfaceTemperatureC) {
        int level = Math.max(0, Math.min(3, moistureLevel));
        double hot = smoothstep((surfaceTemperatureC - 95.0) / 10.0);
        return lerp(BASE_COOLING[level], HOT_COOLING[level], hot);
    }

    public static double carcassCoolingMultiplier(TrackMoisture moisture, double surfaceTemperatureC) {
        return 1.0 + (coolingMultiplier(moisture, surfaceTemperatureC) - 1.0) * 0.35;
    }

    public static double carcassCoolingMultiplier(int moistureLevel, double surfaceTemperatureC) {
        return 1.0 + (coolingMultiplier(moistureLevel, surfaceTemperatureC) - 1.0) * 0.35;
    }

    public static double carcassCoolingMultiplier(TyreType type, TrackMoisture moisture, double carcassTemperatureC) {
        return carcassCoolingMultiplier(type, moisture.level(), carcassTemperatureC);
    }

    public static double carcassCoolingMultiplier(TyreType type, int moistureLevel, double carcassTemperatureC) {
        double coupling = switch (type) {
            case SLICK -> 0.35;
            case INTERMEDIATE -> 0.45;
            case WET -> 0.55;
        };
        return 1.0 + (coolingMultiplier(type, moistureLevel, carcassTemperatureC) - 1.0) * coupling;
    }

    public static double waterResistance(TyreType type, TrackMoisture moisture) {
        return RESISTANCE[moisture.level()] * RESISTANCE_TYPE[type.id()];
    }

    public static double hydroplaningSeverity(TyreType type, TrackMoisture moisture, double speedKmh, double lockup) {
        return hydroplaningSeverity(type, moisture.depth(), speedKmh, lockup);
    }

    public static double hydroplaningSeverity(TyreType type, double waterDepth, double speedKmh, double lockup) {
        if (waterDepth <= 0.0) return 0.0;
        double onset = SOAKING_ONSET_KMH[type.id()] / Math.sqrt(Math.max(0.15, waterDepth));
        onset *= 1.0 - 0.60 * clamp(lockup, 0.0, 1.0);
        return smoothstep((speedKmh - onset) / FULL_SPAN_KMH[type.id()]);
    }

    public static double lateralHydroGrip(double severity) { return 1.0 - 0.45 * clamp(severity, 0.0, 1.0); }
    public static double longitudinalHydroGrip(double severity) { return 1.0 - 0.65 * clamp(severity, 0.0, 1.0); }

    public static double temperatureGrip(TyreType type, double temperatureC) {
        if (type == TyreType.SLICK) return 1.0;
        double center = type == TyreType.INTERMEDIATE ? 75.0 : 45.0;
        double coldSpan = type == TyreType.INTERMEDIATE ? 30.0 : 22.0;
        double hotStart = type == TyreType.INTERMEDIATE ? 82.0 : 55.0;
        double severe = type == TyreType.INTERMEDIATE ? 100.0 : 78.0;
        double cold = clamp((center - temperatureC) / coldSpan, 0.0, 1.0);
        double hot = smoothstep((temperatureC - hotStart) / (severe - hotStart));
        return clamp(1.0 - 0.20 * cold - 0.38 * hot, 0.55, 1.0);
    }

    public static double temperatureWear(TyreType type, double temperatureC) {
        if (type == TyreType.SLICK) return 1.0;
        double onset = type == TyreType.INTERMEDIATE ? 85.0 : 60.0;
        double severe = type == TyreType.INTERMEDIATE ? 105.0 : 80.0;
        return 1.0 + 1.35 * smoothstep((temperatureC - onset) / (severe - onset));
    }

    private static double smoothstep(double value) {
        double t = clamp(value, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
