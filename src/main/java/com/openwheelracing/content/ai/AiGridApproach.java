package com.openwheelracing.content.ai;

/** Final grid-box alignment; AiGridNavigator replans an overshot or misaligned approach. */
public final class AiGridApproach {
    public static final double FINAL_APPROACH_METERS = 30.0;
    public static final double POSITION_TOLERANCE_METERS = 0.8;
    public static final double MAX_READY_SPEED_MPS = 0.4;
    public static final long FINAL_APPROACH_TIMEOUT_TICKS = 30L * 20L;

    public static long returnTimeoutTicks(double routeLength) {
        if (!Double.isFinite(routeLength) || routeLength <= 0.0) return 60L * 20L;
        return (long) Math.clamp(Math.ceil((routeLength / 4.0 + 60.0) * 20.0), 60.0 * 20.0, 20.0 * 60.0 * 20.0);
    }

    public record Target(double x, double y, double z, double heading) { }
    public record Result(boolean close, boolean ready, boolean missed, double targetSpeed, float steering) { }

    private AiGridApproach() { }

    public static Result evaluate(Target target, double x, double y, double z, double heading, double speed) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(heading)
            || !Double.isFinite(speed) || !Double.isFinite(target.x()) || !Double.isFinite(target.y())
            || !Double.isFinite(target.z()) || !Double.isFinite(target.heading())) {
            return new Result(true, false, true, 0.0, 0.0f);
        }
        double dx = target.x() - x;
        double dz = target.z() - z;
        double distance = Math.hypot(dx, dz);
        double along = dx * Math.cos(target.heading()) + dz * Math.sin(target.heading());
        double headingError = wrap(target.heading() - heading);
        boolean close = distance <= FINAL_APPROACH_METERS && Math.abs(target.y() - y) < 3.0;
        boolean ready = distance <= POSITION_TOLERANCE_METERS && Math.abs(headingError) < Math.toRadians(12)
            && Math.abs(speed) <= MAX_READY_SPEED_MPS && Math.abs(target.y() - y) < 1.0;
        boolean missed = close && (along < -1.5 || distance <= POSITION_TOLERANCE_METERS && Math.abs(headingError) > Math.toRadians(45));
        double targetSpeed = Math.min(8.0, Math.sqrt(2.0 * 2.0 * Math.max(0.0, along - 0.25)));
        double goalHeading = Math.atan2(dz, dx);
        double steerError = wrap(goalHeading - heading);
        float steering = (float) Math.clamp(0.8 * steerError + 0.25 * headingError, -0.55, 0.55);
        return new Result(close, ready, missed, ready || missed ? 0.0 : targetSpeed, ready ? 0.0f : steering);
    }

    private static double wrap(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }
}
