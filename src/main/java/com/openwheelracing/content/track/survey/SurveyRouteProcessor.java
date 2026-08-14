package com.openwheelracing.content.track.survey;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SurveyRouteProcessor {
    public static final double DEFAULT_SPACING = 2.0;
    public static final double MAX_CLOSURE_HORIZONTAL = 6.0;
    public static final double MAX_CLOSURE_VERTICAL = 3.0;
    private static final double DUPLICATE_DISTANCE = 0.35;
    private static final double MAX_SEGMENT_LENGTH = 12.0;
    private static final double MIN_ROUTE_LENGTH = 80.0;
    private static final int MIN_SAMPLES = 40;

    private SurveyRouteProcessor() {
    }

    public static Result build(UUID routeId, UUID trackId, List<SurveyRouteModel.Sample> input, double spacing) {
        if (input.size() > SurveyRouteModel.MAX_POINTS) return new Failure("too many samples");
        List<SurveyRouteModel.Sample> samples = filterDuplicates(input);
        if (samples.size() < MIN_SAMPLES) return new Failure("at least " + MIN_SAMPLES + " samples are required");
        SurveyRouteModel.Sample first = samples.getFirst();
        SurveyRouteModel.Sample last = samples.getLast();
        double closureHorizontal = horizontalDistance(first.position(), last.position());
        double closureVertical = Math.abs(first.position().y() - last.position().y());
        if (closureHorizontal > MAX_CLOSURE_HORIZONTAL || closureVertical > MAX_CLOSURE_VERTICAL) {
            return new Failure("route is not closed: horizontal=" + format(closureHorizontal) + " vertical=" + format(closureVertical));
        }
        double[] segmentLengths = new double[samples.size()];
        double length = 0.0;
        for (int i = 0; i < samples.size(); i++) {
            double segmentLength = horizontalDistance(samples.get(i).position(), samples.get((i + 1) % samples.size()).position());
            if (segmentLength > MAX_SEGMENT_LENGTH) return new Failure("sample jump exceeds " + format(MAX_SEGMENT_LENGTH) + " blocks");
            segmentLengths[i] = segmentLength;
            length += segmentLength;
        }
        if (length < MIN_ROUTE_LENGTH) return new Failure("route is shorter than " + format(MIN_ROUTE_LENGTH) + " blocks");
        double normalizedSpacing = Math.max(0.5, spacing);
        int nodeCount = (int) Math.floor(length / normalizedSpacing);
        if (nodeCount < 3 || nodeCount > SurveyRouteModel.MAX_POINTS) return new Failure("processed route node count is invalid: " + nodeCount);

        List<SurveyRouteModel.Node> nodes = new ArrayList<>(nodeCount);
        int segmentIndex = 0;
        double segmentStartDistance = 0.0;
        for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
            double targetDistance = nodeIndex * normalizedSpacing;
            while (segmentIndex < segmentLengths.length - 1 && targetDistance > segmentStartDistance + segmentLengths[segmentIndex]) {
                segmentStartDistance += segmentLengths[segmentIndex++];
            }
            SurveyRouteModel.Point start = samples.get(segmentIndex).position();
            SurveyRouteModel.Point end = samples.get((segmentIndex + 1) % samples.size()).position();
            double segmentLength = segmentLengths[segmentIndex];
            double t = segmentLength <= 1.0E-9 ? 0.0 : (targetDistance - segmentStartDistance) / segmentLength;
            SurveyRouteModel.Point point = interpolate(start, end, t);
            double heading = Math.atan2(end.z() - start.z(), end.x() - start.x());
            nodes.add(new SurveyRouteModel.Node(nodeIndex, point, heading, targetDistance));
        }
        return new Success(new SurveyRouteModel(routeId, trackId, samples, nodes, length, normalizedSpacing));
    }

    private static List<SurveyRouteModel.Sample> filterDuplicates(List<SurveyRouteModel.Sample> input) {
        List<SurveyRouteModel.Sample> filtered = new ArrayList<>();
        for (SurveyRouteModel.Sample sample : input) {
            if (filtered.isEmpty() || horizontalDistance(filtered.getLast().position(), sample.position()) >= DUPLICATE_DISTANCE) filtered.add(sample);
        }
        return List.copyOf(filtered);
    }

    private static SurveyRouteModel.Point interpolate(SurveyRouteModel.Point start, SurveyRouteModel.Point end, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return new SurveyRouteModel.Point(start.x() + (end.x() - start.x()) * clamped, start.y() + (end.y() - start.y()) * clamped, start.z() + (end.z() - start.z()) * clamped);
    }

    static double horizontalDistance(SurveyRouteModel.Point start, SurveyRouteModel.Point end) {
        return Math.hypot(end.x() - start.x(), end.z() - start.z());
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    public sealed interface Result permits Success, Failure {}
    public record Success(SurveyRouteModel route) implements Result {}
    public record Failure(String reason) implements Result {}
}
