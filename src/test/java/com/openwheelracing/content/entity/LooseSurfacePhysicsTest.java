package com.openwheelracing.content.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LooseSurfacePhysicsTest {
    @Test
    void looseGroundIsRecoverableAtLowSpeedAndMoreResistiveAtSpeed() {
        double slow = LooseSurfacePhysics.resistanceCoefficient(true, false, false, 1.0);
        double fast = LooseSurfacePhysics.resistanceCoefficient(true, false, false, 30.0);
        assertTrue(slow + 0.035 < 0.10);
        assertTrue(fast > slow);
        assertEquals(fast, LooseSurfacePhysics.resistanceCoefficient(true, false, false, 100.0));
        assertTrue(LooseSurfacePhysics.resistanceCoefficient(false, true, false, 30.0) >
            LooseSurfacePhysics.resistanceCoefficient(false, true, false, 1.0));
        assertEquals(0.0, LooseSurfacePhysics.resistanceCoefficient(false, false, false, 30.0));
    }

    @Test
    void randomBumpsAreBoundedRepeatableAndVaryBetweenCarsAndCycles() {
        boolean up = false;
        boolean down = false;
        boolean differentCars = false;
        boolean differentCycles = false;
        int lastHop = -100;
        for (int tick = 0; tick < 240; tick++) {
            float pitch = LooseSurfacePhysics.bumpPitchDegrees(true, 20.0, tick, 42);
            assertEquals(pitch, LooseSurfacePhysics.bumpPitchDegrees(true, 20.0, tick, 42));
            assertTrue(Math.abs(pitch) <= 2.5f);
            up |= pitch > 0.0f;
            down |= pitch < 0.0f;
            differentCars |= pitch != LooseSurfacePhysics.bumpPitchDegrees(true, 20.0, tick, 99);
            differentCycles |= pitch != LooseSurfacePhysics.bumpPitchDegrees(true, 20.0, tick + 12, 42);
            double hop = LooseSurfacePhysics.bumpVelocity(true, 100.0, tick, 42);
            assertTrue(hop >= 0.0 && hop <= 0.05);
            if (hop > 0.0) {
                assertTrue(tick - lastHop >= 9);
                lastHop = tick;
            }
            assertEquals(0.0f, LooseSurfacePhysics.bumpPitchDegrees(false, 20.0, tick, 42));
            assertEquals(0.0f, LooseSurfacePhysics.bumpPitchDegrees(true, 0.0, tick, 42));
            assertEquals(0.0, LooseSurfacePhysics.bumpVelocity(false, 20.0, tick, 42));
            assertEquals(0.0, LooseSurfacePhysics.bumpVelocity(true, 0.0, tick, 42));
        }
        assertTrue(up && down && differentCars && differentCycles);
        assertTrue(lastHop > 200);
    }
}
