package com.openwheelracing.content.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConservativeMotionSyncTest {
    @Test
    void nearbyDrivingNeverForcesMotionCorrections() {
        var policy = new ConservativeMotionSync();
        for (int tick = 0; tick < 600; tick++) assertFalse(policy.shouldCorrect(tick, true, 1.0));
    }

    @Test
    void aSingleLargeSampleOrUnconfirmedPredictionDoesNotCorrect() {
        var policy = new ConservativeMotionSync();
        assertFalse(policy.shouldCorrect(0, true, 100));
        for (int tick = 1; tick < 30; tick++) assertFalse(policy.shouldCorrect(tick, false, 100));
        for (int tick = 30; tick < 36; tick++) assertFalse(policy.shouldCorrect(tick, true, 100));
        assertTrue(policy.shouldCorrect(36, true, 100));
    }

    @Test
    void sustainedPositionDriftRespectsCorrectionCooldown() {
        var policy = new ConservativeMotionSync();
        for (int tick = 0; tick < 6; tick++) assertFalse(policy.shouldCorrect(tick, true, 9));
        assertTrue(policy.shouldCorrect(6, true, 9));
        for (int tick = 7; tick < 18; tick++) assertFalse(policy.shouldCorrect(tick, true, 9));
        assertTrue(policy.shouldCorrect(18, true, 9));
    }

    @Test
    void recoveredAlignmentAndMissingObservationsRestartTheGracePeriod() {
        var policy = new ConservativeMotionSync();
        for (int tick = 0; tick < 5; tick++) assertFalse(policy.shouldCorrect(tick, true, 9));
        assertFalse(policy.shouldCorrect(5, true, 0));
        assertFalse(policy.shouldCorrect(20, true, 9));
        assertFalse(policy.shouldCorrect(20, true, 9));
        for (int tick = 21; tick < 26; tick++) assertFalse(policy.shouldCorrect(tick, true, 9));
        assertTrue(policy.shouldCorrect(26, true, 9));
    }
}
