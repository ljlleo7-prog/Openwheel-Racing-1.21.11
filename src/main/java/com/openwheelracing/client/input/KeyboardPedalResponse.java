package com.openwheelracing.client.input;

public final class KeyboardPedalResponse {
    public static final double TICK_SECONDS = 0.05;

    private KeyboardPedalResponse() {
    }

    public static float next(float current, boolean pressed, float initialBite,
                             float riseSeconds, float releaseSeconds, double dtSeconds) {
        float value = clamp(Float.isFinite(current) ? current : 0.0f, 0.0f, 1.0f);
        double dt = Math.max(0.0, dtSeconds);
        if (pressed) {
            value = Math.max(value, clamp(initialBite, 0.0f, 1.0f));
            return moveToward(value, 1.0f, (float) (dt / Math.max(0.001, riseSeconds)));
        }
        return moveToward(value, 0.0f, (float) (dt / Math.max(0.001, releaseSeconds)));
    }

    private static float moveToward(float value, float target, float amount) {
        if (value < target) return Math.min(target, value + amount);
        return Math.max(target, value - amount);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
