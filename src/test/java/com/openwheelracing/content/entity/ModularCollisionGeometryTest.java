package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModularCollisionGeometryTest {
    @Test
    void leftFrontRectangleIsFirstContactWhenApproachingOffsetTarget() {
        ModularCollisionGeometry.Rectangle leftFront = rectangle(-0.73, 1.52, 0.0, 0.22, 0.48);
        ModularCollisionGeometry.Rectangle rightFront = rectangle(0.73, 1.52, 0.0, 0.22, 0.48);
        ModularCollisionGeometry.Rectangle target = rectangle(-0.73, 3.0, 0.0, 0.35, 0.35);

        double leftTime = ModularCollisionGeometry.firstContactTime(leftFront, 0.0, 1.5, target);
        double rightTime = ModularCollisionGeometry.firstContactTime(rightFront, 0.0, 1.5, target);

        assertEquals(13.0 / 30.0, leftTime, 1.0E-9);
        assertTrue(Double.isNaN(rightTime));
    }

    @Test
    void rotatedContactDoesNotMirrorLeftAndRight() {
        double yaw = Math.toRadians(45.0);
        ModularCollisionGeometry.Rectangle leftFront = rectangle(-0.73, 1.52, yaw, 0.22, 0.48);
        ModularCollisionGeometry.Rectangle rightFront = rectangle(0.73, 1.52, yaw, 0.22, 0.48);
        double targetX = leftFront.centerX() - Math.sin(yaw) * 1.2;
        double targetZ = leftFront.centerZ() + Math.cos(yaw) * 1.2;
        ModularCollisionGeometry.Rectangle target = worldRectangle(targetX, targetZ, yaw, 0.30, 0.30);

        double movementX = -Math.sin(yaw) * 1.0;
        double movementZ = Math.cos(yaw) * 1.0;
        assertTrue(Double.isFinite(ModularCollisionGeometry.firstContactTime(leftFront, movementX, movementZ, target)));
        assertTrue(Double.isNaN(ModularCollisionGeometry.firstContactTime(rightFront, movementX, movementZ, target)));
    }

    @Test
    void overlappingRectangleReportsAnOutwardNormalThatAllowsEscape() {
        ModularCollisionGeometry.Rectangle carNose = worldRectangle(0.0, 0.85, 0.0, 0.30, 0.40);
        ModularCollisionGeometry.Rectangle barrier = worldRectangle(0.0, 1.0, 0.0, 0.50, 0.50);

        ModularCollisionGeometry.Contact contact = ModularCollisionGeometry.firstContact(
            carNose, 0.0, -0.25, barrier);

        assertEquals(0.0, contact.time(), 1.0E-9);
        assertTrue(contact.normalZ() < 0.0);
        assertTrue(-0.25 * contact.normalZ() > 0.0,
            "Movement out of an existing overlap must point along the outward normal");
    }

    @Test
    void sweptContactReportsNormalAgainstApproach() {
        ModularCollisionGeometry.Rectangle carNose = worldRectangle(0.0, 0.0, 0.0, 0.30, 0.40);
        ModularCollisionGeometry.Rectangle barrier = worldRectangle(0.0, 1.0, 0.0, 0.50, 0.50);

        ModularCollisionGeometry.Contact contact = ModularCollisionGeometry.firstContact(
            carNose, 0.0, 0.50, barrier);

        assertEquals(0.20, contact.time(), 1.0E-9);
        assertTrue(contact.normalZ() < 0.0);
        assertTrue(0.50 * contact.normalZ() < 0.0);
    }

    @Test
    void acceleratingParallelToNearbyBarrierNeverProducesAnApproachingContact() {
        for (double speed = 0.05; speed <= 5.0; speed += 0.05) {
            for (double clearance : new double[] {0.001, 0.02, 0.10, 0.34}) {
                var car = worldRectangle(0.0, 0.0, 0.0, 0.95, 2.0);
                var wall = worldRectangle(1.45 + clearance, 0.0, 0.0, 0.5, 20.0);
                assertTrue(ModularCollisionGeometry.firstContact(car, 0.0, speed, wall) == null,
                    "Nearby parallel walls must not stop acceleration");
            }
        }
    }

    @Test
    void rotatedCarCanPassWhenAxisAlignedBoundsOverlapBarrierBounds() {
        double yaw = Math.PI / 4.0;
        var car = worldRectangle(0.0, 0.0, yaw, 0.95, 2.0);
        // Same heading, separated sideways: their world AABBs overlap but rectangles do not.
        var wall = worldRectangle(Math.cos(yaw) * 1.6, Math.sin(yaw) * 1.6,
            yaw, 0.5, 20.0);
        for (double speed : new double[] {0.5, 2.0, 4.5}) {
            assertTrue(ModularCollisionGeometry.firstContact(car,
                -Math.sin(yaw) * speed, Math.cos(yaw) * speed, wall) == null);
        }
    }

    @Test
    void touchingWallAllowsParallelTravelAndEscapeButDetectsApproach() {
        var car = worldRectangle(0.0, 0.0, 0.0, 0.95, 2.0);
        var wall = worldRectangle(1.45, 0.0, 0.0, 0.5, 20.0);
        for (double lateral : new double[] {-0.1, 0.0, 0.1}) {
            var contact = ModularCollisionGeometry.firstContact(car, lateral, 4.0, wall);
            assertTrue(contact != null);
            double approach = lateral * contact.normalX() + 4.0 * contact.normalZ();
            assertEquals(lateral > 0.0, approach < -1.0E-8);
        }
    }

    private static ModularCollisionGeometry.Rectangle rectangle(
            double localX, double localZ, double yaw, double halfWidth, double halfLength) {
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = forwardZ;
        double rightZ = -forwardX;
        return new ModularCollisionGeometry.Rectangle(
            rightX * localX + forwardX * localZ,
            rightZ * localX + forwardZ * localZ,
            rightX, rightZ, forwardX, forwardZ, halfWidth, halfLength);
    }

    private static ModularCollisionGeometry.Rectangle worldRectangle(
            double centerX, double centerZ, double yaw, double halfWidth, double halfLength) {
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        return new ModularCollisionGeometry.Rectangle(centerX, centerZ,
            forwardZ, -forwardX, forwardX, forwardZ, halfWidth, halfLength);
    }
}
