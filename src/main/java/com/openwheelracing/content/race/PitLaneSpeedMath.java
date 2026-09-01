package com.openwheelracing.content.race;

import java.util.List;
import java.util.Optional;

public final class PitLaneSpeedMath {
    public static final double INSTANT_LIMIT_KMH = 85.0;
    public static final double AVERAGE_LIMIT_KMH = 80.0;
    public static final double RECOVERY_DISTANCE_METERS = 3.0;

    private PitLaneSpeedMath() {
    }

    public static Optional<Projection> project(List<Point> points, Point position) {
        Projection best = null;
        double distanceAlong = 0.0;
        for (int index = 1; index < points.size(); index++) {
            Point start = points.get(index - 1);
            Point end = points.get(index);
            double dx = end.x() - start.x();
            double dz = end.z() - start.z();
            double length = Math.hypot(dx, dz);
            if (length <= 1.0E-6) continue;
            double t = clamp(((position.x() - start.x()) * dx + (position.z() - start.z()) * dz) / (length * length), 0.0, 1.0);
            double px = start.x() + dx * t;
            double pz = start.z() + dz * t;
            double horizontal = Math.hypot(position.x() - px, position.z() - pz);
            double vertical = position.y() - (start.y() + (end.y() - start.y()) * t);
            Projection candidate = new Projection(index - 1, t, distanceAlong + length * t, horizontal, vertical, dx / length, dz / length);
            if (best == null || candidate.distanceSquared() < best.distanceSquared()) best = candidate;
            distanceAlong += length;
        }
        return Optional.ofNullable(best);
    }

    public static double projectedSpeedKmh(Projection projection, double movementX, double movementZ) {
        return Math.max(0.0, (movementX * projection.tangentX() + movementZ * projection.tangentZ()) * 72.0);
    }

    public static Optional<Double> intersectionDistance(List<Point> points, Point lineLeft, Point lineRight) {
        double distanceAlong = 0.0;
        for (int index = 1; index < points.size(); index++) {
            Point start = points.get(index - 1);
            Point end = points.get(index);
            double segmentX = end.x() - start.x();
            double segmentZ = end.z() - start.z();
            double lineX = lineRight.x() - lineLeft.x();
            double lineZ = lineRight.z() - lineLeft.z();
            double denominator = segmentX * lineZ - segmentZ * lineX;
            double segmentLength = Math.hypot(segmentX, segmentZ);
            if (Math.abs(denominator) > 1.0E-9) {
                double offsetX = lineLeft.x() - start.x();
                double offsetZ = lineLeft.z() - start.z();
                double segmentT = (offsetX * lineZ - offsetZ * lineX) / denominator;
                double lineT = (offsetX * segmentZ - offsetZ * segmentX) / denominator;
                if (segmentT >= 0.0 && segmentT <= 1.0 && lineT >= 0.0 && lineT <= 1.0) {
                    return Optional.of(distanceAlong + segmentLength * segmentT);
                }
            }
            distanceAlong += segmentLength;
        }
        return Optional.empty();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Point(double x, double y, double z) {
    }

    public record Projection(int segmentIndex, double segmentT, double distanceAlong, double horizontalDistance,
                             double verticalDelta, double tangentX, double tangentZ) {
        double distanceSquared() {
            return horizontalDistance * horizontalDistance + verticalDelta * verticalDelta;
        }
    }
}
