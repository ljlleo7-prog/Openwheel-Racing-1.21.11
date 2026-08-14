package com.openwheelracing.content.track.survey;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurveyRouteLocalizerTest {
    @Test
    void marksSeparatedParallelSegmentsAmbiguous() {
        SurveyRouteModel route = parallelRoute();
        SurveyRouteLocalizer.Result result = SurveyRouteLocalizer.locate(route, new SurveyRouteModel.Point(20, 64, 2), Math.PI / 2.0, new SurveyRouteLocalizer.State());

        assertEquals(SurveyRouteLocalizer.Status.AMBIGUOUS, result.status());
    }

    @Test
    void marksFarCarUntracked() {
        SurveyRouteLocalizer.Result result = SurveyRouteLocalizer.locate(parallelRoute(), new SurveyRouteModel.Point(200, 64, 200), 0.0, new SurveyRouteLocalizer.State());

        assertEquals(SurveyRouteLocalizer.Status.UNTRACKED, result.status());
    }

    private static SurveyRouteModel parallelRoute() {
        List<SurveyRouteModel.Node> nodes = List.of(
            node(0, 0, 0, 0), node(1, 40, 0, 40), node(2, 40, 4, 44), node(3, 0, 4, 84)
        );
        return new SurveyRouteModel(UUID.randomUUID(), UUID.randomUUID(), List.of(), nodes, 88, 4);
    }

    private static SurveyRouteModel.Node node(int index, double x, double z, double distance) {
        return new SurveyRouteModel.Node(index, new SurveyRouteModel.Point(x, 64, z), 0.0, distance);
    }
}
