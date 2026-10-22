package com.openwheelracing.content.recovery;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CarRecoveryMotionTest {
    @Test
    void largeTargetChangesNeverTeleportTheCar() {
        for (double distance : new double[] {1, 4.5, 12, 100, 1e100}) {
            assertTrue(distance * CarRecoveryMotion.stepScale(distance) <= CarRecoveryMotion.MAX_STEP + 1e-12);
        }
    }

    @Test
    void approachConvergesWithoutOvershootingOrOscillating() {
        double remaining = 6;
        for (int tick = 0; tick < 100; tick++) {
            double next = remaining * (1 - CarRecoveryMotion.stepScale(remaining));
            assertTrue(next >= 0 && next < remaining);
            remaining = next;
        }
        assertTrue(remaining < 1e-8);
    }

    @Test
    void invalidTargetsCannotInjectMotion() {
        for (double distance : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(0, CarRecoveryMotion.stepScale(distance));
        }
    }
}
