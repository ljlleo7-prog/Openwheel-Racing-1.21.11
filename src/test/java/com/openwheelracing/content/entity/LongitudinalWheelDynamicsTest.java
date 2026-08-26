package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LongitudinalWheelDynamicsTest {
    @Test
    void freelyRollingWheelHasZeroSlip() {
        assertEquals(0.0, VehiclePhysics.kinematicLongitudinalSlip(30.0 / 0.33, 0.33, 30.0), 1.0E-12);
    }

    @Test
    void lockedWheelHasFullBrakingSlip() {
        assertEquals(-1.0, VehiclePhysics.kinematicLongitudinalSlip(0.0, 0.33, 30.0), 1.0E-12);
    }

    @Test
    void tractionControlStrengthBlendsOnlyPositiveDriveTorque() {
        assertEquals(10_000.0, VehiclePhysics.tractionControlledDriveRequest(10_000.0, 4_000.0, 0.0), 1.0E-12);
        assertEquals(7_000.0, VehiclePhysics.tractionControlledDriveRequest(10_000.0, 4_000.0, 0.5), 1.0E-12);
        assertEquals(4_000.0, VehiclePhysics.tractionControlledDriveRequest(10_000.0, 4_000.0, 1.0), 1.0E-12);
        assertEquals(-5_000.0, VehiclePhysics.tractionControlledDriveRequest(-5_000.0, 1_000.0, 1.0), 1.0E-12);
    }

    @Test
    void selectableTcStrengthBlendsItsForceEnvelopeInsteadOfBecomingAbsolute() {
        assertEquals(1.06, VehiclePhysics.tractionControlForceEnvelope(false, 0.98, 1.0), 1.0E-12);
        assertEquals(1.02, VehiclePhysics.tractionControlForceEnvelope(true, 0.98, 0.5), 1.0E-12);
        assertEquals(0.98, VehiclePhysics.tractionControlForceEnvelope(true, 0.98, 1.0), 1.0E-12);
    }

    @Test
    void defaultTcStillLeavesMildRearSlipAuthority() {
        double envelope = VehiclePhysics.tractionControlForceEnvelope(true, 1.02, 0.35);

        assertTrue(envelope > 1.04);
        assertTrue(envelope < VehiclePhysics.BASE_GRIP_ENVELOPE);
    }

    @Test
    void sidewaysCarRetainsPartialThrottleAuthority() {
        assertEquals(1.0, VehiclePhysics.offAxisDriveAuthority(30.0, 30.0), 1.0E-12);
        double sidewaysAuthority = VehiclePhysics.offAxisDriveAuthority(0.0, 30.0);
        assertTrue(sidewaysAuthority >= 0.30 && sidewaysAuthority < 1.0);
    }

    @Test
    void brakingTargetReducesWheelSpeedInEitherRollingDirection() {
        double forward = VehiclePhysics.brakingWheelAngularTarget(30.0, 0.33, 30.0, 0.20);
        double reverse = VehiclePhysics.brakingWheelAngularTarget(-30.0, 0.33, 30.0, 0.20);

        assertEquals(24.0 / 0.33, forward, 1.0E-12);
        assertEquals(-24.0 / 0.33, reverse, 1.0E-12);
        assertTrue(Math.abs(forward) < 30.0 / 0.33);
        assertTrue(Math.abs(reverse) < 30.0 / 0.33);
    }

    @Test
    void brakingTargetCannotDriveWheelThroughZero() {
        assertEquals(0.0, VehiclePhysics.brakingWheelAngularTarget(3.0, 0.33, 30.0, 0.50), 1.0E-12);
        assertEquals(0.0, VehiclePhysics.brakingWheelAngularTarget(-3.0, 0.33, 30.0, 0.50), 1.0E-12);
    }

    @Test
    void tractionControlSoftensContinuouslyAtLowSpeed() {
        assertEquals(1.30, VehiclePhysics.lowSpeedTractionEnvelope(8.0, 1.02), 1.0E-12);
        assertEquals(1.16, VehiclePhysics.lowSpeedTractionEnvelope(16.5, 1.02), 1.0E-12);
        assertEquals(1.02, VehiclePhysics.lowSpeedTractionEnvelope(25.0, 1.02), 1.0E-12);

        assertEquals(0.14, VehiclePhysics.lowSpeedTractionControlStrength(8.0, 0.35), 1.0E-12);
        assertEquals(0.245, VehiclePhysics.lowSpeedTractionControlStrength(16.5, 0.35), 1.0E-12);
        assertEquals(0.35, VehiclePhysics.lowSpeedTractionControlStrength(25.0, 0.35), 1.0E-12);
    }
}
