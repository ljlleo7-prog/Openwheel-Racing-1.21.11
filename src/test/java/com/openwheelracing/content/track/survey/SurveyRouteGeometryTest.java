package com.openwheelracing.content.track.survey;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyRouteGeometryTest {
    @Test
    void projectsAcrossClosingSeam() {
        SurveyRouteModel route = squareRoute();
        SurveyRouteGeometry.Candidate candidate = SurveyRouteGeometry.project(route, new SurveyRouteModel.Point(-1, 64, 5), -Math.PI / 2.0, 3);

        assertEquals(3, candidate.segmentIndex());
        assertEquals(35.0, candidate.distanceAlongRoute(), 0.01);
        assertEquals(1.0, candidate.horizontalDistance(), 0.01);
    }

    @Test
    void signedLateralDistanceDistinguishesSides() {
        SurveyRouteModel route = squareRoute();
        assertTrue(SurveyRouteGeometry.project(route, new SurveyRouteModel.Point(5, 64, 2), 0.0, 0).signedLateralDistance() > 0.0);
        assertTrue(SurveyRouteGeometry.project(route, new SurveyRouteModel.Point(5, 64, -2), 0.0, 0).signedLateralDistance() < 0.0);
    }

    private static SurveyRouteModel squareRoute() {
        List<SurveyRouteModel.Node> nodes = List.of(
            node(0, 0, 0, 0), node(1, 10, 0, 10), node(2, 10, 10, 20), node(3, 0, 10, 30)
        );
        return new SurveyRouteModel(UUID.randomUUID(), UUID.randomUUID(), List.of(), nodes, 40, 10);
    }

    private static SurveyRouteModel.Node node(int index, double x, double z, double distance) {
        return new SurveyRouteModel.Node(index, new SurveyRouteModel.Point(x, 64, z), 0.0, distance);
    }
}
