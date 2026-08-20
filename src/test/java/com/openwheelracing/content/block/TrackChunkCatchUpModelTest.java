package com.openwheelracing.content.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackChunkCatchUpModelTest {
    @Test
    void newlyEligibleBlockOnlyCountsTimeAfterItsThreshold() {
        double ticks = TrackChunkCatchUpModel.eligibleTicks(0.0, 1.0, 2_000L, 0, 0.75, true);
        assertEquals(500.0, ticks, 1.0E-9);
    }

    @Test
    void dryingCountsTimeAfterProgressFallsBelowThreshold() {
        double ticks = TrackChunkCatchUpModel.eligibleTicks(2.0, 1.0, 2_000L, 1, 0.25, false);
        assertEquals(500.0, ticks, 1.0E-9);
    }

    @Test
    void longerUnloadedIntervalRaisesButBoundsTransitionProbability() {
        double shortInterval = TrackChunkCatchUpModel.transitionProbability(0, true, false, true, 200.0);
        double longInterval = TrackChunkCatchUpModel.transitionProbability(0, true, false, true, 20_000.0);
        assertTrue(shortInterval > 0.0);
        assertTrue(longInterval > shortInterval);
        assertTrue(longInterval <= 1.0);
    }

    @Test
    void thunderWetsFasterThanNormalRain() {
        double normal = TrackChunkCatchUpModel.transitionProbability(1, true, false, true, 2_000.0);
        double thunder = TrackChunkCatchUpModel.transitionProbability(1, true, true, true, 2_000.0);
        assertTrue(thunder > normal);
    }
}
