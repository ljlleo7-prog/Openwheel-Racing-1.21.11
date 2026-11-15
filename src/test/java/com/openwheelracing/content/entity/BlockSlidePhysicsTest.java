package com.openwheelracing.content.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockSlidePhysicsTest {
    @Test
    void grazingScrapePreservesAlmostAllSurvivingSpeed() {
        double incoming = Math.hypot(4.0, 0.1);
        double retention = VehiclePhysics.blockSlideRetention(incoming, 4.0);
        assertTrue(retention > 0.995);
        assertTrue(retention <= 1.0);
    }

    @Test
    void obliqueCollisionFrictionScalesWithBlockedNormalSpeed() {
        double retention = VehiclePhysics.blockSlideRetention(5.0, 3.0);
        assertEquals(2.4, 3.0 * retention, 1.0E-9);
        assertTrue(retention < VehiclePhysics.blockSlideRetention(Math.hypot(3.0, 1.0), 3.0));
        assertEquals(0.0, VehiclePhysics.blockSlideRetention(5.0, 0.1));
    }

    @Test
    void stoppedHeadOnImpactStaysStoppedAndFreeTravelLosesNothing() {
        assertEquals(0.0, 0.0 * VehiclePhysics.blockSlideRetention(4.0, 0.0));
        assertEquals(1.0, VehiclePhysics.blockSlideRetention(4.0, 4.0));
        assertEquals(1.0, VehiclePhysics.blockSlideRetention(0.0, 0.0));
        assertEquals(1.0, VehiclePhysics.blockSlideRetention(4.0, 4.01));
    }
}
