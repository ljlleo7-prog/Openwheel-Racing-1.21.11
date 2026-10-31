package com.openwheelracing.content.race;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LapAttemptRulesTest {
    @Test void actualForwardLineTriggerOverridesTelemetryStartingFarAway() {
        assertTrue(LapAttemptRules.validStart(true, 100, 0, true));
        assertTrue(LapAttemptRules.validStart(true, Double.NaN, Double.NaN, false));
        assertFalse(LapAttemptRules.validStart(false, 100, 0, true));
        assertFalse(LapAttemptRules.validStart(false, 0, 0, false));
    }

    @Test void startLineGraceRequiresForwardMovementWithinThreeMeters() {
        assertTrue(LapAttemptRules.validStart(0, 0, true));
        assertTrue(LapAttemptRules.validStart(3, 0, true));
        assertFalse(LapAttemptRules.validStart(3.01, 0, true));
        assertFalse(LapAttemptRules.validStart(1, 3.01, true));
        assertFalse(LapAttemptRules.validStart(1, 0, false));
        assertFalse(LapAttemptRules.validStart(Double.NaN, 0, true));
    }

    @Test void meaningfulMovementCanBeRecordedWithoutAConfidentRacingLineProfile() {
        assertFalse(LapAttemptRules.shouldDiscard(60_000, 1500));
        assertFalse(LapAttemptRules.shouldDiscard(200, 10));
        assertTrue(LapAttemptRules.shouldDiscard(199, 100));
        assertTrue(LapAttemptRules.shouldDiscard(60_000, 9.99));
    }
}
