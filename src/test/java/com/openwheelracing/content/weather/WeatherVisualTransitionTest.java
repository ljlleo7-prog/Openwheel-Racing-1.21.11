package com.openwheelracing.content.weather;

import org.junit.jupiter.api.Test;

import static com.openwheelracing.content.weather.WeatherSchedule.Condition.*;
import static org.junit.jupiter.api.Assertions.*;

class WeatherVisualTransitionTest {
    @Test
    void rainBuildsOverTenSecondsWithFrameInterpolation() {
        var visuals = new WeatherVisualTransition();
        visuals.tick(RAIN);
        assertEquals(0, visuals.intensity(0));
        assertEquals(0.0025, visuals.intensity(0.5F), 1e-6);
        assertEquals(0.005, visuals.intensity(1), 1e-6);
        tick(visuals, RAIN, 99);
        assertEquals(0.5, visuals.intensity(1), 1e-5);
        tick(visuals, RAIN, 100);
        assertEquals(1, visuals.intensity(1));
    }

    @Test
    void clearingKeepsRainVisibleUntilItHasFadedOut() {
        var visuals = new WeatherVisualTransition();
        tick(visuals, RAIN, 200);
        visuals.tick(CLEAR);
        assertEquals(RAIN, visuals.displayed());
        assertEquals(0.995, visuals.intensity(1), 1e-6);
        tick(visuals, CLEAR, 199);
        assertEquals(0, visuals.intensity(1));
        assertEquals(RAIN, visuals.displayed());
        visuals.tick(CLEAR);
        assertEquals(CLEAR, visuals.displayed());
    }

    @Test
    void rainChangesToSnowOnlyWhileFullyInvisible() {
        var visuals = new WeatherVisualTransition();
        tick(visuals, RAIN, 200);
        tick(visuals, SNOW, 100);
        assertEquals(RAIN, visuals.displayed());
        assertEquals(0.5, visuals.intensity(1), 1e-5);
        tick(visuals, SNOW, 100);
        assertEquals(0, visuals.intensity(1));
        visuals.tick(SNOW);
        assertEquals(SNOW, visuals.displayed());
        assertEquals(0, visuals.intensity(0));
        assertEquals(0, visuals.intensity(1));
        tick(visuals, SNOW, 200);
        assertEquals(1, visuals.intensity(1));
    }

    @Test
    void aChangingTargetReversesTheFadeWithoutAnIntensityJump() {
        var visuals = new WeatherVisualTransition();
        tick(visuals, RAIN, 100);
        float before = visuals.intensity(1);
        visuals.tick(CLEAR);
        assertEquals(before, visuals.intensity(0));
        assertEquals(before - 0.005, visuals.intensity(1), 1e-6);
        float fading = visuals.intensity(1);
        visuals.tick(RAIN);
        assertEquals(fading, visuals.intensity(0));
        assertEquals(before, visuals.intensity(1), 1e-6);
    }

    @Test
    void thunderFadesIndependentlyWhileRainContinues() {
        var visuals = new WeatherVisualTransition();
        tick(visuals, THUNDER, 200);
        assertEquals(1, visuals.thunder(1));
        tick(visuals, RAIN, 100);
        assertEquals(1, visuals.intensity(1));
        assertEquals(0.5, visuals.thunder(1), 1e-5);
        visuals.reset();
        assertEquals(CLEAR, visuals.displayed());
        assertEquals(0, visuals.intensity(1));
        assertEquals(0, visuals.thunder(1));
    }

    private void tick(WeatherVisualTransition visuals, WeatherSchedule.Condition condition, int ticks) {
        for (int i = 0; i < ticks; i++) visuals.tick(condition);
    }
}
