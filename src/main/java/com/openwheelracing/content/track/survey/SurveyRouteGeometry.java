package com.openwheelracing.content.track.survey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SurveyRouteGeometry {
    private SurveyRouteGeometry() {}

    public static List<Candidate> candidates(SurveyRouteModel route, SurveyRouteModel.Point position, double headingRadians, int centerSegment, int window) {
        List<Candidate> candidates = new ArrayList<>();
        int count = route.nodes().size();
        if (count < 2) return List.of();
        if (centerSegment < 0 || window >= count / 2) {
            for (int index = 0; index < count; index++) candidates.add(project(route, position, headingRadians, index));
        } else {
            for (int offset = -window; offset <= window; offset++) candidates.add(project(route, position, headingRadians, Math.floorMod(centerSegment + offset, count)));
        }
        return candidates.stream().sorted(Comparator.comparingDouble(Candidate::score)).toList();
    }

    public static Candidate project(SurveyRouteModel route, SurveyRouteModel.Point position, double headingRadians, int segmentIndex) {
        int count = route.nodes().size();
        SurveyRouteModel.Node start = route.nodes().get(Math.floorMod(segmentIndex, count));
        SurveyRouteModel.Node end = route.nodes().get((Math.floorMod(segmentIndex, count) + 1) % count);
        SurveyRouteModel.Point a = start.position();
        SurveyRouteModel.Point b = end.position();
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double lengthSq = dx * dx + dz * dz;
        double t = lengthSq <= 1.0E-9 ? 0.0 : ((position.x() - a.x()) * dx + (position.z() - a.z()) * dz) / lengthSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double x = a.x() + dx * t;
        double y = a.y() + (b.y() - a.y()) * t;
        double z = a.z() + dz * t;
        double lateralX = position.x() - x;
        double lateralZ = position.z() - z;
        double horizontalDistance = Math.hypot(lateralX, lateralZ);
        double segmentLength = Math.sqrt(lengthSq);
        double signedLateral = segmentLength <= 1.0E-9 ? 0.0 : lateralX * (-dz / segmentLength) + lateralZ * (dx / segmentLength);
        double routeDistance = start.distanceAlongRoute() + segmentLength * t;
        if (routeDistance >= route.length()) routeDistance -= route.length();
        double segmentHeading = Math.atan2(dz, dx);
        double headingDelta = Math.abs(wrapRadians(headingRadians - segmentHeading));
        double verticalDelta = position.y() - y;
        double score = horizontalDistance * horizontalDistance + 0.25 * verticalDelta * verticalDelta + 9.0 * (1.0 - Math.cos(headingDelta));
        return new Candidate(Math.floorMod(segmentIndex, count), t, new SurveyRouteModel.Point(x, y, z), routeDistance, signedLateral, horizontalDistance, verticalDelta, headingDelta, score);
    }

    private static double wrapRadians(double radians) {
        double wrapped = radians;
        while (wrapped <= -Math.PI) wrapped += Math.PI * 2.0;
        while (wrapped > Math.PI) wrapped -= Math.PI * 2.0;
        return wrapped;
    }

    public record Candidate(int segmentIndex, double segmentT, SurveyRouteModel.Point projectedPosition, double distanceAlongRoute, double signedLateralDistance,
                            double horizontalDistance, double verticalDelta, double headingDeltaRadians, double score) {}
}
