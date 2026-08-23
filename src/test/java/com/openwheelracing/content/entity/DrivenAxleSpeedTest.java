package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DrivenAxleSpeedTest {
    @Test
    void launchWheelspinRaisesDrivelineSpeedAboveChassisSpeed() {
        assertEquals(39.6, VehiclePhysics.drivenAxleSpeedMetersPerSecond(
            120.0, 120.0, 0.33, 10.0, true), 1.0E-12);
    }

    @Test
    void differentialCarrierUsesAverageSignedWheelRotation() {
        assertEquals(0.0, VehiclePhysics.drivenAxleSpeedMetersPerSecond(
            100.0, -100.0, 0.33, 20.0, true), 1.0E-12);
    }

    @Test
    void chassisSpeedIsUsedOnlyBeforeWheelStateInitialization() {
        assertEquals(18.0, VehiclePhysics.drivenAxleSpeedMetersPerSecond(
            0.0, 0.0, 0.33, 18.0, false), 1.0E-12);
        assertEquals(0.0, VehiclePhysics.drivenAxleSpeedMetersPerSecond(
            0.0, 0.0, 0.33, 18.0, true), 1.0E-12);
    }
}
