package com.openwheelracing.content.recovery;

import com.openwheelracing.content.recovery.CarRecoveryCollision.Bounds;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CarRecoveryCollisionTest {
    private static final Bounds WALL = new Bounds(1, 0, 0, 2, 4, 4);
    private static final Bounds STUCK = new Bounds(0, 1, 1, 1.4, 2, 2);

    @Test
    void partiallyEmbeddedCarCanPullOutOrSlideAlongTheWall() {
        assertTrue(CarRecoveryCollision.allows(STUCK, STUCK.move(-0.05, 0, 0), WALL));
        assertTrue(CarRecoveryCollision.allows(STUCK, STUCK.move(0, 0, 0.05), WALL));
    }

    @Test
    void embeddedCarCannotBePushedDeeperIntoTheWall() {
        assertFalse(CarRecoveryCollision.allows(STUCK, STUCK.move(0.05, 0, 0), WALL));
    }

    @Test
    void clearCarCannotEnterAWall() {
        Bounds clear = STUCK.move(-0.5, 0, 0);
        assertFalse(CarRecoveryCollision.allows(clear, clear.move(0.2, 0, 0), WALL));
    }

    @Test
    void escapingOneObstacleCannotIntroduceOverlapWithAnother() {
        Bounds otherWall = new Bounds(-1, 0, 0, 0, 4, 4);
        Bounds pulled = STUCK.move(-0.05, 0, 0);
        assertTrue(CarRecoveryCollision.allows(STUCK, pulled, WALL));
        assertFalse(CarRecoveryCollision.allows(STUCK, pulled, otherWall));
    }
}
