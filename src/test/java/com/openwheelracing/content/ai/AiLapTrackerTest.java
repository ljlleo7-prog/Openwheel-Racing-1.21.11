package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiLapTrackerTest {
    @Test
    void requiresForwardStartCoverageAndCleanSurface() {
        AiLapTracker tracker = new AiLapTracker(100.0);
        tracker.sampleProgress(0.0, true);
        tracker.forwardStartFinishCrossing();
        for (int distance = 1; distance <= 99; distance++) tracker.sampleProgress(distance, true);
        AiLapTracker.Outcome clean = tracker.finishAtForwardCrossing(5_000);
        assertTrue(clean.clean());
        assertTrue(clean.forwardCoverage() >= 0.97);
    }

    @Test
    void recoveryAndAnyCompleteOffTrackSampleInvalidateLap() {
        AiLapTracker tracker = new AiLapTracker(40.0);
        tracker.forwardStartFinishCrossing();
        tracker.sampleProgress(0, true);
        tracker.sampleProgress(10, true);
        tracker.sampleProgress(20, true);
        tracker.sampleProgress(30, true);
        tracker.sampleProgress(0, true);
        tracker.surfaceSample(false);
        tracker.fail(AiLapTracker.Failure.TELEPORT_OR_RECOVERY);
        AiLapTracker.Outcome outcome = tracker.finishAtForwardCrossing(4_000);
        assertFalse(outcome.clean());
        assertTrue(outcome.failures().contains(AiLapTracker.Failure.OFF_TRACK));
        assertTrue(outcome.failures().contains(AiLapTracker.Failure.TELEPORT_OR_RECOVERY));
    }
}
