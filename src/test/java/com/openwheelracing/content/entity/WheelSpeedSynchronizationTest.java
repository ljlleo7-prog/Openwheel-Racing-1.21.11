package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WheelSpeedSynchronizationTest {
    @Test
    void rollingWheelIsSynchronizedWithPatch() {
        VehiclePhysics.WheelSpeedSynchronization sync = VehiclePhysics.wheelSpeedSynchronization(
            30.0 / 0.33, 0.33, 30.0);

        assertEquals(30.0, sync.surfaceSpeed(), 1.0E-12);
        assertEquals(0.0, sync.relativeDifference(), 1.0E-12);
    }

    @Test
    void lockedWheelReportsNegativeDifference() {
        VehiclePhysics.WheelSpeedSynchronization sync = VehiclePhysics.wheelSpeedSynchronization(
            0.0, 0.33, 30.0);

        assertEquals(-1.0, sync.relativeDifference(), 1.0E-12);
    }

    @Test
    void wheelspinReportsPositiveDifferenceInReverseToo() {
        VehiclePhysics.WheelSpeedSynchronization sync = VehiclePhysics.wheelSpeedSynchronization(
            -40.0 / 0.33, 0.33, -30.0);

        assertTrue(sync.relativeDifference() > 0.3);
        assertTrue(!sync.directionMismatch());
    }

    @Test
    void oppositeRotationIsFlagged() {
        assertTrue(VehiclePhysics.wheelSpeedSynchronization(
            -10.0 / 0.33, 0.33, 30.0).directionMismatch());
    }
}
