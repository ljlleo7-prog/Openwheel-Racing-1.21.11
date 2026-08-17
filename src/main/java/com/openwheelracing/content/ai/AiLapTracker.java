package com.openwheelracing.content.ai;

import java.util.EnumSet;

/** Tracks authoritative clean-lap eligibility independently of controller progress. */
public final class AiLapTracker {
    public static final double MIN_FORWARD_COVERAGE = 0.97;
    private final double routeLength;
    private double coveredDistance;
    private double previousDistance;
    private boolean initialized;
    private boolean armed;
    private final EnumSet<Failure> failures = EnumSet.noneOf(Failure.class);

    public AiLapTracker(double routeLength) {
        if (!(routeLength > 0.0)) throw new IllegalArgumentException("route length must be positive");
        this.routeLength = routeLength;
    }

    public void sampleProgress(double routeDistance, boolean unambiguous) {
        if (!unambiguous || !Double.isFinite(routeDistance)) return;
        routeDistance = SurveyRouteSampler.wrapDistance(routeDistance, routeLength);
        if (!initialized) {
            previousDistance = routeDistance;
            initialized = true;
            return;
        }
        double forward = SurveyRouteSampler.forwardDelta(previousDistance, routeDistance, routeLength);
        if (forward > 0.0 && forward <= Math.min(80.0, routeLength * 0.25)) coveredDistance += forward;
        previousDistance = routeDistance;
    }

    public void forwardStartFinishCrossing() {
        if (!armed) {
            armed = true;
            coveredDistance = 0.0;
            failures.clear();
        }
    }

    public Outcome finishAtForwardCrossing(int lapMillis) {
        if (!armed) return new Outcome(false, 0.0, EnumSet.of(Failure.NO_FORWARD_START), lapMillis);
        double coverage = Math.min(1.0, coveredDistance / routeLength);
        EnumSet<Failure> result = failures.clone();
        if (coverage < MIN_FORWARD_COVERAGE) result.add(Failure.INSUFFICIENT_COVERAGE);
        armed = true;
        coveredDistance = 0.0;
        failures.clear();
        return new Outcome(result.isEmpty(), coverage, result, lapMillis);
    }

    public void fail(Failure failure) { failures.add(failure); }
    public void surfaceSample(boolean completeFootprintOnTrack) { if (!completeFootprintOnTrack) fail(Failure.OFF_TRACK); }
    public void reverseGateCrossing() { fail(Failure.REVERSE_GATE); }

    public record Outcome(boolean clean, double forwardCoverage, EnumSet<Failure> failures, int lapMillis) {
        public Outcome { failures = failures.clone(); }
    }

    public enum Failure {
        NO_FORWARD_START, INSUFFICIENT_COVERAGE, OFF_TRACK, COLLISION_DAMAGE, DESTROYED, STALL,
        TELEPORT_OR_RECOVERY, LOCALIZATION_LOSS, REVERSE_GATE
    }
}
