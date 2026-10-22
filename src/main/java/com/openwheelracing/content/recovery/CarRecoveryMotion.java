package com.openwheelracing.content.recovery;

/** Bounded spring motion: no teleporting or stored release impulse. Units are blocks/tick. */
public final class CarRecoveryMotion {
    public static final double MAX_STEP = 0.25;
    public static final double LOWER_STEP = 0.08;

    private CarRecoveryMotion() {
    }

    public static double stepScale(double distance) {
        if (!Double.isFinite(distance) || distance <= 0.0) return 0.0;
        return Math.min(0.25, MAX_STEP / distance);
    }
}
