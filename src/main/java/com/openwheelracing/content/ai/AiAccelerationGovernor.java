package com.openwheelracing.content.ai;

/** Learns the throttle needed for a measured acceleration, including drag and actual engine/gear output. */
public final class AiAccelerationGovernor {
    private double previousSpeed;
    private long previousTick = Long.MIN_VALUE;
    private double throttle;

    public BasicAiDriveCommand update(BasicAiDriveCommand command, double speed, double targetSpeed, double accelerationLimit, long tick) {
        double dt = previousTick == Long.MIN_VALUE ? 0.05 : Math.clamp((tick - previousTick) / 20.0, 0.05, 0.25);
        double acceleration = previousTick == Long.MIN_VALUE ? 0.0 : (speed - previousSpeed) / dt;
        previousSpeed = speed;
        previousTick = tick;
        if (targetSpeed <= 0.1) {
            throttle = 0.0;
            return BasicAiDriveCommand.stopped(command.steering());
        }
        if (command.brake() > 0.0f) {
            throttle *= 0.95;
            return command;
        }
        double desired = Math.clamp((targetSpeed - speed) * 0.9, -accelerationLimit, accelerationLimit);
        double correction = Math.clamp((desired - acceleration) * dt * 0.15, -0.10, 0.06);
        throttle = Math.clamp(throttle + correction, 0.0, 1.0);
        return new BasicAiDriveCommand((float) throttle, 0.0f, command.steering());
    }

    public void reset() { previousTick = Long.MIN_VALUE; throttle = 0.0; }
}
