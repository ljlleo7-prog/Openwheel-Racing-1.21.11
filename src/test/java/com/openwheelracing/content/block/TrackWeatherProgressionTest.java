package com.openwheelracing.content.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TrackWeatherProgressionTest {
    @Test
    void normalRainTakesSeveralMinutesToDampenTrack() {
        assertEquals(0.5, TrackWeatherProgression.rainingProgress(2400L, false), 1.0E-9);
        assertEquals(1.0, TrackWeatherProgression.rainingProgress(4800L, false), 1.0E-9);
    }

    @Test
    void sustainedNormalRainSettlesBetweenDampAndWet() {
        assertEquals(1.0, TrackWeatherProgression.rainingProgress(4800L, false), 1.0E-9);
        assertEquals(1.5, TrackWeatherProgression.rainingProgress(14400L, false), 1.0E-9);
        assertEquals(1.5, TrackWeatherProgression.rainingProgress(43200L, false), 1.0E-9);
    }

    @Test
    void fiveMinuteNormalShowerOnlyDampensTrack() {
        assertEquals(1.125, TrackWeatherProgression.rainingProgress(6000L, false), 1.0E-9);
    }

    @Test
    void thunderIsFasterThanNormalRain() {
        assertEquals(1.0, TrackWeatherProgression.rainingProgress(3600L, true), 1.0E-9);
        assertEquals(2.0, TrackWeatherProgression.rainingProgress(18000L, true), 1.0E-9);
        assertEquals(3.0, TrackWeatherProgression.rainingProgress(32400L, true), 1.0E-9);
    }

    @Test
    void dryingRetreatsInBroadStages() {
        assertEquals(3.0, TrackWeatherProgression.dryingProgress(3.0, 0L), 1.0E-9);
        assertEquals(2.0, TrackWeatherProgression.dryingProgress(3.0, 8400L), 1.0E-9);
        assertEquals(1.0, TrackWeatherProgression.dryingProgress(3.0, 16800L), 1.0E-9);
        assertEquals(0.0, TrackWeatherProgression.dryingProgress(3.0, 25200L), 1.0E-9);
    }

    @Test
    void resumedRainContinuesFromPersistedProgress() {
        assertEquals(1.5, TrackWeatherProgression.advanceRainingProgress(1.0, 9600L, false), 1.0E-9);
    }
}
