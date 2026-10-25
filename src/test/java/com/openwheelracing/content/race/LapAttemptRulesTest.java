package com.openwheelracing.content.race;

import com.openwheelracing.content.track.survey.SurveyRouteModel;
import com.openwheelracing.content.track.survey.SurveyRouteLocalizer;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LapAttemptRulesTest {
    @Test void ambiguousParallelRouteStillStartsTiming() {
        var nodes = List.of(
            new SurveyRouteModel.Node(0, new SurveyRouteModel.Point(0, 64, 0), 0, 0),
            new SurveyRouteModel.Node(1, new SurveyRouteModel.Point(40, 64, 0), 0, 40),
            new SurveyRouteModel.Node(2, new SurveyRouteModel.Point(40, 64, 4), 0, 44),
            new SurveyRouteModel.Node(3, new SurveyRouteModel.Point(0, 64, 4), 0, 84));
        var route = new SurveyRouteModel(UUID.randomUUID(), UUID.randomUUID(), List.of(), nodes, 88, 4);
        var position = new SurveyRouteModel.Point(20, 64, 2);
        assertEquals(SurveyRouteLocalizer.Status.AMBIGUOUS,
            SurveyRouteLocalizer.locate(route, position, Math.PI / 2,
                new SurveyRouteLocalizer.State()).status());
        assertTrue(LapAttemptRules.withinRouteZone(route, position));
    }

    @Test void startsAcrossFullDetectionEnvelopeRatherThanOnlyHighConfidenceBand() {
        assertTrue(LapAttemptRules.withinRouteZone(15, 0));
        assertTrue(LapAttemptRules.withinRouteZone(25, 5));
        assertTrue(LapAttemptRules.withinRouteZone(0, -5));
        assertFalse(LapAttemptRules.withinRouteZone(25.01, 0));
        assertFalse(LapAttemptRules.withinRouteZone(0, 5.01));
    }

    @Test void meaningfulMovementCanBeRecordedWithoutAConfidentRacingLineProfile() {
        assertFalse(LapAttemptRules.shouldDiscard(60_000, 1500));
        assertFalse(LapAttemptRules.shouldDiscard(200, 10));
        assertTrue(LapAttemptRules.shouldDiscard(199, 100));
        assertTrue(LapAttemptRules.shouldDiscard(60_000, 9.99));
    }
}
