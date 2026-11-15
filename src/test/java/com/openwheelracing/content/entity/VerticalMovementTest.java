package com.openwheelracing.content.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerticalMovementTest {
    @Test
    void steppingUpDoesNotLaunchTheCarEvenBeforeGroundFlagUpdates() {
        assertEquals(0, VehiclePhysics.carriedVerticalMovement(0, 1, false, 1));
        assertEquals(0, VehiclePhysics.carriedVerticalMovement(-.04, .5, false, 1));
        double carried = 0;
        for (int i = 0; i < 100; i++) {
            carried = VehiclePhysics.carriedVerticalMovement(carried - .04, .5, false, 1);
            assertEquals(0, carried);
        }
    }

    @Test
    void genuineUpwardMomentumAndFreeFallRemainPossible() {
        assertEquals(.05, VehiclePhysics.carriedVerticalMovement(.05, .55, false, 1));
        assertEquals(.8, VehiclePhysics.carriedVerticalMovement(.8, .8, false, 1));
        assertEquals(-.3, VehiclePhysics.carriedVerticalMovement(-.3, -.3, false, 1));
        assertEquals(0, VehiclePhysics.carriedVerticalMovement(-.3, -.1, true, 1));
    }
}
