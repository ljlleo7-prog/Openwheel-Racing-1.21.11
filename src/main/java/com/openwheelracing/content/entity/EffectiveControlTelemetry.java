package com.openwheelracing.content.entity;

/** Observes post-assist requests without changing the solver or counting drag as pedal braking. */
final class EffectiveControlTelemetry {
    private double throttleSum;
    private double brakeSum;
    private int samples;

    void reset() {
        throttleSum = 0.0;
        brakeSum = 0.0;
        samples = 0;
    }

    void sample(double throttle, double brake, double requestedDrive, double deliveredDrive,
                double requestedBrake, double deliveredBrake) {
        throttleSum += effective(throttle, requestedDrive, deliveredDrive);
        brakeSum += effective(brake, requestedBrake, deliveredBrake);
        samples++;
    }

    double throttle() { return samples == 0 ? 0.0 : throttleSum / samples; }
    double brake() { return samples == 0 ? 0.0 : brakeSum / samples; }

    private static double effective(double input, double requested, double delivered) {
        if (!Double.isFinite(input) || !Double.isFinite(requested) || !Double.isFinite(delivered)
                || requested <= 1.0E-9) return 0.0;
        return Math.clamp(input, 0.0, 1.0) * Math.clamp(delivered / requested, 0.0, 1.0);
    }
}
