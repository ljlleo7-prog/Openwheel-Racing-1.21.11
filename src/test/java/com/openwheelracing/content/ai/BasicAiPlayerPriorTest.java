package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAiPlayerPriorTest {
    @Test
    void playerLearningIsDisabled() {
        assertFalse(BasicAiPlayerPrior.enabled());
    }

    @Test
    void priorIsSmallAndFiltered() {
        double first = BasicAiPlayerPrior.blendSpeed(40.0, 60.0, 20, 0.0);
        double second = BasicAiPlayerPrior.blendSpeed(40.0, 60.0, 20, first - 40.0);
        assertTrue(first > 40.0);
        assertTrue(first < 60.0);
        assertTrue(second > first);
    }

    @Test
    void onlyRaceFlyingAndStartUsePrior() {
        assertEquals(1.0, BasicAiPlayerPrior.phaseWeight(BasicAiTrainingPhase.FLYING, BasicAiTrafficMode.RACE));
        assertEquals(0.35, BasicAiPlayerPrior.phaseWeight(BasicAiTrainingPhase.RACE_START, BasicAiTrafficMode.RACE));
        assertEquals(0.0, BasicAiPlayerPrior.phaseWeight(BasicAiTrainingPhase.OUTLAP, BasicAiTrafficMode.RACE));
        assertEquals(0.0, BasicAiPlayerPrior.phaseWeight(BasicAiTrainingPhase.FLYING, BasicAiTrafficMode.VSC));
    }
}
