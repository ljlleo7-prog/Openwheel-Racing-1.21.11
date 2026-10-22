package com.openwheelracing.content.weather;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeatherScheduleTest {
    private WeatherRegion region(double wet, double storm, double snow, long seed) {
        return new WeatherRegion("test", -100, -100, 100, 100, wet, storm, snow, 1200, seed);
    }

    @Test
    void forecastMatchesLiveSamplingAtEveryBoundary() {
        var region = region(0.6, 0.4, 0.3, 1234);
        for (long tick = 0; tick < 100_000; tick += 137) {
            var forecast = WeatherSchedule.sample(region, tick);
            assertEquals(forecast, WeatherSchedule.sample(region, forecast.startsAt()));
            assertEquals(forecast, WeatherSchedule.sample(region, forecast.endsAt() - 1));
            assertEquals(forecast.endsAt(), WeatherSchedule.sample(region, forecast.endsAt()).startsAt());
            assertTrue(forecast.startsAt() <= tick && tick < forecast.endsAt());
        }
    }

    @Test
    void climateExtremesProduceDryRainStormAndSnowWithoutBiomeInput() {
        assertEquals(WeatherSchedule.Condition.CLEAR, WeatherSchedule.sample(region(0, 1, 1, 42), 9999).condition());
        assertEquals(WeatherSchedule.Condition.RAIN, WeatherSchedule.sample(region(1, 0, 0, 42), 9999).condition());
        assertEquals(WeatherSchedule.Condition.THUNDER, WeatherSchedule.sample(region(1, 1, 0, 42), 9999).condition());
        assertEquals(WeatherSchedule.Condition.SNOW, WeatherSchedule.sample(region(1, 1, 1, 42), 9999).condition());
    }

    @Test
    void regionsHaveIndependentReproducibleVariance() {
        var first = region(0.5, 0.4, 0.3, 1);
        var second = region(0.5, 0.4, 0.3, 2);
        boolean differs = false;
        int wet = 0;
        for (int slot = 0; slot < 10000; slot++) {
            long tick = slot * 1200L;
            var sample = WeatherSchedule.sample(first, tick);
            assertEquals(sample, WeatherSchedule.sample(region(0.5, 0.4, 0.3, 1), tick));
            differs |= sample.condition() != WeatherSchedule.sample(second, tick).condition();
            if (sample.wet()) wet++;
        }
        assertTrue(differs);
        assertTrue(wet > 4500 && wet < 5500);
    }

    @Test
    void rainUsuallyContinuesInsteadOfClearingAtTheNextBoundary() {
        var climate = region(0.3, 0, 0, 12345);
        int rainPeriods = 0;
        int continuedRain = 0;
        int wetPeriods = 0;
        var previous = WeatherSchedule.sample(climate, -1200);
        for (int slot = 0; slot < 100000; slot++) {
            var current = WeatherSchedule.sample(climate, slot * 1200L);
            if (current.wet()) wetPeriods++;
            if (previous.condition() == WeatherSchedule.Condition.RAIN) {
                rainPeriods++;
                if (current.condition() == WeatherSchedule.Condition.RAIN) continuedRain++;
            }
            previous = current;
        }
        // Stationary wet share remains 30%, while P(rain -> rain) is 0.8 + 0.2 * 0.3 = 0.86.
        assertEquals(0.3, (double) wetPeriods / 100000, 0.025);
        assertEquals(0.86, (double) continuedRain / rainPeriods, 0.025);
    }

    @Test
    void snowAndStormsAlsoPersistWithoutChangingTheirClimateShares() {
        var climate = region(1, 0.5, 0.5, 9876);
        int snow = 0;
        int storms = 0;
        int unchanged = 0;
        var previous = WeatherSchedule.sample(climate, -1200);
        for (int slot = 0; slot < 100000; slot++) {
            var current = WeatherSchedule.sample(climate, slot * 1200L);
            if (current.condition() == WeatherSchedule.Condition.SNOW) snow++;
            if (current.thunder()) storms++;
            if (current.condition() == previous.condition()) unchanged++;
            previous = current;
        }
        assertEquals(0.5, (double) snow / 100000, 0.025);
        assertEquals(0.25, (double) storms / 100000, 0.025);
        assertTrue(unchanged > 80000);
    }

    @Test
    void forecastsAreIndependentOfQueryOrderAndWorkFarIntoTheFuture() {
        var climate = region(0.5, 0.4, 0.3, 555);
        long[] ticks = {0, 1_000_000_000_000L, 1200, 6000, 3600, 2400};
        for (long tick : ticks) {
            var expected = WeatherSchedule.sample(climate, tick);
            WeatherSchedule.sample(climate, tick + 100000);
            WeatherSchedule.sample(climate, -12000);
            assertEquals(expected, WeatherSchedule.sample(climate, tick));
        }
    }

    @Test
    void validatesRegionBoundsAndClimateValues() {
        assertThrows(IllegalArgumentException.class, () -> region(Double.NaN, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> region(0, -0.1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> region(0, 0, 1.1, 1));
        assertThrows(IllegalArgumentException.class, () -> new WeatherRegion("bad", 10, 0, 0, 10, 0, 0, 0, 1200, 1));
        assertTrue(region(0, 0, 0, 1).contains(-100, 100));
        assertFalse(region(0, 0, 0, 1).contains(-101, 100));
    }
}
