package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyboardStabilityAssistTest {
    @Test
    void disabledAssistPreservesDriverInputsAndBrakeBias() {
        VehiclePhysics.KeyboardStabilityInputs result = VehiclePhysics.keyboardStabilityInputs(
            1.0, 1.0, 1.0, 50.0, Math.toRadians(12.0), Math.toRadians(2.0), 0.58, 0.0);

        assertEquals(1.0, result.throttle(), 1.0E-12);
        assertEquals(1.0, result.brake(), 1.0E-12);
        assertEquals(0.58, result.frontBrakeBias(), 1.0E-12);
    }

    @Test
    void frontSlipSoftensThrottleWithoutRemovingFullDriverRequest() {
        VehiclePhysics.KeyboardStabilityInputs result = VehiclePhysics.keyboardStabilityInputs(
            1.0, 0.0, 1.0, 50.0, Math.toRadians(12.0), Math.toRadians(2.0), 0.58, 1.0);

        assertTrue(result.throttle() >= 0.50 && result.throttle() < 1.0);
        assertEquals(0.0, result.brake(), 1.0E-12);
    }

    @Test
    void rearSlipMovesBrakeBiasForwardAndOnlyModeratelyReducesBraking() {
        VehiclePhysics.KeyboardStabilityInputs result = VehiclePhysics.keyboardStabilityInputs(
            0.0, 1.0, 1.0, 50.0, Math.toRadians(2.0), Math.toRadians(10.0), 0.58, 1.0);

        assertTrue(result.frontBrakeBias() > 0.58 && result.frontBrakeBias() <= 0.72);
        assertTrue(result.brake() >= 0.75 && result.brake() < 1.0);
    }
}
