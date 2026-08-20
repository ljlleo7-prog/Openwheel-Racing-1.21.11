package com.openwheelracing.content.block;

import com.openwheelracing.content.car.TyreType;

public final class TrackVehicleDryingModel {
    private static final double[] MOISTURE_BASE_CHANCE = {0.0, 0.140, 0.090, 0.055};
    private static final double[] TYRE_TYPE_MULTIPLIER = {0.85, 1.10, 1.25};

    private TrackVehicleDryingModel() {
    }

    public static double dryingChance(int moistureLevel, TyreType tyreType,
                                      double tyreTemperatureC, double speedKmh, double slip) {
        int moisture = Math.max(0, Math.min(3, moistureLevel));
        double temperature = clamp01((tyreTemperatureC - 20.0) / 100.0);
        double speed = clamp01(speedKmh / 280.0);
        double scrub = clamp01(slip);
        double temperatureMultiplier = 0.75 + 1.10 * temperature;
        double speedMultiplier = 0.70 + 1.10 * Math.sqrt(speed);
        double scrubMultiplier = 1.0 + 1.25 * scrub;
        double contactChance = MOISTURE_BASE_CHANCE[moisture]
            * TYRE_TYPE_MULTIPLIER[tyreType.id()]
            * temperatureMultiplier
            * speedMultiplier
            * scrubMultiplier;
        double boiling = smoothstep(clamp01((tyreTemperatureC - 90.0) / 15.0));
        double chance = contactChance + (0.95 - contactChance) * boiling;
        return Math.min(0.95, Math.max(0.0, chance));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double smoothstep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }
}
