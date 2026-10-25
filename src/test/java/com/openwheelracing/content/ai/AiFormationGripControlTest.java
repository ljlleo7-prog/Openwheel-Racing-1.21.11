package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiFormationGripControlTest {
    private static BasicAiGripModel.State grip() {
        return BasicAiGripModel.build(new BasicAiGripModel.Input(95, 88, 104, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1));
    }

    @Test
    void fullThrottleAndFullBrakeAreUsedOnSafeStraight() {
        var grip = grip();
        assertEquals(1.0f, AiFormationGripControl.command(45, 55, 0, grip, BasicAiNearbyAvoidance.Decision.NONE, 0, 0, true).throttle());
        var brakingGrip = BasicAiGripModel.build(new BasicAiGripModel.Input(95, 88, 104, 0, 0, 1, 1, 2, 1, 1, 1, 1, 1, 1));
        assertEquals(1.0f, AiFormationGripControl.command(55, 20, 0, brakingGrip, BasicAiNearbyAvoidance.Decision.NONE, 0, 0, true).brake());
    }

    @Test
    void fullLockIsBoundedByAvailableLateralGripAtSpeed() {
        var grip = grip();
        for (double speed : new double[] {5, 15, 30, 55}) {
            var command = AiFormationGripControl.command(speed, 55, 1, grip, BasicAiNearbyAvoidance.Decision.NONE, 0, 0, true);
            double lateral = speed * speed * Math.abs(Math.tan(command.steering() * Math.toRadians(34)) / 3.6);
            assertTrue(lateral <= grip.lateralAcceleration(speed) * 0.751);
            double longitudinal = command.throttle() * grip.driveAcceleration(speed);
            double utilization = Math.hypot(lateral / grip.lateralAcceleration(speed), longitudinal / grip.longitudinalAcceleration(speed));
            assertTrue(utilization <= 0.901, "utilization=" + utilization);
        }
    }

    @Test
    void brakingPreservesSteeringReserveAndAvoidanceOverridesHeating() {
        var grip = grip();
        var command = AiFormationGripControl.command(55, 20, 1, grip, BasicAiNearbyAvoidance.Decision.NONE, 0, 0, true);
        assertEquals(0, command.throttle());
        double lat = 55 * 55 * Math.abs(Math.tan(command.steering() * Math.toRadians(34)) / 3.6);
        double longitudinal = command.brake() * grip.brakeAcceleration(55);
        assertTrue(Math.hypot(lat / grip.lateralAcceleration(55), longitudinal / grip.longitudinalAcceleration(55)) <= 0.901);
        var threat = new BasicAiNearbyAvoidance.Decision(true, 0.5, 1, 1, 2);
        assertEquals(1, AiFormationGripControl.command(55, 55, 0, grip, threat, 0, 0, true).brake());
    }

    @Test
    void misalignmentOrMissingPavementPreventsAggressiveThrottle() {
        var grip = grip();
        assertTrue(AiFormationGripControl.command(25, 55, 0, grip, BasicAiNearbyAvoidance.Decision.NONE, 0.4, 0, true).throttle() <= 0.25);
        assertTrue(AiFormationGripControl.command(25, 55, 0, grip, BasicAiNearbyAvoidance.Decision.NONE, 0, 0, false).throttle() <= 0.25);
    }
}
