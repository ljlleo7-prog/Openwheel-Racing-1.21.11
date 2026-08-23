package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TyreRelaxationTransportTest {
    private static final double RELAXATION_LENGTH_METERS = 0.42;
    private static final double SUBSTEP_SECONDS = 0.05 / 4.0;

    @Test
    void pureLateralSlidingBuildsForceAsQuicklyAsEqualSpeedForwardMotion() {
        double forwardGain = VehiclePhysics.tyreRelaxationGainForPatch(
            40.0, 0.0, RELAXATION_LENGTH_METERS, SUBSTEP_SECONDS);
        double lateralGain = VehiclePhysics.tyreRelaxationGainForPatch(
            0.0, 40.0, RELAXATION_LENGTH_METERS, SUBSTEP_SECONDS);

        assertEquals(forwardGain, lateralGain, 1.0E-12);
        assertTrue(lateralGain > 0.65,
            "A fast lateral slide must not retain almost all of the previous tyre-force direction");
    }

    @Test
    void diagonalPatchSpeedControlsRelaxationTransport() {
        double diagonalGain = VehiclePhysics.tyreRelaxationGainForPatch(
            30.0, 40.0, RELAXATION_LENGTH_METERS, SUBSTEP_SECONDS);
        double equalTotalSpeedGain = VehiclePhysics.tyreRelaxationGainForPatch(
            50.0, 0.0, RELAXATION_LENGTH_METERS, SUBSTEP_SECONDS);

        assertEquals(equalTotalSpeedGain, diagonalGain, 1.0E-12);
    }
}
