package com.openwheelracing.content.ai;

/** Uses the remaining friction envelope aggressively; full inputs are allowed only when that envelope permits them. */
public final class AiFormationGripControl {
    public static final double UTILIZATION = 0.90;

    private AiFormationGripControl() { }

    public static BasicAiDriveCommand command(double speed, double target, float steering,
                                              BasicAiGripModel.State grip, BasicAiNearbyAvoidance.Decision avoidance,
                                              double headingError, double lateralError, boolean paved) {
        double lateralCapacity = Math.max(0.1, grip.lateralAcceleration(speed));
        double turnUtilization = 0.75;
        double maxAngle = Math.atan(3.6 * lateralCapacity * turnUtilization / Math.max(1.0, speed * speed));
        float safeSteering = (float) Math.clamp(steering, -Math.min(1.0, maxAngle / Math.toRadians(34)), Math.min(1.0, maxAngle / Math.toRadians(34)));
        double curvature = Math.tan(safeSteering * Math.toRadians(34)) / 3.6;
        double lateralFraction = speed * speed * Math.abs(curvature) / lateralCapacity;
        double remaining = Math.sqrt(Math.max(0.0, UTILIZATION * UTILIZATION - lateralFraction * lateralFraction));
        double availableLongitudinal = grip.longitudinalAcceleration(speed) * remaining;
        if (avoidance.threat()) return BasicAiDriveCommand.stopped(safeSteering);
        boolean aligned = paved && Math.abs(headingError) < 0.18 && Math.abs(lateralError) < 1.0;
        if (speed > target + 1.0) {
            float brake = (float) Math.clamp(availableLongitudinal / Math.max(0.1, grip.brakeAcceleration(speed)), 0.0, 1.0);
            return new BasicAiDriveCommand(0, brake, safeSteering);
        }
        if (speed < target - 2.0 && aligned) {
            float throttle = (float) Math.clamp(availableLongitudinal / Math.max(0.1, grip.driveAcceleration(speed)), 0.0, 1.0);
            return new BasicAiDriveCommand(throttle, 0, safeSteering);
        }
        BasicAiDriveCommand follow = BasicAiCarController.speedCommand(speed, target, 0.0, safeSteering, avoidance, grip);
        float throttleBound = (float) Math.min(aligned ? 1.0 : 0.25, availableLongitudinal / Math.max(0.1, grip.driveAcceleration(speed)));
        float brakeBound = (float) Math.min(1.0, availableLongitudinal / Math.max(0.1, grip.brakeAcceleration(speed)));
        return new BasicAiDriveCommand(Math.min(follow.throttle(), throttleBound), Math.min(follow.brake(), brakeBound), safeSteering);
    }
}
