package com.openwheelracing.content.track.survey;

import java.util.List;
import java.util.UUID;

public record SurveyRouteModel(UUID routeId, UUID trackId, List<Sample> rawSamples, List<Node> nodes, double length, double spacing) {
    public static final int MAX_POINTS = 8192;

    public SurveyRouteModel {
        rawSamples = List.copyOf(rawSamples);
        nodes = List.copyOf(nodes);
    }

    public record Point(double x, double y, double z) {
    }

    public record Sample(Point position, double headingRadians) {
    }

    public record Node(int index, Point position, double headingRadians, double distanceAlongRoute) {
    }
}
