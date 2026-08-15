package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAiTrafficPolicyTest {
    @Test
    void healthyMovingAiCanLead() {
        assertTrue(BasicAiTrafficPolicy.healthyMovingAi(true, true, false, 80.0, 20.0, 3.0));
    }

    @Test
    void stoppedDisabledReverseAndSidewaysCarsCannotLead() {
        assertFalse(BasicAiTrafficPolicy.healthyMovingAi(true, true, false, 1.0, 0.0, 0.0));
        assertFalse(BasicAiTrafficPolicy.healthyMovingAi(true, false, false, 80.0, 20.0, 0.0));
        assertFalse(BasicAiTrafficPolicy.healthyMovingAi(true, true, false, 80.0, -2.0, 0.0));
        assertFalse(BasicAiTrafficPolicy.healthyMovingAi(true, true, false, 80.0, 10.0, 6.0));
    }

    @Test
    void passengerControlledCarsCannotLead() {
        assertFalse(BasicAiTrafficPolicy.healthyMovingAi(true, true, true, 80.0, 20.0, 0.0));
    }

    @Test
    void onlyHealthyAiInitiatorEscapesUnhealthyAi() {
        assertTrue(BasicAiTrafficPolicy.shouldEscapeAiObstacle(true, true, false, false));
        assertFalse(BasicAiTrafficPolicy.shouldEscapeAiObstacle(false, true, false, false));
        assertFalse(BasicAiTrafficPolicy.shouldEscapeAiObstacle(true, true, true, false));
        assertFalse(BasicAiTrafficPolicy.shouldEscapeAiObstacle(true, true, false, true));
    }
}
