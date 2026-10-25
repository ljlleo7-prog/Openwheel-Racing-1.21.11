package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiPavedFootprintTest {
    @Test
    void risingBahrainGridApproachUsesRoadTopInsteadOfCurrentCarFloor() {
        // Survey: the final 100 m rises from y=-58 to y=-57, with slab transitions.
        assertEquals(-57.5, AiPavedFootprint.clearanceFloor(new double[] {-58, -58, -57.5, -57.5}), 1e-6);
        assertEquals(-57, AiPavedFootprint.clearanceFloor(new double[] {-57.5, -57.5, -57, -57}), 1e-6);
        assertTrue(AiPavedFootprint.clearanceFloor(new double[] {-57, -57, -57}) + 0.2 > -57);
    }

    @Test
    void unsupportedOrCliffFootprintsRemainBlocked() {
        assertTrue(Double.isNaN(AiPavedFootprint.clearanceFloor(new double[] {-58, Double.NaN, -58})));
        assertTrue(Double.isNaN(AiPavedFootprint.clearanceFloor(new double[] {-58, -56})));
    }
}
