package com.openwheelracing.content.track;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class PlacedMarkerGateMath {
    private PlacedMarkerGateMath() {
    }

    public enum Type {
        START_FINISH,
        CHECKPOINT
    }

    public enum Axis {
        X,
        Z
    }

    public record Marker(Type type, Axis axis, int facingSign, int plane, int lateral, int y) {
    }

    public record Gate(Type type, Axis axis, int facingSign, int plane, int y, int lateralStart, int lateralEnd) {
        public String key() {
            return type + ":" + axis + ":" + facingSign + ":" + plane + ":" + y + ":" + lateralStart + ":" + lateralEnd;
        }

        public boolean overlapsVertical(double centerY, double halfHeight) {
            return centerY + halfHeight >= y && centerY - halfHeight <= y + 1.0;
        }
    }

    public record Crossing(Gate gate, double movementT, double gateT) {
    }

    public static List<Gate> merge(Collection<Marker> markers) {
        List<Marker> ordered = new ArrayList<>(markers);
        ordered.sort(Comparator.comparing(Marker::type).thenComparing(Marker::axis).thenComparingInt(Marker::facingSign)
            .thenComparingInt(Marker::plane).thenComparingInt(Marker::y).thenComparingInt(Marker::lateral));
        List<Gate> gates = new ArrayList<>();
        for (Marker marker : ordered) {
            if (!gates.isEmpty()) {
                Gate previous = gates.getLast();
                if (previous.type() == marker.type() && previous.axis() == marker.axis() && previous.facingSign() == marker.facingSign()
                    && previous.plane() == marker.plane() && previous.y() == marker.y() && marker.lateral() == previous.lateralEnd() + 1) {
                    gates.set(gates.size() - 1, new Gate(previous.type(), previous.axis(), previous.facingSign(), previous.plane(), previous.y(), previous.lateralStart(), marker.lateral()));
                    continue;
                }
            }
            gates.add(new Gate(marker.type(), marker.axis(), marker.facingSign(), marker.plane(), marker.y(), marker.lateral(), marker.lateral()));
        }
        return List.copyOf(gates);
    }

    public static Optional<Crossing> earliestCrossing(double previousX, double previousZ, double currentX, double currentZ,
                                                       double previousY, double currentY, double previousHalfHeight, double currentHalfHeight,
                                                       Collection<Gate> gates, double lateralExpansion) {
        Crossing best = null;
        for (Gate gate : gates) {
            double left = gate.lateralStart() - lateralExpansion;
            double right = gate.lateralEnd() + 1.0 + lateralExpansion;
            double lineStart = gate.axis() == Axis.X ? gate.plane() + 0.5 : left;
            double lineStartOther = gate.axis() == Axis.X ? left : gate.plane() + 0.5;
            double lineEnd = gate.axis() == Axis.X ? gate.plane() + 0.5 : right;
            double lineEndOther = gate.axis() == Axis.X ? right : gate.plane() + 0.5;
            Optional<double[]> crossing = crossing(previousX, previousZ, currentX, currentZ, lineStart, lineStartOther, lineEnd, lineEndOther);
            if (crossing.isEmpty()) {
                continue;
            }
            double movementT = crossing.get()[0];
            double centerY = previousY + (currentY - previousY) * movementT;
            double halfHeight = previousHalfHeight + (currentHalfHeight - previousHalfHeight) * movementT;
            if (!gate.overlapsVertical(centerY, halfHeight)) {
                continue;
            }
            if (best == null || movementT < best.movementT()) {
                best = new Crossing(gate, movementT, crossing.get()[1]);
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<double[]> crossing(double previousX, double previousZ, double currentX, double currentZ,
                                                double leftX, double leftZ, double rightX, double rightZ) {
        double movementX = currentX - previousX;
        double movementZ = currentZ - previousZ;
        double lineX = rightX - leftX;
        double lineZ = rightZ - leftZ;
        double denominator = cross(movementX, movementZ, lineX, lineZ);
        if (Math.abs(denominator) <= 1.0E-9) {
            return Optional.empty();
        }
        double startX = leftX - previousX;
        double startZ = leftZ - previousZ;
        double movementT = cross(startX, startZ, lineX, lineZ) / denominator;
        double lineT = cross(startX, startZ, movementX, movementZ) / denominator;
        if (movementT < -1.0E-9 || movementT > 1.0 + 1.0E-9 || lineT < -1.0E-9 || lineT > 1.0 + 1.0E-9) {
            return Optional.empty();
        }
        return Optional.of(new double[] {Math.max(0.0, Math.min(1.0, movementT)), Math.max(0.0, Math.min(1.0, lineT))});
    }

    private static double cross(double ax, double az, double bx, double bz) {
        return ax * bz - az * bx;
    }
}
