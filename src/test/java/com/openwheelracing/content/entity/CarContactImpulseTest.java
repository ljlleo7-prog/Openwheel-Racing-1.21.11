package com.openwheelracing.content.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CarContactImpulseTest {
    @Test
    void centeredImpactPreservesTangentialMotionAndMomentum() {
        var result = VehiclePhysics.resolveRigidPlanarCollision(4, 2, 1000, 0, 1500, 2, 0,
            0, 2, 1500, 0, 2000, -2, 0, 1, 0, .28);
        assertEquals(2, result.firstVelocityZ(), 1e-12);
        assertEquals(2, result.secondVelocityZ(), 1e-12);
        assertEquals(4000, result.firstVelocityX() * 1000 + result.secondVelocityX() * 1500, 1e-9);
        assertEquals(0, result.firstYawRate(), 1e-12);
    }

    @Test
    void offsetImpactGeneratesTorqueWithoutCreatingKineticEnergy() {
        var result = VehiclePhysics.resolveRigidPlanarCollision(0, 4, 1000, 0, 1500, .5, 2,
            0, 0, 1000, 0, 1500, -.5, -2, 0, 1, .28);
        assertNotEquals(0, result.firstYawRate());
        assertNotEquals(0, result.secondYawRate());
        double energy = 500 * (result.firstVelocityZ() * result.firstVelocityZ()
            + result.secondVelocityZ() * result.secondVelocityZ())
            + 750 * (result.firstYawRate() * result.firstYawRate() + result.secondYawRate() * result.secondYawRate());
        assertTrue(energy < 8000);
        assertEquals(4000, 1000 * (result.firstVelocityZ() + result.secondVelocityZ()), 1e-9);
    }

    @Test
    void separatingAndMatchedSpeedCarsReceiveNoImpulse() {
        for (double own : new double[] {2, 3}) {
            var result = VehiclePhysics.resolveRigidPlanarCollision(own, 1, 1000, 0, 1500, 2, 0,
                3, 1, 1000, 0, 1500, -2, 0, 1, 0, .28);
            assertEquals(own, result.firstVelocityX());
            assertEquals(3, result.secondVelocityX());
        }
    }
    @Test
    void rotationCanCloseAContactEvenWhenCentersAreStationary() {
        var result = VehiclePhysics.resolveRigidPlanarCollision(0, 0, 1000, 1, 1500, 1, 0,
            0, 0, 1000, 0, 1500, -1, 0, 0, 1, .28);
        assertTrue(result.firstVelocityZ() < 0);
        assertTrue(result.secondVelocityZ() > 0);
        assertTrue(result.firstYawRate() < 1);
    }
}
