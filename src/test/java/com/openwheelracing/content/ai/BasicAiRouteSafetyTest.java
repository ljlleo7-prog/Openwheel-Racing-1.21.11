package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAiRouteSafetyTest {
    @Test
    void keepsCarSafeInsideUsableCorridor() {
        BasicAiRouteSafety.Assessment assessment = BasicAiRouteSafety.assess(1.0, 4.0, 4.0, 1.0, 4.0, 4.0);
        assertEquals(BasicAiRouteSafety.State.SAFE, assessment.state());
        assertTrue(assessment.permitsFullThrottle());
    }

    @Test
    void recoversBeforeBodyLeavesTrack() {
        BasicAiRouteSafety.Assessment assessment = BasicAiRouteSafety.assess(3.0, 4.0, 4.0, 3.0, 4.0, 4.0);
        assertEquals(BasicAiRouteSafety.State.RECOVERING, assessment.state());
        assertEquals(-1, assessment.recoveryDirection());
    }

    @Test
    void brakesWhenPreviewIsBeyondRecoverableEnvelope() {
        BasicAiRouteSafety.Assessment assessment = BasicAiRouteSafety.assess(0.0, 4.0, 4.0, 6.0, 4.0, 4.0);
        assertEquals(BasicAiRouteSafety.State.UNSAFE, assessment.state());
        assertEquals(-1, assessment.recoveryDirection());
    }
}
