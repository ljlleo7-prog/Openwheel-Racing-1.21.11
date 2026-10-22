package com.openwheelracing.content.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LowSpeedContactStabilityTest {
    @Test
    void staticTyreImpulseDoesNotReverseContactSlip() {
        double mass = 200.0;
        double dt = 0.0125;
        for (double slip : new double[] {-0.01, 0.01, -0.1, 0.1}) {
            double force = VehiclePhysics.staticContactLateralForce(slip, 100_000.0, 3_000.0,
                mass * 9.81, dt);
            double after = slip + force / mass * dt;
            assertTrue(after * slip >= 0.0, "Contact impulse reversed slip: " + slip + " -> " + after);
            assertTrue(Math.abs(after) < Math.abs(slip));
        }
    }

    @Test
    void staticContactsOpposeSpinWithoutReversingIt() {
        double yaw = 0.05;
        double moment = 0.0;
        for (double x : new double[] {-0.8, 0.8}) {
            for (double z : new double[] {-1.65, 1.95}) {
                var patch = VehiclePhysics.wheelPatchVelocity(0.0, 0.0, yaw, x, z, 0.0);
                double lateral = VehiclePhysics.staticContactLateralForce(
                    patch.lateral(), 100_000.0, 3_000.0, 200.0 * 9.81, 0.0125);
                moment += VehiclePhysics.minecraftYawMoment(x, z, 0.0, lateral);
            }
        }
        double after = yaw + moment / 2_000.0 * 0.0125;
        assertTrue(after >= 0.0 && after < yaw);
    }

    @Test
    void launchSteeringRetainsCorrectDirectionInForwardAndReverse() {
        for (double speed : new double[] {-0.2, 0.2}) {
            for (double steering : new double[] {-0.2, 0.2}) {
                var patch = VehiclePhysics.wheelPatchVelocity(speed, 0.0, 0.0, 0.0, 1.95, steering);
                double lateral = VehiclePhysics.staticContactLateralForce(
                    patch.lateral(), 100_000.0, 3_000.0, 200.0 * 9.81, 0.0125);
                var force = VehiclePhysics.wheelForceToBody(0.0, lateral, steering);
                double moment = VehiclePhysics.minecraftYawMoment(0.0, 1.95,
                    force.longitudinal(), force.lateral());
                assertTrue(moment * speed * steering > 0.0);
            }
        }
        assertEquals(0.0, VehiclePhysics.staticContactLateralForce(
            0.0, 100_000.0, 3_000.0, 200.0 * 9.81, 0.0125), 1e-12);
    }

    @Test
    void blockedCarDoesNotAccumulateAlreadyAppliedYaw() {
        assertEquals(0.0, VehiclePhysics.contactYawDelta(0.02, 0.05, 0.0, true, 0.0), 1e-12);
        assertEquals(0.0, VehiclePhysics.contactYawDelta(-0.02, 0.05, 0.0, true, 0.0), 1e-12);
    }

    @Test
    void freeDrivingAndFastWallContactsRetainExistingYaw() {
        assertEquals(0.02, VehiclePhysics.contactYawDelta(0.02, 0.05, 0.05, false, 1.0));
        assertEquals(-0.02, VehiclePhysics.contactYawDelta(-0.02, 0.05, 0.05, false, 1.0));
        assertEquals(0.02, VehiclePhysics.contactYawDelta(0.02, 1.0, 0.5, true, 10.0));
    }
}
