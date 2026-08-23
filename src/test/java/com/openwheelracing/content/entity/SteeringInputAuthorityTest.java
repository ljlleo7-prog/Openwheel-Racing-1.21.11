package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SteeringInputAuthorityTest {
    @Test
    void directJoystickKeepsMechanicalSteeringLockAtSpeed() {
        double mechanicalLock = Math.toRadians(34.0);
        double keyboardSpeedLock = Math.toRadians(2.45);

        assertEquals(mechanicalLock,
            VehiclePhysics.steeringLockForInputSource(false, mechanicalLock, keyboardSpeedLock), 1.0E-12);
        assertEquals(keyboardSpeedLock,
            VehiclePhysics.steeringLockForInputSource(true, mechanicalLock, keyboardSpeedLock), 1.0E-12);
    }

    @Test
    void steeringPreviewUsesRoadWheelDegrees() {
        assertEquals(-17.0, VehiclePhysics.steeringCommandDegrees(-0.5, Math.toRadians(34.0)), 1.0E-12);
        assertEquals(34.0, VehiclePhysics.steeringCommandDegrees(1.0, Math.toRadians(34.0)), 1.0E-12);
    }

    @Test
    void networkRoadWheelAngleIsValidatedAgainstMechanicalLock() {
        double lock = Math.toRadians(34.0);

        assertEquals(Math.toRadians(20.0),
            VehiclePhysics.clampSteeringAngleCommand(Math.toRadians(20.0), lock), 1.0E-12);
        assertEquals(-lock,
            VehiclePhysics.clampSteeringAngleCommand(Math.toRadians(-80.0), lock), 1.0E-12);
        assertEquals(0.0, VehiclePhysics.clampSteeringAngleCommand(Double.NaN, lock), 1.0E-12);
    }
}
