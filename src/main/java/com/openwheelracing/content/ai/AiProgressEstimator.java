package com.openwheelracing.content.ai;

public final class AiProgressEstimator {
    public static final int MAX_PREDICTED_TICKS = 10;
    private double distance;
    private boolean initialized;
    private int degradedTicks;

    public Estimate update(double candidateDistance, boolean unambiguous, double routeLength,
                           double forwardSpeedMetersPerSecond, double tickSeconds) {
        if (!initialized) {
            if (!unambiguous || !Double.isFinite(candidateDistance)) return new Estimate(0.0, State.LOST, 0);
            distance = SurveyRouteSampler.wrapDistance(candidateDistance, routeLength);
            initialized = true;
            return new Estimate(distance, State.TRACKED, 0);
        }
        double maximumReach = Math.max(1.0, Math.max(0.0, forwardSpeedMetersPerSecond) * tickSeconds * 2.5 + 0.5);
        if (unambiguous && Double.isFinite(candidateDistance)) {
            double forward = SurveyRouteSampler.forwardDelta(distance, candidateDistance, routeLength);
            if (forward <= maximumReach || forward >= routeLength - 0.25) {
                distance = SurveyRouteSampler.wrapDistance(candidateDistance, routeLength);
                degradedTicks = 0;
                return new Estimate(distance, State.TRACKED, 0);
            }
        }
        degradedTicks++;
        if (degradedTicks <= MAX_PREDICTED_TICKS) {
            distance = SurveyRouteSampler.wrapDistance(distance + Math.max(0.0, forwardSpeedMetersPerSecond) * tickSeconds, routeLength);
            return new Estimate(distance, State.PREDICTED, degradedTicks);
        }
        return new Estimate(distance, State.LOST, degradedTicks);
    }

    public void reset() { initialized = false; degradedTicks = 0; distance = 0.0; }
    public record Estimate(double routeDistance, State state, int degradedTicks) {}
    public enum State { TRACKED, PREDICTED, LOST }
}
