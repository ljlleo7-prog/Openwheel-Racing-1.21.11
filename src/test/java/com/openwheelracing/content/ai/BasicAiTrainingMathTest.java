package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAiTrainingMathTest {
    @Test
    void validRaceLapUpdatesWithinBounds() {
        BasicAiTrainingMath.Update update = BasicAiTrainingMath.update(1.0, 1.0, 1.0, 3, 0, 0, 100_000, 95_000, true, true);
        assertTrue(update.accepted());
        assertTrue(update.targetScale() > 1.0);
        assertTrue(update.brakingScale() < 1.0);
    }

    @Test
    void invalidAndNonRaceLapDoesNotTrain() {
        assertFalse(BasicAiTrainingMath.update(1.0, 1.0, 1.0, 3, 0, 0, 100_000, 95_000, false, true).accepted());
        assertFalse(BasicAiTrainingMath.update(1.0, 1.0, 1.0, 3, 0, 0, 100_000, 95_000, true, false).accepted());
    }

    @Test
    void prefixPromotionPrefersDistanceBeforeSpeed() {
        OWRAiTrainingData.Prefix existing = new OWRAiTrainingData.Prefix(0.0, 80.0, 4.0, new int[21], new int[21], 35.0);
        OWRAiTrainingData.Prefix shorterFast = new OWRAiTrainingData.Prefix(0.0, 20.0, 4.0, new int[6], new int[6], 90.0);
        assertFalse(shorterFast.betterThan(existing));
        OWRAiTrainingData.Prefix fartherControlled = new OWRAiTrainingData.Prefix(0.0, 84.0, 4.0, new int[22], new int[22], 30.0);
        assertTrue(fartherControlled.betterThan(existing));
    }

    @Test
    void recoveryRequiresThreeSecondsBelowOneMeterPerSecond() {
        assertFalse(BasicAiTrainingMath.stalled(0.99, 59));
        assertTrue(BasicAiTrainingMath.stalled(0.99, 60));
        assertFalse(BasicAiTrainingMath.stalled(1.0, 60));
        assertFalse(BasicAiTrainingMath.stalled(20.0, 600));
    }
}
