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
