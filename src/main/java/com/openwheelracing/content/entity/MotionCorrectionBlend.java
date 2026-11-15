package com.openwheelracing.content.entity;

/** Render-only residual. The clock is supplied so animation is independent of render frequency. */
final class MotionCorrectionBlend {
    static final long DURATION_NANOS = 300_000_000L;
    private final double[] residual = new double[4];
    private long started;
    private boolean active;

    void add(long now, double x, double y, double z, double yaw) {
        double weight = weight(now);
        double[] delta = {x, y, z, yaw};
        for (int i = 0; i < 4; i++) residual[i] = residual[i] * weight + delta[i];
        residual[3] = Math.IEEEremainder(residual[3], 360.0);
        started = now;
        active = true;
    }

    double sample(long now, int component) { return residual[component] * weight(now); }

    void clear() { active = false; }

    private double weight(long now) {
        if (!active) return 0;
        double t = Math.max(0, Math.min(1, (now - started) / (double) DURATION_NANOS));
        return 1 - t * t * (3 - 2 * t);
    }
}
