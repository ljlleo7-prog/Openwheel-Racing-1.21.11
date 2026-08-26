package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AxleGripBalanceTest {
    @Test
    void powertrainBrakingIsIncludedWhenTargetingEffectiveFrontBias() {
        VehiclePhysics.BrakeAxleRequests requests = VehiclePhysics.balanceBrakeRequests(
            30_000.0, 6_000.0, 0.64);

        double total = requests.front() + requests.rear();
        assertEquals(30_000.0, total, 1.0E-9,
            "Powertrain braking must replace rear friction braking, not add to pedal demand");
        assertEquals(0.64, requests.front() / total, 1.0E-12);
        assertEquals(6_000.0, requests.rearPowertrain(), 1.0E-12);
    }

    @Test
    void excessiveRegenerationIsClippedToRequestedRearBrakeShare() {
        VehiclePhysics.BrakeAxleRequests requests = VehiclePhysics.balanceBrakeRequests(
            10_000.0, 8_000.0, 0.64);

        assertEquals(6_400.0, requests.front(), 1.0E-12);
        assertEquals(3_600.0, requests.rear(), 1.0E-12);
        assertEquals(3_600.0, requests.rearPowertrain(), 1.0E-12);
    }

    @Test
    void liftOffPowertrainBrakingRemainsAvailableWithoutPedalDemand() {
        VehiclePhysics.BrakeAxleRequests requests = VehiclePhysics.balanceBrakeRequests(
            0.0, 5_000.0, 0.64);

        assertEquals(0.0, requests.front(), 1.0E-12);
        assertEquals(5_000.0, requests.rear(), 1.0E-12);
    }

    @Test
    void steeringUnderBrakingMovesTargetForwardOfDynamicLoadShare() {
        assertEquals(0.64, VehiclePhysics.dynamicFrontBrakeShare(
            6_000.0, 4_000.0, 1.0, 0.58), 1.0E-12);
        assertEquals(0.60, VehiclePhysics.dynamicFrontBrakeShare(
            6_000.0, 4_000.0, 0.0, 0.58), 1.0E-12);
    }

    @Test
    void softAssistEnvelopePermitsSmallCombinedSlip() {
        double limited = VehiclePhysics.softCombinedLongitudinalLimit(
            12_000.0, 6_000.0, 10_000.0, 10_000.0, 1.0, 1.06);

        assertTrue(limited > 8_000.0, "Assist should permit some slip beyond the unit friction circle");
        assertTrue(limited < 12_000.0, "Assist should still bound a grossly excessive request");
    }

    @Test
    void brakingReducesTurnInYawButPreservesSpinRecoveryMoment() {
        double baseline = VehiclePhysics.balancedHandlingYawMoment(
            12_000.0, -8_000.0, Math.toRadians(6.0), 40.0, 0.4, 3.6, 0.0, 0.0, 1.0, 1.0, 1.0);
        double braking = VehiclePhysics.balancedHandlingYawMoment(
            12_000.0, -8_000.0, Math.toRadians(6.0), 40.0, 0.4, 3.6, 1.0, 0.0, 1.0, 1.0, 1.0);
        double recovering = VehiclePhysics.balancedHandlingYawMoment(
            -12_000.0, -8_000.0, Math.toRadians(6.0), 40.0, 0.4, 3.6, 1.0, 0.0, 1.0, 1.0, 1.0);

        assertTrue(baseline > 4_000.0);
        assertEquals(880.0, braking, 1.0E-9);
        assertEquals(-20_000.0, recovering, 1.0E-9);
    }

    @Test
    void throttleRelievesCounterYawOnlyBelowTargetYawRate() {
        double belowTarget = VehiclePhysics.balancedHandlingYawMoment(
            10_000.0, -8_000.0, Math.toRadians(6.0), 55.0, 0.0, 3.6, 0.0, 1.0, 1.0, 1.0, 1.0);
        double coasting = VehiclePhysics.balancedHandlingYawMoment(
            10_000.0, -8_000.0, Math.toRadians(6.0), 55.0, 0.0, 3.6, 0.0, 0.0, 1.0, 1.0, 1.0);
        double aboveTarget = VehiclePhysics.balancedHandlingYawMoment(
            10_000.0, -8_000.0, Math.toRadians(6.0), 55.0, 2.0, 3.6, 0.0, 1.0, 1.0, 1.0, 1.0);

        assertEquals(6_400.0, belowTarget, 1.0E-9);
        assertEquals(3_600.0, coasting, 1.0E-9);
        assertEquals(2_000.0, aboveTarget, 1.0E-9);
    }

    @Test
    void extraCornerRotationFadesOutBeforeFlatOutCornerSpeed() {
        assertEquals(1.0, VehiclePhysics.lowSpeedCornerRotationBlend(30.0), 1.0E-12);
        assertEquals(0.5, VehiclePhysics.lowSpeedCornerRotationBlend(42.5), 1.0E-12);
        assertEquals(0.0, VehiclePhysics.lowSpeedCornerRotationBlend(55.0), 1.0E-12);
        assertEquals(0.0, VehiclePhysics.lowSpeedCornerRotationBlend(70.0), 1.0E-12);
    }

    @Test
    void lowSpeedRearCorneringStiffnessRemovesNeutralUndersteerWithoutChangingHighSpeedBalance() {
        assertEquals(0.70, VehiclePhysics.lowSpeedRearCorneringStiffnessScale(30.0, 1.0), 1.0E-12);
        assertEquals(0.85, VehiclePhysics.lowSpeedRearCorneringStiffnessScale(42.5, 1.0), 1.0E-12);
        assertEquals(1.0, VehiclePhysics.lowSpeedRearCorneringStiffnessScale(55.0, 1.0), 1.0E-12);
    }

    @Test
    void brakingDisablesNeutralAndThrottleYawAuthority() {
        double neutralBraking = VehiclePhysics.balancedHandlingYawMoment(
            0.0, -10_000.0, Math.toRadians(8.0), 25.0, 0.0, 3.6, 0.05, 0.0, 1.0, 1.0, 1.0);
        double throttleAndBrake = VehiclePhysics.balancedHandlingYawMoment(
            0.0, -10_000.0, Math.toRadians(8.0), 25.0, 0.0, 3.6, 0.05, 1.0, 1.0, 1.0, 1.0);

        assertEquals(-10_000.0, neutralBraking, 1.0E-9);
        assertEquals(-10_000.0, throttleAndBrake, 1.0E-9);
    }

    @Test
    void fullBrakeRetainsConsiderableTurnInAuthorityFromEitherAxle() {
        double rearTurnIn = VehiclePhysics.balancedHandlingYawMoment(
            0.0, 10_000.0, Math.toRadians(8.0), 35.0, 0.2, 3.6, 1.0, 0.0, 1.0, 1.0, 1.0);
        double bothAxlesTurnIn = VehiclePhysics.balancedHandlingYawMoment(
            10_000.0, 10_000.0, Math.toRadians(8.0), 35.0, 0.2, 3.6, 1.0, 0.0, 1.0, 1.0, 1.0);

        assertEquals(5_500.0, rearTurnIn, 1.0E-9);
        assertEquals(9_900.0, bothAxlesTurnIn, 1.0E-9);
    }

    @Test
    void brakingActivelyRecoversExcessYawEstablishedBeforePedalApplication() {
        double recovering = VehiclePhysics.balancedHandlingYawMoment(
            10_000.0, -10_000.0, Math.toRadians(8.0), 25.0, 2.0, 3.6, 1.0, 0.0, 1.0, 1.0, 1.0);

        assertTrue(recovering < -10_000.0);
    }

    @Test
    void brakingRecoveryOpposesWetStyleRotationAgainstSteeringDirection() {
        double recovering = VehiclePhysics.balancedHandlingYawMoment(
            10_000.0, -10_000.0, Math.toRadians(8.0), 25.0, -1.0, 3.6,
            1.0, 0.0, 1.0, 1.0, 1.0);

        assertTrue(recovering > 10_000.0);
    }

    @Test
    void zeroYawAdjustmentsRestoreRawMomentAndRearStiffness() {
        double rawMoment = VehiclePhysics.balancedHandlingYawMoment(
            12_000.0, -8_000.0, Math.toRadians(6.0), 25.0, 0.4, 3.6,
            1.0, 1.0, 0.0, 0.0, 0.0);

        assertEquals(4_000.0, rawMoment, 1.0E-9);
        assertEquals(1.0,
            VehiclePhysics.lowSpeedRearCorneringStiffnessScale(25.0, 0.0), 1.0E-12);
        assertEquals(0.55,
            VehiclePhysics.lowSpeedRearCorneringStiffnessScale(25.0, 1.5), 1.0E-12);
    }
}
