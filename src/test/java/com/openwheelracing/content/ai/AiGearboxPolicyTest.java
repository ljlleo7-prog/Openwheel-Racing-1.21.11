package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiGearboxPolicyTest {
    private final AiGearboxPolicy policy = new AiGearboxPolicy();

    @Test
    void preRevHoldsBeforeRelease() {
        AiGearboxPolicy.Decision decision = policy.decide(new AiGearboxPolicy.State(1, 8, 900, 13000, 1.0f, 0.0f, 4, 0, 900));
        assertEquals(AiGearboxPolicy.Action.PRE_REV, decision.action());
    }

    @Test
    void shiftsUpNearRedline() {
        AiGearboxPolicy.Decision decision = policy.decide(new AiGearboxPolicy.State(1, 8, 12000, 13000, 1.0f, 0.0f, 0, 0, 12000));
        assertEquals(AiGearboxPolicy.Action.SHIFT_UP, decision.action());
    }

    @Test
    void hysteresisShiftsDownOnlyAtLowRpm() {
        AiGearboxPolicy.Decision decision = policy.decide(new AiGearboxPolicy.State(3, 8, 8000, 13000, 0.0f, 0.4f, 0, 0, 8000));
        assertEquals(AiGearboxPolicy.Action.HOLD, decision.action());
        decision = policy.decide(new AiGearboxPolicy.State(3, 8, 6500, 13000, 0.0f, 0.4f, 0, 0, 8000));
        assertEquals(AiGearboxPolicy.Action.SHIFT_DOWN, decision.action());
    }

    @Test
    void cooldownPreventsImmediateSecondShift() {
        AiGearboxPolicy.Decision decision = policy.decide(new AiGearboxPolicy.State(2, 8, 12500, 13000, 1.0f, 0.0f, 0, 4, 12500));
        assertEquals(AiGearboxPolicy.Action.HOLD, decision.action());
        assertEquals(3, decision.cooldownTicks());
    }
}
