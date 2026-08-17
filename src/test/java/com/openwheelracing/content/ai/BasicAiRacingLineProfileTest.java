package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAiRacingLineProfileTest {
    @Test
    void storesAndInterpolatesFullResolutionLine() {
        BasicAiRacingLineProfile profile = BasicAiRacingLineProfile.empty(2000.0, 2000).update(10.0, 1.2, 0.1);
        assertEquals(2000, profile.pointCount());
        assertTrue(profile.populatedPoints() > 0);
        assertTrue(profile.offset(10.0) > 0.0);
    }

    @Test
    void provenProfileIsCopiedWithoutNeighborSmoothing() {
        BasicAiRacingLineProfile profile = BasicAiRacingLineProfile.fromSamples(16.0, 4,
            new double[]{0.0, 1.0, 0.0, 0.0}, new double[]{0.0, 0.1, 0.0, 0.0});
        assertEquals(0.0, profile.offset(0.0), 0.01);
        assertEquals(1.0, profile.offset(4.0), 0.01);
        assertEquals(0.0, profile.offset(8.0), 0.01);
    }

    @Test
    void prefixTargetLeavesUnvalidatedSuffixOnSurveyLine() {
        OWRAiTrainingData.Prefix prefix = new OWRAiTrainingData.Prefix(0.0, 8.0, 4.0, new int[]{0, 100, 0}, new int[]{0, 100, 0}, 40.0);
        BasicAiRacingLineProfile profile = BasicAiRacingLineProfile.fromPrefix(40.0, 10, prefix);
        assertEquals(1.0, profile.offset(4.0), 0.01);
        assertEquals(0.0, profile.offset(20.0), 0.01);
    }

    @Test
    void clampsOffsetAndRejectsOversizedProfiles() {
        BasicAiRacingLineProfile profile = BasicAiRacingLineProfile.empty(1000.0, 1000).update(5.0, 99.0, 4.0);
        assertTrue(profile.offset(5.0) <= BasicAiRacingLineProfile.MAX_OFFSET_METERS);
        assertTrue(profile.headingResidual(5.0) <= 0.7);
    }
}
