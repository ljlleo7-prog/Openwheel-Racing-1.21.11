package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SurfaceReferenceDistanceTest {
    @Test
    void surfaceOneBlockBelowTyreRemainsAValidGripReference() {
        assertTrue(VehiclePhysics.isSurfaceWithinReferenceDistance(12.08, 11.0, 1.0, 0.08));
    }

    @Test
    void genuinelyAirborneCarDoesNotInheritRemoteSurfaceGrip() {
        assertFalse(VehiclePhysics.isSurfaceWithinReferenceDistance(12.081, 11.0, 1.0, 0.08));
    }

    @Test
    void toleranceAllowsSmallContactPenetration() {
        assertTrue(VehiclePhysics.isSurfaceWithinReferenceDistance(10.96, 11.0, 1.0, 0.08));
    }
}
