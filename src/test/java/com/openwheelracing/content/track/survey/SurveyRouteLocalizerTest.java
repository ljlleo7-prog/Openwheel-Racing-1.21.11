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

    @Test
    void globallyReacquiresAfterAnUntrackedIntervalInsteadOfStickingToOldWindow() {
        SurveyRouteModel route = recoveryRoute();
        SurveyRouteLocalizer.State state = new SurveyRouteLocalizer.State();
        SurveyRouteLocalizer.locate(route, new SurveyRouteModel.Point(5, 64, 0), 0.0, state);

        SurveyRouteLocalizer.Result lost = SurveyRouteLocalizer.locate(route,
            new SurveyRouteModel.Point(10_000, 64, 10_000), 0.0, state);
        SurveyRouteLocalizer.Result recovered = SurveyRouteLocalizer.locate(route,
            new SurveyRouteModel.Point(5, 64, 3), 0.0, state);

        assertEquals(SurveyRouteLocalizer.Status.UNTRACKED, lost.status());
        assertEquals(SurveyRouteLocalizer.Status.TRACKED, recovered.status());
        assertEquals(22, recovered.best().orElseThrow().segmentIndex());
    }

    private static SurveyRouteModel parallelRoute() {
        List<SurveyRouteModel.Node> nodes = List.of(
            node(0, 0, 0, 0), node(1, 40, 0, 40), node(2, 40, 4, 44), node(3, 0, 4, 84)
        );
        return new SurveyRouteModel(UUID.randomUUID(), UUID.randomUUID(), List.of(), nodes, 88, 4);
    }

    private static SurveyRouteModel recoveryRoute() {
        java.util.ArrayList<SurveyRouteModel.Node> nodes = new java.util.ArrayList<>();
        nodes.add(node(0, 0, 0, 0));
        nodes.add(node(1, 10, 0, 10));
        for (int index = 2; index <= 18; index++) {
            nodes.add(node(index, 100 + index * 10, 100, index * 10));
        }
        nodes.add(node(19, 300, 150, 190));
        nodes.add(node(20, 200, 200, 200));
        nodes.add(node(21, 0, 10, 210));
        nodes.add(node(22, 0, 3, 220));
        nodes.add(node(23, 10, 3, 230));
        nodes.add(node(24, 100, 200, 240));
        for (int index = 25; index <= 38; index++) {
            nodes.add(node(index, 100 + (index - 25) * 10, 300, index * 10));
        }
        nodes.add(node(39, 0, -100, 390));
        return new SurveyRouteModel(UUID.randomUUID(), UUID.randomUUID(), List.of(), nodes, 400, 10);
    }

    private static SurveyRouteModel.Node node(int index, double x, double z, double distance) {
        return new SurveyRouteModel.Node(index, new SurveyRouteModel.Point(x, 64, z), 0.0, distance);
    }
}
