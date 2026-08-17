package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiGripCalibrationTest {
    @Test
    void twoCleanLapsPromoteWholeControlEnvelopeOneStep() {
        AiGripCalibration calibration = new AiGripCalibration();
        calibration.recordLap(true);
        assertEquals(1.0, calibration.settings().paceScale());
        calibration.recordLap(true);
        assertEquals(1.025, calibration.settings().paceScale());
        assertEquals(0.52, calibration.settings().brakingCapabilityFraction(), 1.0E-9);
        assertEquals(0.80, calibration.settings().speedErrorGain(), 1.0E-9);
    }

    @Test
    void dirtyTrialReturnsToAndFreezesPreviousProvenEnvelope() {
        AiGripCalibration calibration = new AiGripCalibration();
        calibration.recordLap(true);
        calibration.recordLap(true);
        calibration.recordLap(false);
        assertTrue(calibration.frozen());
        assertEquals(1.0, calibration.settings().paceScale());
        calibration.recordLap(true);
        assertEquals(1.0, calibration.settings().paceScale());
    }

    @Test
    void randomStepsAreBoundedAndStableForSeed() {
        AiGripCalibration first = new AiGripCalibration(0.75, true, 42L);
        AiGripCalibration second = new AiGripCalibration(0.75, true, 42L);
        first.recordLap(true);
        first.recordLap(true);
        second.recordLap(true);
        second.recordLap(true);
        assertEquals(first.settings(), second.settings());
        assertTrue(first.settings().paceScale() >= 1.0);
        assertTrue(first.settings().paceScale() <= 1.0075);
    }

    @Test
    void manualPushReopensFrozenCalibrationAtNextBoundedLevel() {
        AiGripCalibration calibration = new AiGripCalibration(0.5, false, 1L);
        calibration.recordLap(false);
        assertTrue(calibration.frozen());
        assertTrue(calibration.manualPush());
        assertFalse(calibration.frozen());
        assertEquals(1.005, calibration.settings().paceScale(), 1.0E-9);
    }
}
