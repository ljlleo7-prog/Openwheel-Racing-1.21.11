package com.openwheelracing.content.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TrackWeatherProgressionTest {
    @Test
    void twentySecondsOfNormalRainIsStillMostlyDry() {
        assertEquals(0.667, TrackWeatherProgression.rainingProgress(400L, false), 0.002);
    }

    @Test
    void sustainedRainProgressesThroughClearTrackPhases() {
        assertEquals(1.0, TrackWeatherProgression.rainingProgress(600L, false), 1.0E-9);
        assertEquals(2.0, TrackWeatherProgression.rainingProgress(2600L, false), 1.0E-9);
        assertEquals(3.0, TrackWeatherProgression.rainingProgress(14400L, false), 1.0E-9);
    }

    @Test
    void fiveMinuteNormalShowerCannotBecomeSoaking() {
        assertEquals(2.288, TrackWeatherProgression.rainingProgress(6000L, false), 0.002);
    }

    @Test
    void thunderIsFasterButStillNeedsThreeMinutesToBecomeSoaking() {
        assertEquals(1.0, TrackWeatherProgression.rainingProgress(400L, true), 1.0E-9);
        assertEquals(2.0, TrackWeatherProgression.rainingProgress(1400L, true), 1.0E-9);
        assertEquals(3.0, TrackWeatherProgression.rainingProgress(6000L, true), 1.0E-9);
    }

    @Test
    void dryingRetreatsInBroadStages() {
        assertEquals(3.0, TrackWeatherProgression.dryingProgress(3.0, 0L), 1.0E-9);
        assertEquals(2.0, TrackWeatherProgression.dryingProgress(3.0, 900L), 1.0E-9);
        assertEquals(1.0, TrackWeatherProgression.dryingProgress(3.0, 1800L), 1.0E-9);
        assertEquals(0.0, TrackWeatherProgression.dryingProgress(3.0, 2700L), 1.0E-9);
    }

    @Test
    void resumedRainContinuesFromPersistedProgress() {
        assertEquals(1.5, TrackWeatherProgression.advanceRainingProgress(1.0, 1000L, false), 1.0E-9);
    }
}
