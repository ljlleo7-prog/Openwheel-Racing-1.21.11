package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AbsRearDecelerationTest {
    @Test
    void regenerativeBrakingCannotBypassAbsLimit() {
        double result = VehiclePhysics.absLimitedRearRequest(
            -15_000.0, -10_000.0, 8_000.0, 10_000.0, 10_000.0, 0.98);

        assertTrue(result > -5_800.0,
            "Negative ERS drive torque must be included in the ABS-limited rear deceleration request");
    }

    @Test
    void positiveDriveIsPreservedWhileBrakeContributionIsLimited() {
        double result = VehiclePhysics.absLimitedRearRequest(
            -12_000.0, 3_000.0, 8_000.0, 10_000.0, 10_000.0, 0.98);
        double availableBrake = 10_000.0 * Math.sqrt(0.98 * 0.98 - 0.8 * 0.8);

        assertEquals(3_000.0 - availableBrake, result, 1.0E-9);
    }

    @Test
    void lateralSaturationStillAllowsModestBrakeSlip() {
        double result = VehiclePhysics.absLimitedBrakeForce(
            -5_000.0, 10_000.0, 10_000.0, 10_000.0, 1.06);

        assertTrue(result < 0.0);
        assertTrue(result > -5_000.0);
    }


    @Test
    void enabledAbsCanHoldAnAggressiveEnvelopeInsideGrip() {
        double result = VehiclePhysics.absLimitedBrakeForce(
            -8_000.0, 8_000.0, 10_000.0, 10_000.0, 0.98);
        double demand = Math.hypot(result / 10_000.0, 0.8);

        assertEquals(0.98, demand, 1.0E-12);
    }
}
