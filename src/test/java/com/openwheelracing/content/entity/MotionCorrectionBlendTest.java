package com.openwheelracing.content.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MotionCorrectionBlendTest {
    @Test
    void fadesContinuouslyToZeroWithoutOvershoot() {
        var blend = new MotionCorrectionBlend();
        blend.add(0, 2, -1, 4, 30);
        assertEquals(2, blend.sample(0, 0));
        assertEquals(1, blend.sample(150_000_000, 0), 1e-9);
        assertEquals(0, blend.sample(300_000_000, 0));
        assertEquals(0, blend.sample(900_000_000, 0));
    }

    @Test
    void overlappingYawUsesTheShortestEquivalentRotation() {
        var blend = new MotionCorrectionBlend();
        blend.add(0, 0, 0, 0, 170);
        blend.add(0, 0, 0, 0, 170);
        assertEquals(-20, blend.sample(0, 3), 1e-9);
    }

    @Test
    void overlappingCorrectionsPreserveTheCurrentlyRenderedPosition() {
        var blend = new MotionCorrectionBlend();
        blend.add(0, 2, 0, 0, 0);
        double before = blend.sample(100_000_000, 0);
        blend.add(100_000_000, -0.5, 0, 0, 0);
        assertEquals(before - 0.5, blend.sample(100_000_000, 0), 1e-9);
        blend.clear();
        assertEquals(0, blend.sample(100_000_000, 0));
    }
}
