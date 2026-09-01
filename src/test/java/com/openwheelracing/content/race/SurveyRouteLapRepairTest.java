package com.openwheelracing.content.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyRouteLapRepairTest {
    @Test
    void detectsPlausibleForwardWrapAndInterpolatesCrossingTime() {
        SurveyRouteLapRepair.Candidate candidate = SurveyRouteLapRepair.detect(
            998.0, 100.0, 3.0, 101.0, 1000.0, 995.0, 6.0
        ).orElseThrow();

        assertEquals(100.4, candidate.estimatedCrossingGameTime(), 1.0E-9);
        assertEquals(101.0, candidate.detectedGameTime(), 1.0E-9);
    }

    @Test
    void rejectsTeleportLikeWrap() {
        assertTrue(SurveyRouteLapRepair.detect(
            980.0, 100.0, 30.0, 101.0, 1000.0, 990.0, 8.0
        ).isEmpty());
    }

    @Test
    void rejectsWrapBeforeMostOfLapWasObserved() {
        assertTrue(SurveyRouteLapRepair.detect(
            998.0, 100.0, 3.0, 101.0, 1000.0, 200.0, 6.0
        ).isEmpty());
    }

    @Test
    void rejectsOrdinaryForwardProgress() {
        assertTrue(SurveyRouteLapRepair.detect(
            400.0, 100.0, 405.0, 101.0, 1000.0, 900.0, 6.0
        ).isEmpty());
    }

    @Test
    void rejectsWrapAcrossPitLaneLocalizationGap() {
        assertTrue(SurveyRouteLapRepair.detect(
            998.0, 100.0, 3.0, 110.0, 1000.0, 995.0, 6.0
        ).isEmpty());
    }
}
