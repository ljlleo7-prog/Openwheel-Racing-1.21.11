package com.openwheelracing.content.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BarrierScrapePhysicsTest {
    @Test
    void gentleNormalContactDoesNotPenalizeHighTangentialSpeed() {
        assertEquals(0, VehiclePhysics.barrierDamageSeverity(.12));
        assertEquals(0, VehiclePhysics.barrierDamageSeverity(.28));
        assertTrue(VehiclePhysics.blockSlideRetention(Math.hypot(5.5, .01), 5.5) > .999);
    }

    @Test
    void barrierDamageIsReducedButHeadOnCrashesStillHurt() {
        double normal = 1.5;
        double current = VehiclePhysics.barrierDamageSeverity(normal);
        assertTrue(current > 0);
        assertTrue(current < (normal - .28) * 14 * .75);
    }
}
