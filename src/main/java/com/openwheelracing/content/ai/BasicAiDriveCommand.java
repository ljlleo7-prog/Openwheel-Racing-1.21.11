package com.openwheelracing.content.ai;

public record BasicAiDriveCommand(float throttle, float brake, float steering) {
    public BasicAiDriveCommand {
        throttle = clamp(throttle, 0.0f, 1.0f);
        brake = clamp(brake, 0.0f, 1.0f);
        steering = clamp(steering, -1.0f, 1.0f);
        if (brake > 0.0f) {
            throttle = 0.0f;
        }
    }

    public static BasicAiDriveCommand stopped(float steering) {
        return new BasicAiDriveCommand(0.0f, 1.0f, steering);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
