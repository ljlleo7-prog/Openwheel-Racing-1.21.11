package com.openwheelracing.content.race;

import java.util.ArrayList;
import java.util.List;

/** Reconstructs missed lap boundaries only when the recorded survey progress supports them. */
public final class LapTelemetryLapSplitter {
    public static final int SAMPLE_INTERVAL_PENALTY_MILLIS = 250;
    private static final double MAX_RECONSTRUCTION_SPEED_METERS_PER_SECOND = 130.0;
    private static final double MAX_LINE_SAMPLE_DISTANCE_METERS = 80.0;

    private LapTelemetryLapSplitter() {
    }

    public static List<Split> split(LapTelemetryRecorder.Snapshot data, double startFinishRouteDistance,
                                    int minimumLapMillis) {
        int count = data.elapsedMillis().length;
        double routeLength = data.routeLength();
        if (count < 3 || !(routeLength > 0.0) || !Double.isFinite(routeLength)
            || data.lapMillis() <= 0 || data.coveredDistanceCm()[count - 1] < 0) {
            return List.of();
        }

        int[] route = data.routeDistanceCm();
        int[] covered = data.coveredDistanceCm();
        int[] elapsed = data.elapsedMillis();
        byte[] status = data.trackingStatus();
        double startLine = Double.isFinite(startFinishRouteDistance)
            ? wrap(startFinishRouteDistance, routeLength)
            : wrap(route[0] / 100.0, routeLength);
        double firstDistance = wrap(route[0] / 100.0, routeLength);
        double lastDistance = wrap(route[count - 1] / 100.0, routeLength);
        double initialOffset = forwardDistance(startLine, firstDistance, routeLength);
        double finalTail = forwardDistance(lastDistance, startLine, routeLength);
        if (circularDistance(startLine, firstDistance, routeLength) > MAX_LINE_SAMPLE_DISTANCE_METERS
            || circularDistance(lastDistance, startLine, routeLength) > MAX_LINE_SAMPLE_DISTANCE_METERS
            || Byte.toUnsignedInt(status[0]) > 1 || Byte.toUnsignedInt(status[count - 1]) > 1) {
            return List.of();
        }

        int trackedSamples = 0;
        for (int index = 0; index < count; index++) {
            if (Byte.toUnsignedInt(status[index]) <= 1) trackedSamples++;
            if (index > 0 && (covered[index] < covered[index - 1]
                || covered[index] - covered[index - 1] > 8_000)) {
                return List.of();
            }
        }
        // A few samples may be explicitly untracked during a brief localizer dropout; the
        // bounded monotonic distance and tracked seam samples still provide independent support.
        if (trackedSamples < Math.ceil(count * 0.80)) return List.of();

        double traveledMeters = initialOffset + covered[count - 1] / 100.0 + finalTail;
        int lapCount = (int) Math.round(traveledMeters / routeLength);
        if (lapCount < 2 || Math.abs(traveledMeters - lapCount * routeLength) > distanceTolerance(routeLength)
            || traveledMeters / (data.lapMillis() / 1000.0) > MAX_RECONSTRUCTION_SPEED_METERS_PER_SECOND) {
            return List.of();
        }

        List<Integer> boundaries = new ArrayList<>(lapCount + 1);
        boundaries.add(0);
        int previousIndex = 0;
        for (int lap = 1; lap < lapCount; lap++) {
            double targetCovered = lap * routeLength - initialOffset;
            int nearest = -1;
            double nearestLineDistance = Double.POSITIVE_INFINITY;
            double nearestProgressError = Double.POSITIVE_INFINITY;
            for (int index = previousIndex + 1; index < count - 1; index++) {
                if (Byte.toUnsignedInt(status[index]) > 1) continue;
                double lineDistance = circularDistance(startLine, route[index] / 100.0, routeLength);
                double progressError = Math.abs(covered[index] / 100.0 - targetCovered);
                if (lineDistance <= MAX_LINE_SAMPLE_DISTANCE_METERS && progressError <= MAX_LINE_SAMPLE_DISTANCE_METERS
                    && (lineDistance < nearestLineDistance
                        || lineDistance == nearestLineDistance && progressError < nearestProgressError)) {
                    nearest = index;
                    nearestLineDistance = lineDistance;
                    nearestProgressError = progressError;
                }
            }
            if (nearest < 0) return List.of();
            boundaries.add(nearest);
            previousIndex = nearest;
        }
        boundaries.add(count - 1);

        List<Split> splits = new ArrayList<>(lapCount);
        int previousElapsed = 0;
        for (int lap = 1; lap < boundaries.size(); lap++) {
            int endIndex = boundaries.get(lap);
            int endElapsed = lap == boundaries.size() - 1 ? data.lapMillis() : elapsed[endIndex];
            int reconstructedMillis = endElapsed - previousElapsed + SAMPLE_INTERVAL_PENALTY_MILLIS;
            if (reconstructedMillis <= minimumLapMillis) return List.of();
            splits.add(new Split(boundaries.get(lap - 1), endIndex, previousElapsed, endElapsed,
                reconstructedMillis));
            previousElapsed = endElapsed;
        }
        return List.copyOf(splits);
    }

    private static double distanceTolerance(double routeLength) {
        return Math.max(40.0, Math.min(100.0, routeLength * 0.02));
    }

    private static double forwardDistance(double from, double to, double length) {
        return wrap(to - from, length);
    }

    private static double circularDistance(double first, double second, double length) {
        double difference = Math.abs(first - second);
        return Math.min(difference, length - difference);
    }

    private static double wrap(double distance, double length) {
        double wrapped = distance % length;
        return wrapped < 0.0 ? wrapped + length : wrapped;
    }

    public record Split(int startSample, int endSample, int startElapsedMillis, int endElapsedMillis,
                        int lapMillis) {
    }
}
