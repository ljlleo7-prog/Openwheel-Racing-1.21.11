package com.openwheelracing.client.input;

public class KeyboardInputSettings {
    public static final float LOW_SPEED_KMH = 60.0f;
    public static final float HIGH_SPEED_KMH = 220.0f;

    public float lowSpeedSteeringRate = 1.0f;
    public float highSpeedSteeringRate = 1.0f;
    public float lowSpeedCenteringRate = 1.0f;
    public float highSpeedCenteringRate = 1.0f;
    public float lowSpeedSteeringGain = 1.0f;
    public float highSpeedSteeringGain = 1.0f;
    public float speedResponseCurve = 1.0f;
    public float throttleResponse = 1.0f;
    public float brakeResponse = 1.0f;
    public Float stabilityAssistStrength = 0.65f;

    public static KeyboardInputSettings defaults() {
        return new KeyboardInputSettings();
    }

    public KeyboardInputSettings copy() {
        KeyboardInputSettings copy = new KeyboardInputSettings();
        copy.lowSpeedSteeringRate = lowSpeedSteeringRate;
        copy.highSpeedSteeringRate = highSpeedSteeringRate;
        copy.lowSpeedCenteringRate = lowSpeedCenteringRate;
        copy.highSpeedCenteringRate = highSpeedCenteringRate;
        copy.lowSpeedSteeringGain = lowSpeedSteeringGain;
        copy.highSpeedSteeringGain = highSpeedSteeringGain;
        copy.speedResponseCurve = speedResponseCurve;
        copy.throttleResponse = throttleResponse;
        copy.brakeResponse = brakeResponse;
        copy.stabilityAssistStrength = stabilityAssistStrength;
        return copy.sanitized();
    }

    public KeyboardInputSettings sanitized() {
        lowSpeedSteeringRate = clampFinite(lowSpeedSteeringRate, 0.5f, 2.0f);
        highSpeedSteeringRate = clampFinite(highSpeedSteeringRate, 0.5f, 2.0f);
        lowSpeedCenteringRate = clampFinite(lowSpeedCenteringRate, 0.5f, 2.0f);
        highSpeedCenteringRate = clampFinite(highSpeedCenteringRate, 0.5f, 2.0f);
        lowSpeedSteeringGain = clampFinite(lowSpeedSteeringGain, 0.7f, 1.3f);
        highSpeedSteeringGain = clampFinite(highSpeedSteeringGain, 0.5f, 1.3f);
        speedResponseCurve = clampFinite(speedResponseCurve, 0.6f, 1.4f);
        throttleResponse = clampPositive(throttleResponse, 0.5f, 2.0f);
        brakeResponse = clampPositive(brakeResponse, 0.5f, 2.0f);
        stabilityAssistStrength = stabilityAssistStrength == null
            ? 0.65f
            : clampNonNegative(stabilityAssistStrength, 0.0f, 1.0f, 0.65f);
        return this;
    }

    public float throttleRiseSeconds() {
        return 0.30f / throttleResponse;
    }

    public float throttleReleaseSeconds() {
        return 0.08f / throttleResponse;
    }

    public float brakeRiseSeconds() {
        return 0.15f / brakeResponse;
    }

    public float brakeReleaseSeconds() {
        return 0.08f / brakeResponse;
    }

    private static float clampFinite(float value, float min, float max) {
        return WheelInputSettings.clamp(Float.isFinite(value) ? value : 1.0f, min, max);
    }

    private static float clampPositive(float value, float min, float max) {
        return WheelInputSettings.clamp(Float.isFinite(value) && value > 0.0f ? value : 1.0f, min, max);
    }

    private static float clampNonNegative(float value, float min, float max, float fallback) {
        return WheelInputSettings.clamp(Float.isFinite(value) && value >= 0.0f ? value : fallback, min, max);
    }
}
