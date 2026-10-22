package com.openwheelracing.content.weather;

import org.junit.jupiter.api.Test;
import com.openwheelracing.content.block.TrackWeatherResponseModel;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeatherSurfaceStateTest {
    private WeatherRegion region(double wet) {
        return new WeatherRegion("circuit", 0, 0, 100, 100, wet, 0, 0, 1200, 42);
    }

    @Test
    void newRainRegionDoesNotInheritRainFromBeforeItWasCreated() {
        long createdAt = 1_000_000;
        var state = WeatherSurfaceState.initial(region(1), createdAt);
        assertEquals(0, state.progress());
        assertEquals(0.25, state.advance(createdAt + 1200).progress(), 1e-9);
        assertEquals(1, state.advance(createdAt + 4800).progress(), 1e-9);
        assertEquals(1.5, state.advance(createdAt + 14400).progress(), 1e-9);
    }

    @Test
    void continuousRainKeepsItsSpatialPatternAcrossForecastPeriods() {
        var initial = WeatherSurfaceState.initial(region(1), 100);
        var later = initial.advance(20000);
        assertEquals(initial.epoch(), later.epoch());
        assertEquals(1.5, later.progress(), 1e-9);
    }

    @Test
    void changingADryRegionToRainStartsWettingAtTheEditTime() {
        var dry = WeatherSurfaceState.initial(region(0), 0).advance(1_000_000);
        var rain = dry.reconfigure(region(1), 1_000_000);
        assertEquals(0, rain.progress());
        assertEquals(1_000_000, rain.epoch());
        assertEquals(0.5, rain.advance(1_002_400).progress(), 1e-9);
    }

    @Test
    void savedStatePreservesBlocksThatDryLaterThanTheNominalTarget() {
        var clear = new WeatherSurfaceState(region(0), 0, 3, 0, 3, false, false);
        var saved = clear.advance(25200);
        assertEquals(0, saved.progress(), 1e-9);
        assertEquals(1, TrackWeatherResponseModel.targetLevel(saved.progress(), 0.25, true));
        var restored = new WeatherSurfaceState(saved.region(), saved.updatedAt(), saved.progress(), saved.epoch(),
            saved.progressAtEpoch(), saved.raining(), saved.thundering());
        var later = restored.advance(29400);
        assertEquals(-0.5, later.progress(), 1e-9);
        assertEquals(0, TrackWeatherResponseModel.targetLevel(later.progress(), 0.25, true));
        var rainingAgain = later.reconfigure(region(1), 29400);
        assertEquals(0, rainingAgain.progress(), 1e-9);
        assertEquals(0.5, rainingAgain.advance(31800).progress(), 1e-9);
    }

    @Test
    void resumingSavedStateMatchesUninterruptedProgression() {
        var initial = WeatherSurfaceState.initial(region(1), 400);
        var saved = initial.advance(4100);
        var restored = new WeatherSurfaceState(saved.region(), saved.updatedAt(), saved.progress(), saved.epoch(),
            saved.progressAtEpoch(), saved.raining(), saved.thundering());
        assertEquals(initial.advance(10000).progress(), restored.advance(10000).progress(), 1e-9);
        assertEquals(initial.epoch(), restored.advance(10000).epoch());
    }
}
