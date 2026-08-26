package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void counterSteerRecoveryRequiresInputOppositeEstablishedYaw() {
        assertEquals(0.0, VehiclePhysics.counterSteerRecoveryBlend(1.0, 0.8), 1.0E-12);
        assertEquals(0.0, VehiclePhysics.counterSteerRecoveryBlend(0.0, -0.8), 1.0E-12);
        assertEquals(0.0, VehiclePhysics.counterSteerRecoveryBlend(-1.0, 0.02), 1.0E-12);
        assertEquals(1.0, VehiclePhysics.counterSteerRecoveryBlend(-1.0, 0.37), 1.0E-12);
        assertEquals(0.5, VehiclePhysics.counterSteerRecoveryBlend(-1.0, 0.195), 1.0E-12);
    }

    @Test
    void keyboardRackPermitsHairpinRadiusAtLowSpeed() {
        double wheelbase = 3.60;
        double lowLock = Math.toRadians(34.0);
        double highLock = Math.toRadians(2.45);

        double lockAt40 = VehiclePhysics.keyboardSpeedSteeringLock(
            40.0 / 3.6, lowLock, highLock, 1.0, 1.0, 1.0, 20.0, 0.72);
        double lockAt60 = VehiclePhysics.keyboardSpeedSteeringLock(
            60.0 / 3.6, lowLock, highLock, 1.0, 1.0, 1.0, 20.0, 0.72);

        assertEquals(22.8494587968, Math.toDegrees(lockAt40), 1.0E-9);
        assertEquals(17.4011236936, Math.toDegrees(lockAt60), 1.0E-9);
        assertTrue(VehiclePhysics.kinematicCornerRadius(wheelbase, lockAt40) < 9.0);
        assertTrue(VehiclePhysics.kinematicCornerRadius(wheelbase, lockAt60) < 12.0);
        assertEquals(5.3372194866,
            VehiclePhysics.kinematicCornerRadius(wheelbase, lowLock), 1.0E-9);
    }
}
