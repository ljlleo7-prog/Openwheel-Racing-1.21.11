package com.openwheelracing.client.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AutoShiftPolicyTest {
    @Test
    void neutralRequiresDeliberateManualShift() {
        assertEquals(0, AutoShiftPolicy.decide(0, 8, 14000, 15000, 14000, 1.0f, 0.0f, 0).direction());
    }

    @Test
    void shiftsUpNearRedline() {
        assertEquals(1, AutoShiftPolicy.decide(3, 8, 14000, 15000, 16000, 1.0f, 0.0f, 0).direction());
    }

    @Test
    void downshiftRejectsAnOverRev() {
        assertEquals(0, AutoShiftPolicy.decide(4, 8, 7000, 15000, 14000, 0.0f, 1.0f, 0).direction());
        assertEquals(-1, AutoShiftPolicy.decide(4, 8, 7000, 15000, 12000, 0.0f, 1.0f, 0).direction());
    }

    @Test
    void cooldownPreventsRepeatedRequests() {
        AutoShiftPolicy.Decision decision = AutoShiftPolicy.decide(3, 8, 14000, 15000, 12000, 1.0f, 0.0f, 3);
        assertEquals(0, decision.direction());
        assertEquals(2, decision.cooldownTicks());
    }
}
