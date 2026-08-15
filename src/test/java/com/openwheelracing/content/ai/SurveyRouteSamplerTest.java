package com.openwheelracing.content.ai;

import com.openwheelracing.content.track.survey.SurveyRouteModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyRouteSamplerTest {
    private static final double EPSILON = 1.0E-6;

    @Test
    void wrapsDistanceAndDirectedDelta() {
        assertEquals(9.0, SurveyRouteSampler.wrapDistance(-1.0, 10.0), EPSILON);
        assertEquals(1.5, SurveyRouteSampler.wrapDistance(11.5, 10.0), EPSILON);
        assertEquals(2.0, SurveyRouteSampler.forwardDelta(9.0, 1.0, 10.0), EPSILON);
    }

    @Test
    void interpolatesNormalAndClosingSegments() {
        SurveyRouteModel route = squareRoute();
        assertEquals(5.0, SurveyRouteSampler.sample(route, 5.0).position().x(), EPSILON);
        SurveyRouteModel.Point seam = SurveyRouteSampler.sample(route, 35.0).position();
        assertEquals(0.0, seam.x(), EPSILON);
        assertEquals(5.0, seam.z(), EPSILON);
    }

    @Test
    void interpolatesHeadingAcrossPiSeam() {
        SurveyRouteModel route = new SurveyRouteModel(UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of(
            node(0, 0, 0, Math.toRadians(170), 0),
            node(1, 10, 0, Math.toRadians(-170), 10)
        ), 20, 10);
        double heading = SurveyRouteSampler.sample(route, 5.0).headingRadians();
        assertEquals(Math.PI, Math.abs(heading), 1.0E-6);
    }

    @Test
    void distinguishesStraightAndCurvedRoutes() {
        SurveyRouteModel straight = new SurveyRouteModel(UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of(
            node(0, 0, 0, 0, 0), node(1, 10, 0, 0, 10), node(2, 20, 0, 0, 20)
        ), 30, 10);
        assertEquals(0.0, SurveyRouteSampler.curvature(straight, 10, 4), EPSILON);
        assertTrue(SurveyRouteSampler.curvature(squareRoute(), 10, 8) > 0.05);
    }

    static SurveyRouteModel squareRoute() {
        return new SurveyRouteModel(UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of(
            node(0, 0, 0, 0, 0),
            node(1, 10, 0, Math.PI / 2.0, 10),
            node(2, 10, 10, Math.PI, 20),
            node(3, 0, 10, -Math.PI / 2.0, 30)
        ), 40, 10);
    }

    static SurveyRouteModel.Node node(int index, double x, double z, double heading, double distance) {
        return new SurveyRouteModel.Node(index, new SurveyRouteModel.Point(x, 64, z), heading, distance);
    }
}
