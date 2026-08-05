package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DriveInputSequenceTest {
    @Test
    void higherSequenceIsNewer() {
        assertTrue(VehiclePhysics.isNewerSequence(11, 10));
    }

    @Test
    void sameOrLowerSequenceIsNotNewer() {
        assertFalse(VehiclePhysics.isNewerSequence(10, 10));
        assertFalse(VehiclePhysics.isNewerSequence(9, 10));
    }

    @Test
    void signedDifferenceHandlesIntWrap() {
        assertTrue(VehiclePhysics.isNewerSequence(Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertFalse(VehiclePhysics.isNewerSequence(Integer.MAX_VALUE, Integer.MIN_VALUE));
    }

    @Test
    void twoTickGapDoesNotTriggerReconciliation() {
        assertFalse(VehiclePhysics.exceedsSequenceGap(12, 10, 2));
    }

    @Test
    void moreThanTwoTickGapTriggersReconciliation() {
        assertTrue(VehiclePhysics.exceedsSequenceGap(13, 10, 2));
    }
}
