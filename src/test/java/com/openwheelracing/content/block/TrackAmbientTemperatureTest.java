package com.openwheelracing.content.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrackAmbientTemperatureTest {
    @Test
    void rainCoolsTrackCloseToTwentyWithinNinetySeconds() {
        double temperature = TrackAmbientTemperature.progress(36.0, 20.0, 90.0, 45.0);
        assertTrue(temperature > 20.0 && temperature < 22.5, "temperature=" + temperature);
    }

    @Test
    void sunlightWarmsTrackMuchMoreSlowly() {
        double afterEightMinutes = TrackAmbientTemperature.progress(20.0, 36.0, 480.0, 480.0);
        double afterSixteenMinutes = TrackAmbientTemperature.progress(20.0, 36.0, 960.0, 480.0);
        assertEquals(30.11, afterEightMinutes, 0.02);
        assertTrue(afterSixteenMinutes > 33.5 && afterSixteenMinutes < 34.0);
    }
}
