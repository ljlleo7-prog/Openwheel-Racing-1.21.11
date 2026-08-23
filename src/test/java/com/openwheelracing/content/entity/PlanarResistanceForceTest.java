package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlanarResistanceForceTest {
    @Test
    void pureLateralMotionReceivesPureLateralResistance() {
        VehiclePhysics.PlanarForce force = VehiclePhysics.opposingPlanarForce(0.0, 20.0, 500.0);

        assertEquals(0.0, force.longitudinal(), 1.0E-12);
        assertEquals(-500.0, force.lateral(), 1.0E-12);
    }

    @Test
    void diagonalResistanceOpposesMotionWithRequestedMagnitude() {
        double velocityLongitudinal = 30.0;
        double velocityLateral = -40.0;
        VehiclePhysics.PlanarForce force = VehiclePhysics.opposingPlanarForce(
            velocityLongitudinal, velocityLateral, 750.0);

        assertEquals(750.0, Math.hypot(force.longitudinal(), force.lateral()), 1.0E-9);
        assertTrue(force.longitudinal() * velocityLongitudinal + force.lateral() * velocityLateral < 0.0);
        assertEquals(0.0,
            force.longitudinal() * velocityLateral - force.lateral() * velocityLongitudinal,
            1.0E-9);
    }

    @Test
    void stoppedCarReceivesNoDirectionalResistance() {
        VehiclePhysics.PlanarForce force = VehiclePhysics.opposingPlanarForce(0.0, 0.0, 500.0);

        assertEquals(0.0, force.longitudinal(), 1.0E-12);
        assertEquals(0.0, force.lateral(), 1.0E-12);
    }
}
