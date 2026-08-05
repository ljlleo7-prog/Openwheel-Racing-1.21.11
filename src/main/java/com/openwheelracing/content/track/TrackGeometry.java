package com.openwheelracing.content.track;

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public final class TrackGeometry {
    private TrackGeometry() {
    }

    public static Optional<ProgressSample> sample(TrackDefinition definition, Vec3 position) {
        List<TrackDefinition.CenterlineNode> centerline = definition.centerline();
        if (centerline.size() < 2) {
            return Optional.empty();
        }

        ProgressSample best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (int i = 1; i < centerline.size(); i++) {
            TrackDefinition.CenterlineNode previous = centerline.get(i - 1);
            TrackDefinition.CenterlineNode next = centerline.get(i);
            SegmentProjection projection = projectOntoSegment(position, previous.position(), next.position());
            if (projection.distanceSq < bestDistanceSq) {
                double widthLeft = lerp(previous.widthLeft(), next.widthLeft(), projection.t);
                double widthRight = lerp(previous.widthRight(), next.widthRight(), projection.t);
                double distanceAlongTrack = lerp(previous.distanceAlongTrack(), next.distanceAlongTrack(), projection.t);
                double heading = segmentHeading(previous.position(), next.position());
                bestDistanceSq = projection.distanceSq;
                best = new ProgressSample(i - 1, projection.position, distanceAlongTrack, projection.signedLateralDistance, widthLeft, widthRight, heading);
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean isWrongWay(double carHeadingRadians, ProgressSample sample, double thresholdRadians) {
        return Math.abs(wrapRadians(carHeadingRadians - sample.headingRadians())) > thresholdRadians;
    }

    public static double headingFromDelta(Vec3 delta) {
        return Math.atan2(delta.z, delta.x);
    }

    public static Optional<LineCrossing> crossing(Vec3 previous, Vec3 current, TrackDefinition.StewardLine line) {
        return crossing(previous, current, line.left(), line.right());
    }

    public static Optional<LineCrossing> crossing(Vec3 previous, Vec3 current, TrackDefinition.Point3 left, TrackDefinition.Point3 right) {
        double movementX = current.x - previous.x;
        double movementZ = current.z - previous.z;
        double lineX = right.x() - left.x();
        double lineZ = right.z() - left.z();
        double denominator = cross2d(movementX, movementZ, lineX, lineZ);
        if (Math.abs(denominator) <= 1.0E-7) {
            return Optional.empty();
        }
        double startToLineX = left.x() - previous.x;
        double startToLineZ = left.z() - previous.z;
        double movementT = cross2d(startToLineX, startToLineZ, lineX, lineZ) / denominator;
        double lineT = cross2d(startToLineX, startToLineZ, movementX, movementZ) / denominator;
        if (movementT < -1.0E-6 || movementT > 1.0 + 1.0E-6 || lineT < -1.0E-6 || lineT > 1.0 + 1.0E-6) {
            return Optional.empty();
        }
        return Optional.of(new LineCrossing(clamp(movementT, 0.0, 1.0), clamp(lineT, 0.0, 1.0)));
    }

    private static double cross2d(double ax, double az, double bx, double bz) {
        return ax * bz - az * bx;
    }

    public static double wrapRadians(double radians) {
        double wrapped = radians;
        while (wrapped <= -Math.PI) {
            wrapped += Math.PI * 2.0;
        }
        while (wrapped > Math.PI) {
            wrapped -= Math.PI * 2.0;
        }
        return wrapped;
    }

    private static SegmentProjection projectOntoSegment(Vec3 position, TrackDefinition.Point3 start, TrackDefinition.Point3 end) {
        double sx = start.x();
        double sy = start.y();
        double sz = start.z();
        double dx = end.x() - sx;
        double dy = end.y() - sy;
        double dz = end.z() - sz;
        double horizontalLengthSq = dx * dx + dz * dz;
        double t = horizontalLengthSq <= 0.000001 ? 0.0 : clamp(((position.x - sx) * dx + (position.z - sz) * dz) / horizontalLengthSq, 0.0, 1.0);
        Vec3 projected = new Vec3(sx + dx * t, sy + dy * t, sz + dz * t);
        double lateralX = position.x - projected.x;
        double lateralZ = position.z - projected.z;
        double horizontalLength = Math.sqrt(horizontalLengthSq);
        double signedLateral = horizontalLength <= 0.000001 ? 0.0 : lateralX * (-dz / horizontalLength) + lateralZ * (dx / horizontalLength);
        double distanceSq = lateralX * lateralX + lateralZ * lateralZ;
        return new SegmentProjection(projected, t, signedLateral, distanceSq);
    }

    private static double segmentHeading(TrackDefinition.Point3 start, TrackDefinition.Point3 end) {
        return Math.atan2(end.z() - start.z(), end.x() - start.x());
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record SegmentProjection(Vec3 position, double t, double signedLateralDistance, double distanceSq) {
    }

    public record LineCrossing(double movementT, double lineT) {
    }

    public record ProgressSample(int segmentIndex, Vec3 projectedPosition, double distanceAlongTrack, double signedLateralDistance, double widthLeft, double widthRight, double headingRadians) {
        public boolean isWithinTrack(double tolerance) {
            return signedLateralDistance <= widthLeft + tolerance && signedLateralDistance >= -widthRight - tolerance;
        }

        public double progressScore(int completedLaps, double trackLength) {
            return Math.max(0, completedLaps) * Math.max(0.0, trackLength) + distanceAlongTrack;
        }
    }
}
