package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAiSpacingTest {
    @Test
    void closeFollowerSlowsAndBrakes() {
        assertTrue(BasicAiCarController.applySpacing(20.0, 15.0, 8.0) < 15.0);
        assertTrue(BasicAiCarController.speedCommand(15.0, 15.0, 0.0f, 4.0).brake() >= 0.35f);
    }

    @Test
    void seamAdjacentLeaderIsAhead() {
        assertEquals(3.0, SurveyRouteSampler.forwardDelta(98.0, 1.0, 100.0), 1.0E-6);
    }

    @Test
    void carBehindHasLargeForwardDelta() {
        assertEquals(97.0, SurveyRouteSampler.forwardDelta(1.0, 98.0, 100.0), 1.0E-6);
    }
}
