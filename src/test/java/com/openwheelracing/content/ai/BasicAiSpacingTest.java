package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAiSpacingTest {
    @Test
    void selfishCommandDoesNotQueueBehindGap() {
        BasicAiDriveCommand command = BasicAiCarController.speedCommand(15.0, 95.0, 0.0f, BasicAiNearbyAvoidance.Decision.NONE);
        assertEquals(1.0f, command.throttle());
        assertEquals(0.0f, command.brake());
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
