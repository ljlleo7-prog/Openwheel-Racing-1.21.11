package com.openwheelracing.content.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class BoPMathTest {
    @Test void estimatedLapMovesInTheExpectedDirection() {
        assertEquals(100000, BoPMath.estimateLapMillis(100000, 0, 0));
        assertEquals(97000, BoPMath.estimateLapMillis(100000, 0, 10));
        assertEquals(102200, BoPMath.estimateLapMillis(100000, 10, 0));
    }

    @Test void profileValuesProvideExpectedMultipliers() {
        BoPProfileState.ProfileValues values = new BoPProfileState.ProfileValues(20, 10);
        assertEquals(1.20, values.weightMultiplier(), 1e-9);
        assertEquals(1.10, values.powerMultiplier(), 1e-9);
    }
}
