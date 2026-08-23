package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyboardGripSteeringLimitTest {
    @Test
    void highSpeedDigitalSteeringCannotDemandImplausibleLateralAcceleration() {
        double limit = VehiclePhysics.keyboardGripSteeringLimit(
            206.7 / 3.6, 3.60, 769.0, 9.81, 14_343.0, 2.15, 0.65);

        assertTrue(Math.toDegrees(limit) >= 2.3);
        assertTrue(Math.toDegrees(limit) <= 3.2);
    }

    @Test
    void lowSpeedDriverAuthorityRemainsLarge() {
        double limit = VehiclePhysics.keyboardGripSteeringLimit(
            50.0 / 3.6, 3.60, 769.0, 9.81, 900.0, 2.15, 0.65);

        assertTrue(Math.toDegrees(limit) > 14.0);
    }

    @Test
    void degradedGripReducesSafeHighSpeedRackAngle() {
        double dry = VehiclePhysics.keyboardGripSteeringLimit(
            180.0 / 3.6, 3.60, 769.0, 9.81, 10_000.0, 2.15, 0.70);
        double wet = VehiclePhysics.keyboardGripSteeringLimit(
            180.0 / 3.6, 3.60, 769.0, 9.81, 10_000.0, 2.15, 0.40);

        assertTrue(wet < dry);
    }
}
