package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiGridApproachTest {
    private static final AiGridApproach.Target SLOT = new AiGridApproach.Target(0, 64, 0, 0);

    @Test
    void invalidGeometryAndUnboundedWaitsFailSafe() {
        var result = AiGridApproach.evaluate(SLOT, Double.NaN, 64, 0, 0, 0);
        assertTrue(result.missed());
        assertEquals(0.0, result.targetSpeed());
        assertEquals(1_200L, AiGridApproach.returnTimeoutTicks(Double.NaN));
        assertTrue(AiGridApproach.returnTimeoutTicks(1_000) > 1_200L);
        assertEquals(24_000L, AiGridApproach.returnTimeoutTicks(1e100));
    }

    @Test
    void finalApproachSlowsDownAsGridBoxApproaches() {
        var far = AiGridApproach.evaluate(SLOT, -20, 64, 0, 0, 8);
        var near = AiGridApproach.evaluate(SLOT, -2, 64, 0, 0, 3);
        assertTrue(far.close());
        assertTrue(far.targetSpeed() <= 8);
        assertTrue(near.targetSpeed() < far.targetSpeed());
        assertEquals(0, near.steering(), 1e-6);
    }

    @Test
    void readinessRequiresPositionHeadingHeightAndSpeed() {
        assertTrue(AiGridApproach.evaluate(SLOT, -0.3, 64.02, 0, 0, 0).ready());
        assertFalse(AiGridApproach.evaluate(SLOT, -0.3, 64, 0, 0, 2).ready());
        assertFalse(AiGridApproach.evaluate(SLOT, -0.3, 64, 0, Math.PI / 2, 0).ready());
        assertFalse(AiGridApproach.evaluate(SLOT, -0.3, 69, 0, 0, 0).ready());
        assertFalse(AiGridApproach.evaluate(SLOT, -2, 64, 0, 0, 0).ready());
    }

    @Test
    void overshootStopsRatherThanLoopsOrReverses() {
        var missed = AiGridApproach.evaluate(SLOT, 2, 64, 0, 0, 1);
        assertTrue(missed.missed());
        assertEquals(0, missed.targetSpeed());
        assertTrue(AiGridApproach.evaluate(SLOT, 0, 64, 0, Math.PI, 0).missed());
    }

    @Test
    void lateralApproachHasBoundedSteeringAndSpeed() {
        for (int z = -25; z <= 25; z++) {
            var approach = AiGridApproach.evaluate(SLOT, -5, 64, z, 0.3, 3);
            assertTrue(Math.abs(approach.steering()) <= 0.551);
            assertTrue(approach.targetSpeed() >= 0 && approach.targetSpeed() <= 8);
        }
    }
}
