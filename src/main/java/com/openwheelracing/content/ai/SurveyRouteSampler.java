package com.openwheelracing.content.ai;

import com.openwheelracing.content.track.TrackGeometry;
import com.openwheelracing.content.track.survey.SurveyRouteModel;

public final class SurveyRouteSampler {
    private SurveyRouteSampler() {
    }

    public static double wrapDistance(double distance, double routeLength) {
        if (!(routeLength > 0.0)) {
            return 0.0;
        }
        double wrapped = distance % routeLength;
        return wrapped < 0.0 ? wrapped + routeLength : wrapped;
    }

    public static double forwardDelta(double from, double to, double routeLength) {
        return wrapDistance(to - from, routeLength);
    }

    public static Sample sample(SurveyRouteModel route, double distance) {
        if (route.nodes().isEmpty()) {
            throw new IllegalArgumentException("route has no nodes");
        }
        if (route.nodes().size() == 1 || !(route.length() > 0.0)) {
            SurveyRouteModel.Node node = route.nodes().getFirst();
            return new Sample(node.position(), node.headingRadians(), 0);
        }
        double wrapped = wrapDistance(distance, route.length());
        int segment = findSegment(route, wrapped);
        SurveyRouteModel.Node start = route.nodes().get(segment);
        SurveyRouteModel.Node end = route.nodes().get((segment + 1) % route.nodes().size());
        double segmentStart = start.distanceAlongRoute();
        double segmentEnd = segment + 1 < route.nodes().size() ? end.distanceAlongRoute() : route.length();
        double segmentLength = Math.max(1.0E-9, segmentEnd - segmentStart);
        double t = Math.max(0.0, Math.min(1.0, (wrapped - segmentStart) / segmentLength));
        SurveyRouteModel.Point a = start.position();
        SurveyRouteModel.Point b = end.position();
        SurveyRouteModel.Point point = new SurveyRouteModel.Point(
            lerp(a.x(), b.x(), t),
            lerp(a.y(), b.y(), t),
            lerp(a.z(), b.z(), t)
        );
        return new Sample(point, interpolateAngle(start.headingRadians(), end.headingRadians(), t), segment);
    }

    public static double curvature(SurveyRouteModel route, double centerDistance, double window) {
        double sampleWindow = Math.max(0.25, window);
        double before = sample(route, centerDistance - sampleWindow * 0.5).headingRadians();
        double after = sample(route, centerDistance + sampleWindow * 0.5).headingRadians();
        return Math.abs(TrackGeometry.wrapRadians(after - before)) / sampleWindow;
    }

    private static int findSegment(SurveyRouteModel route, double distance) {
        int low = 0;
        int high = route.nodes().size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (route.nodes().get(middle).distanceAlongRoute() <= distance) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return Math.floorMod(high, route.nodes().size());
    }

    private static double interpolateAngle(double from, double to, double t) {
        return TrackGeometry.wrapRadians(from + TrackGeometry.wrapRadians(to - from) * t);
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    public record Sample(SurveyRouteModel.Point position, double headingRadians, int segmentIndex) {
    }
}
