package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WheelCoordinateConventionTest {
    @Test
    void positiveMinecraftYawMovesFrontContactTowardBodyRight() {
        VehiclePhysics.WheelPatchVelocity front = VehiclePhysics.wheelPatchVelocity(
            0.0, 0.0, 2.0, 0.0, 1.5, 0.0);

        assertEquals(0.0, front.longitudinal(), 1.0E-12);
        assertEquals(-3.0, front.lateral(), 1.0E-12);
    }

    @Test
    void positiveMinecraftYawMovesBodyLeftContactForward() {
        VehiclePhysics.WheelPatchVelocity left = VehiclePhysics.wheelPatchVelocity(
            0.0, 0.0, 2.0, 0.8, 0.0, 0.0);

        assertEquals(1.6, left.longitudinal(), 1.0E-12);
        assertEquals(0.0, left.lateral(), 1.0E-12);
    }

    @Test
    void positiveSteeringProducesRightwardForceAndPositiveYaw() {
        double steeringAngle = Math.toRadians(10.0);
        VehiclePhysics.WheelPatchVelocity velocity = VehiclePhysics.wheelPatchVelocity(
            30.0, 0.0, 0.0, 0.0, 1.5, steeringAngle);
        assertTrue(velocity.lateral() > 0.0,
            "Forward motion across a right-steered tyre must appear toward its body-left side");

        VehiclePhysics.PlanarForce bodyForce = VehiclePhysics.wheelForceToBody(
            0.0, -5_000.0, steeringAngle);
        assertTrue(bodyForce.lateral() < 0.0, "A right turn must push the chassis toward body-right");
        assertTrue(VehiclePhysics.minecraftYawMoment(
            0.0, 1.5, bodyForce.longitudinal(), bodyForce.lateral()) > 0.0,
            "A rightward force at the front axle must create positive Minecraft yaw");
    }

    @Test
    void positiveYawRotatesForceFreeVelocityTowardBodyLeftCoordinates() {
        VehiclePhysics.BodyAcceleration acceleration = VehiclePhysics.minecraftBodyAcceleration(
            30.0, 0.0, 0.5, 0.0, 0.0, 769.0);

        assertEquals(0.0, acceleration.longitudinal(), 1.0E-12);
        assertEquals(15.0, acceleration.lateral(), 1.0E-12);
    }

    @Test
    void allFourLateralTyreForcesOpposeAnEstablishedSpin() {
        double halfTrack = 0.815;
        double frontZ = 1.944;
        double rearZ = -1.656;
        double totalYawMoment = 0.0;

        for (double localX : new double[] {-halfTrack, halfTrack}) {
            for (double localZ : new double[] {frontZ, rearZ}) {
                VehiclePhysics.WheelPatchVelocity patch = VehiclePhysics.wheelPatchVelocity(
                    25.0, 0.0, 0.8, localX, localZ, 0.0);
                double opposingLateralForce = -Math.signum(patch.lateral()) * 5_000.0;
                double moment = VehiclePhysics.minecraftYawMoment(
                    localX, localZ, 0.0, opposingLateralForce);
                assertTrue(moment < 0.0, "Each contact patch must oppose positive yaw");
                totalYawMoment += moment;
            }
        }

        assertTrue(totalYawMoment < 0.0, "The four-tyre sum must damp rather than reinforce the spin");
    }
}
