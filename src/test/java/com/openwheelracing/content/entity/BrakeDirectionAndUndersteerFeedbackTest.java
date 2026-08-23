package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BrakeDirectionAndUndersteerFeedbackTest {
    @Test
    void forwardGearKeepsBrakingForwardReferencedDuringSpin() {
        assertEquals(1.0, VehiclePhysics.brakeTravelDirection(-8.0, 1.0), 1.0E-12);
        assertEquals(-1.0, VehiclePhysics.brakeTravelDirection(8.0, -1.0), 1.0E-12);
    }

    @Test
    void neutralUsesCurrentLongitudinalTravelDirection() {
        assertEquals(-1.0, VehiclePhysics.brakeTravelDirection(-8.0, 0.0), 1.0E-12);
        assertEquals(1.0, VehiclePhysics.brakeTravelDirection(8.0, 0.0), 1.0E-12);
    }

    @Test
    void trueFrontLimitedYawDeficitEnablesUndersteerFeedback() {
        double relief = VehiclePhysics.understeerFeedbackRelief(
            1.25, 0.82, Math.toRadians(8.0), Math.toRadians(3.0),
            Math.toRadians(3.0), 45.0, 0.20, 3.60);

        assertTrue(relief > 0.5);
    }

    @Test
    void excessiveYawCannotBeMisclassifiedAsUndersteer() {
        assertEquals(0.0, VehiclePhysics.understeerFeedbackRelief(
            1.25, 0.82, Math.toRadians(8.0), Math.toRadians(3.0),
            Math.toRadians(3.0), 45.0, 1.20, 3.60), 1.0E-12);
    }

    @Test
    void rearSlipDominanceCannotEnableUndersteerFeedback() {
        assertEquals(0.0, VehiclePhysics.understeerFeedbackRelief(
            1.25, 0.82, Math.toRadians(5.0), Math.toRadians(10.0),
            Math.toRadians(3.0), 45.0, 0.20, 3.60), 1.0E-12);
    }
}
