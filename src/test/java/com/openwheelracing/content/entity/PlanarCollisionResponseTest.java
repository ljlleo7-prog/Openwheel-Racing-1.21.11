package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlanarCollisionResponseTest {
    @Test
    void equalCarsShareHeadOnImpactSymmetrically() {
        VehiclePhysics.CollisionResponse response = VehiclePhysics.resolvePlanarCollision(
            0.60, 0.0, 800.0,
            0.0, 0.0, 800.0,
            1.0, 0.0, 0.28);

        assertEquals(0.216, response.firstVelocityX(), 1.0E-9);
        assertEquals(0.384, response.secondVelocityX(), 1.0E-9);
        assertEquals(0.60, response.firstVelocityX() + response.secondVelocityX(), 1.0E-9);
    }

    @Test
    void swappingCarsAndNormalOnlySwapsTheResult() {
        VehiclePhysics.CollisionResponse forward = VehiclePhysics.resolvePlanarCollision(
            0.55, 0.12, 780.0,
            -0.10, -0.04, 860.0,
            0.8, 0.6, 0.22);
        VehiclePhysics.CollisionResponse reversed = VehiclePhysics.resolvePlanarCollision(
            -0.10, -0.04, 860.0,
            0.55, 0.12, 780.0,
            -0.8, -0.6, 0.22);

        assertEquals(forward.firstVelocityX(), reversed.secondVelocityX(), 1.0E-12);
        assertEquals(forward.firstVelocityZ(), reversed.secondVelocityZ(), 1.0E-12);
        assertEquals(forward.secondVelocityX(), reversed.firstVelocityX(), 1.0E-12);
        assertEquals(forward.secondVelocityZ(), reversed.firstVelocityZ(), 1.0E-12);
    }

    @Test
    void separatingCarsReceiveNoSecondImpulse() {
        VehiclePhysics.CollisionResponse response = VehiclePhysics.resolvePlanarCollision(
            -0.20, 0.03, 800.0,
            0.15, -0.02, 800.0,
            1.0, 0.0, 0.28);

        assertEquals(-0.20, response.firstVelocityX(), 1.0E-12);
        assertEquals(0.15, response.secondVelocityX(), 1.0E-12);
    }
}
