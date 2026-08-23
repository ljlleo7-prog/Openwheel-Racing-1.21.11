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
}
