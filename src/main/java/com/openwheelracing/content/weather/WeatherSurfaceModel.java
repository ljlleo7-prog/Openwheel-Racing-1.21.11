package com.openwheelracing.content.weather;

import com.openwheelracing.content.block.TrackWeatherProgression;

/** Reuses the existing multi-minute wetting and drying calibration. */
public final class WeatherSurfaceModel {
    private WeatherSurfaceModel() {
    }

    public static double advance(double progress, WeatherSchedule.Sample weather, long ticks) {
        return weather.wet() ? TrackWeatherProgression.advanceRainingProgress(progress, ticks, weather.thunder())
            : TrackWeatherProgression.naturalDryingProgress(progress, ticks);
    }

    public static double progressAt(WeatherRegion region, long tick) {
        double progress = 0;
        for (long start = 0; start < tick;) {
            var weather = WeatherSchedule.sample(region, start);
            long end = Math.min(tick, weather.endsAt());
            progress = advance(progress, weather, end - start);
            start = end;
        }
        return progress;
    }
}
