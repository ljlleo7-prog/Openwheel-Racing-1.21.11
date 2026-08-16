package com.openwheelracing.content.ai;

import java.util.Comparator;
import java.util.List;

public final class BasicAiNearbyAvoidance {
    public static final double MAX_FORWARD_DISTANCE = 14.0;
    public static final double MAX_REAR_DISTANCE = 4.0;
    public static final double MAX_LATERAL_DISTANCE = 3.4;
    public static final double LONGITUDINAL_CLEARANCE = 6.4;
    public static final double LATERAL_CLEARANCE = 2.6;
    public static final double HORIZON_SECONDS = 1.25;

    private BasicAiNearbyAvoidance() {
    }

    public static Decision choose(Car subject, List<Car> candidates) {
        return candidates.stream()
            .filter(candidate -> candidate.id() != subject.id())
            .map(candidate -> assess(subject, candidate))
            .filter(Decision::threat)
            .max(Comparator.comparingDouble(Decision::severity))
            .orElse(Decision.NONE);
    }

    private static Decision assess(Car subject, Car target) {
        double dx = target.x() - subject.x();
        double dz = target.z() - subject.z();
        double forwardX = Math.cos(subject.heading());
        double forwardZ = Math.sin(subject.heading());
        double rightX = forwardZ;
        double rightZ = -forwardX;
        double longitudinal = dx * forwardX + dz * forwardZ;
        double lateral = dx * rightX + dz * rightZ;
        if (longitudinal < -MAX_REAR_DISTANCE || longitudinal > MAX_FORWARD_DISTANCE || Math.abs(lateral) > MAX_LATERAL_DISTANCE) {
            return Decision.NONE;
        }
        double relativeLongitudinal = (target.velocityX() - subject.velocityX()) * forwardX
            + (target.velocityZ() - subject.velocityZ()) * forwardZ;
        double relativeLateral = (target.velocityX() - subject.velocityX()) * rightX
            + (target.velocityZ() - subject.velocityZ()) * rightZ;
        double predictedLongitudinal = longitudinal + relativeLongitudinal * HORIZON_SECONDS;
        double predictedLateral = lateral + relativeLateral * HORIZON_SECONDS;
        boolean currentOverlap = Math.abs(longitudinal) <= LONGITUDINAL_CLEARANCE && Math.abs(lateral) <= LATERAL_CLEARANCE;
        boolean predictedOverlap = Math.abs(predictedLongitudinal) <= LONGITUDINAL_CLEARANCE
            && Math.abs(predictedLateral) <= LATERAL_CLEARANCE;
        if (!currentOverlap && !predictedOverlap) {
            return Decision.NONE;
        }
        int preferredSide;
        if (Math.abs(lateral) > 0.35) {
            preferredSide = lateral > 0.0 ? -1 : 1;
        } else {
            preferredSide = subject.id() < target.id() ? 1 : -1;
        }
        double longitudinalClearance = Math.max(0.0, Math.abs(predictedLongitudinal) - LONGITUDINAL_CLEARANCE);
        double severity = currentOverlap ? 2.0 : 1.0 / Math.max(0.1, longitudinalClearance + Math.abs(predictedLateral) * 0.25);
        double brake = currentOverlap ? 1.0 : clamp(0.35 + severity * 0.08, 0.35, 1.0);
        return new Decision(true, preferredSide * 0.75, brake, preferredSide, severity);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Car(int id, double x, double z, double heading, double velocityX, double velocityZ) {
    }

    public record Decision(boolean threat, double steeringBias, double brake, int preferredSide, double severity) {
        public static final Decision NONE = new Decision(false, 0.0, 0.0, 0, 0.0);
    }
}
