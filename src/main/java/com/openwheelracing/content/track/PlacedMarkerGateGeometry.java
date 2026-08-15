package com.openwheelracing.content.track;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class PlacedMarkerGateGeometry {
    private PlacedMarkerGateGeometry() {
    }

    public enum MarkerType {
        START_FINISH,
        CHECKPOINT
    }

    public enum Axis {
        X,
        Z
    }

    public record Marker(MarkerType type, Axis axis, int facingSign, int planeCoordinate, int lateralCoordinate, int y) {
        public static Marker from(MarkerType type, Axis axis, int facingSign, int planeCoordinate, int y, int lateralCoordinate) {
            return new Marker(type, axis, facingSign, planeCoordinate, lateralCoordinate, y);
        }

        public static Marker from(MarkerType type, Direction facing, int x, int y, int z) {
            Axis axis = facing.getAxis() == Direction.Axis.X ? Axis.X : Axis.Z;
            int plane = axis == Axis.X ? x : z;
            int lateral = axis == Axis.X ? z : x;
            int sign = axis == Axis.X ? facing.getStepX() : facing.getStepZ();
            return new Marker(type, axis, sign, plane, lateral, y);
        }
    }

    public record Gate(MarkerType type, Axis axis, int facingSign, int planeCoordinate, int y, int lateralStart, int lateralEnd) {
        public String key() {
            return type.name() + ":" + axis + ":" + facingSign + ":" + planeCoordinate + ":" + y + ":" + lateralStart + ":" + lateralEnd;
        }

        public double plane() {
            return planeCoordinate + 0.5;
        }

        public double lateralCenter() {
            return (lateralStart + lateralEnd + 1) * 0.5;
        }

        public double lateralDistance(double lateral) {
            return switch (axis) {
                case X -> lateral;
                case Z -> lateral;
            };
        }

        public double longitudinalDistance(double x, double z) {
            return axis == Axis.X ? x - plane() : z - plane();
        }

        public TrackDefinition.Point3 left(double expansion) {
            double start = lateralStart - expansion;
            return point(start);
        }

        public TrackDefinition.Point3 right(double expansion) {
            double end = lateralEnd + 1.0 + expansion;
            return point(end);
        }

        private TrackDefinition.Point3 point(double lateral) {
            return axis == Axis.X
                ? new TrackDefinition.Point3(plane(), y + 0.5, lateral)
                : new TrackDefinition.Point3(lateral, y + 0.5, plane());
        }

        public boolean overlapsVertical(double centerY, double halfHeight) {
            return centerY + halfHeight >= y && centerY - halfHeight <= y + 1.0;
        }
    }

    public record Crossing(Gate gate, TrackGeometry.LineCrossing crossing) {
    }

    public static List<Gate> merge(Collection<Marker> markers) {
        List<Marker> ordered = new ArrayList<>(markers);
        ordered.sort(Comparator
            .comparing(Marker::type)
            .thenComparing(Marker::axis)
            .thenComparingInt(Marker::facingSign)
            .thenComparingInt(Marker::planeCoordinate)
            .thenComparingInt(Marker::y)
            .thenComparingInt(Marker::lateralCoordinate));
        List<Gate> gates = new ArrayList<>();
        for (Marker marker : ordered) {
            if (!gates.isEmpty()) {
                Gate previous = gates.getLast();
                if (sameRun(previous, marker)) {
                    gates.set(gates.size() - 1, new Gate(previous.type(), previous.axis(), previous.facingSign(), previous.planeCoordinate(), previous.y(), previous.lateralStart(), marker.lateralCoordinate()));
                    continue;
                }
            }
            gates.add(new Gate(marker.type(), marker.axis(), marker.facingSign(), marker.planeCoordinate(), marker.y(), marker.lateralCoordinate(), marker.lateralCoordinate()));
        }
        return List.copyOf(gates);
    }

    public static Optional<Crossing> earliestCrossing(
        Vec3 previous,
        Vec3 current,
        double previousCenterY,
        double currentCenterY,
        double previousHalfHeight,
        double currentHalfHeight,
        Collection<Gate> gates,
        double lateralExpansion
    ) {
        Crossing best = null;
        for (Gate gate : gates) {
            double movementT = TrackGeometry.crossing(previous, current, gate.left(lateralExpansion), gate.right(lateralExpansion))
                .map(TrackGeometry.LineCrossing::movementT)
                .orElse(-1.0);
            if (movementT < 0.0) {
                continue;
            }
            double centerY = lerp(previousCenterY, currentCenterY, movementT);
            double halfHeight = lerp(previousHalfHeight, currentHalfHeight, movementT);
            if (!gate.overlapsVertical(centerY, halfHeight)) {
                continue;
            }
            TrackGeometry.LineCrossing crossing = TrackGeometry.crossing(previous, current, gate.left(lateralExpansion), gate.right(lateralExpansion)).orElseThrow();
            if (best == null || crossing.movementT() < best.crossing().movementT()) {
                best = new Crossing(gate, crossing);
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean sameRun(Gate previous, Marker marker) {
        return previous.type() == marker.type()
            && previous.axis() == marker.axis()
            && previous.facingSign() == marker.facingSign()
            && previous.planeCoordinate() == marker.planeCoordinate()
            && previous.y() == marker.y()
            && marker.lateralCoordinate() == previous.lateralEnd() + 1;
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }
}
