package com.openwheelracing.content.weather;

import com.openwheelracing.content.block.TrackWeatherProgression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeatherSurfaceModelTest {
    @Test
    void wettingContinuesAcrossScheduleBoundaries() {
        var rain = new WeatherRegion("rain", 0, 0, 10, 10, 1, 0, 0, 1200, 10);
        assertEquals(TrackWeatherProgression.rainingProgress(20000, false), WeatherSurfaceModel.progressAt(rain, 20000), 1e-10);
        assertEquals(1.5, WeatherSurfaceModel.progressAt(rain, 100000), 1e-10);
    }

    @Test
    void snowWetsSurfacesAndStormsCanReachSoaking() {
        var snow = new WeatherRegion("snow", 0, 0, 10, 10, 1, 0, 1, 1200, 10);
        var storm = new WeatherRegion("storm", 0, 0, 10, 10, 1, 1, 0, 1200, 10);
        assertEquals(1.5, WeatherSurfaceModel.progressAt(snow, 100000), 1e-10);
        assertEquals(3, WeatherSurfaceModel.progressAt(storm, 100000), 1e-10);
    }

    @Test
    void dryWeatherUsesExistingDryingRate() {
        var clear = new WeatherSchedule.Sample(WeatherSchedule.Condition.CLEAR, 0, 1200);
        assertEquals(2, WeatherSurfaceModel.advance(3, clear, 8400), 1e-10);
        assertEquals(-0.5, WeatherSurfaceModel.advance(3, clear, 100000), 1e-10);
    }
}
