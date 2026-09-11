package com.openwheelracing.content.track.survey;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyRouteSpatialIndexTest {
    @Test
    void returnsOnlySegmentsNearQueriedCell() {
        SurveyRouteModel route = new SurveyRouteModel(UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of(
            node(0, 0, 0, 0), node(1, 8, 0, 8), node(2, 200, 0, 200), node(3, 208, 0, 208)
        ), 400, 8);
        SurveyRouteSpatialIndex index = new SurveyRouteSpatialIndex(route, 16.0);

        assertTrue(index.candidateSegments(4, 5).contains(0));
        assertFalse(index.candidateSegments(4, 5).contains(2));
        assertTrue(index.candidateSegments(204, 5).contains(2));
    }

    @Test
    void marginIncludesNeighboringRouteCell() {
        SurveyRouteModel route = new SurveyRouteModel(UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of(
            node(0, 0, 0, 0), node(1, 8, 0, 8)
        ), 16, 8);
        SurveyRouteSpatialIndex index = new SurveyRouteSpatialIndex(route, 16.0);

        assertTrue(index.candidateSegments(4, 15).contains(0));
    }

    private static SurveyRouteModel.Node node(int index, double x, double z, double distance) {
        return new SurveyRouteModel.Node(index, new SurveyRouteModel.Point(x, 64, z), 0.0, distance);
    }
}
