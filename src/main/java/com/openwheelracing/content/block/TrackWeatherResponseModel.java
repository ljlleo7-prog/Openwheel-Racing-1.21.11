package com.openwheelracing.content.block;

/** Natural wetting/drying only. Tyre-contact drying applies directly through TrackDryingBudget. */
public final class TrackWeatherResponseModel {
    private TrackWeatherResponseModel() {
    }

    public static int targetLevel(double progress, double spatialThreshold) {
        return targetLevel(progress, spatialThreshold, false);
    }

    public static int targetLevel(double progress, double spatialThreshold, boolean drying) {
        if (drying) {
            // Center stage loss on the nominal deadline; retain a Gaussian late tail.
            return Math.clamp((int) Math.ceil(progress + 0.5 - spatialThreshold), 0, 3);
        }
        double clamped = Math.max(0.0, Math.min(3.0, progress));
        int lower = (int) Math.floor(clamped);
        return Math.min(3, lower + (spatialThreshold < clamped - lower ? 1 : 0));
    }

    public static int transitionLevel(int current, int target, boolean precipitating, boolean exposed) {
        if (precipitating) return exposed && target > current ? current + 1 : current;
        return target < current ? current - 1 : current;
    }
}
