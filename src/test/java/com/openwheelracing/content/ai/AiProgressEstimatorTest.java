package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiProgressEstimatorTest {
    @Test
    void acceptsReachableMovementAcrossSeamAndRejectsJump() {
        AiProgressEstimator estimator = new AiProgressEstimator();
        assertEquals(AiProgressEstimator.State.TRACKED, estimator.update(98.0, true, 100.0, 10.0, 0.05).state());
        assertEquals(AiProgressEstimator.State.TRACKED, estimator.update(0.4, true, 100.0, 20.0, 0.05).state());
        assertEquals(AiProgressEstimator.State.PREDICTED, estimator.update(50.0, true, 100.0, 20.0, 0.05).state());
    }

    @Test
    void predictsForTenTicksThenRequiresControlledStop() {
        AiProgressEstimator estimator = new AiProgressEstimator();
        estimator.update(10.0, true, 100.0, 5.0, 0.05);
        for (int tick = 1; tick <= 10; tick++) {
            assertEquals(AiProgressEstimator.State.PREDICTED, estimator.update(Double.NaN, false, 100.0, 5.0, 0.05).state());
        }
        assertEquals(AiProgressEstimator.State.LOST, estimator.update(Double.NaN, false, 100.0, 5.0, 0.05).state());
    }
}
