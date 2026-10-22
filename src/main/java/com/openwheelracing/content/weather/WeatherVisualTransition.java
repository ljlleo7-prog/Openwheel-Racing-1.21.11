package com.openwheelracing.content.weather;

/** Client presentation only; never used for precipitation or moisture simulation. */
public final class WeatherVisualTransition {
    private static final float INTENSITY_STEP = 1.0F / 200.0F;
    private WeatherSchedule.Condition displayed = WeatherSchedule.Condition.CLEAR;
    private float previousIntensity;
    private float intensity;
    private float previousThunder;
    private float thunder;

    public void tick(WeatherSchedule.Condition condition) {
        var requested = condition == WeatherSchedule.Condition.SNOW ? WeatherSchedule.Condition.SNOW
            : condition == WeatherSchedule.Condition.CLEAR ? WeatherSchedule.Condition.CLEAR : WeatherSchedule.Condition.RAIN;
        if (displayed == WeatherSchedule.Condition.CLEAR) displayed = requested;
        boolean changingType = requested != WeatherSchedule.Condition.CLEAR && requested != displayed;
        previousIntensity = intensity;
        previousThunder = thunder;
        intensity = approach(intensity, requested == WeatherSchedule.Condition.CLEAR || changingType ? 0 : 1);
        thunder = approach(thunder, condition == WeatherSchedule.Condition.THUNDER && !changingType ? 1 : 0);
        // Keep the old particle type through the fade, including its final interpolated frame.
        if (intensity == 0 && previousIntensity == 0) displayed = requested;
    }

    public float intensity(float partialTick) {
        return interpolate(previousIntensity, intensity, partialTick);
    }

    public float thunder(float partialTick) {
        return interpolate(previousThunder, thunder, partialTick);
    }

    public WeatherSchedule.Condition displayed() { return displayed; }

    public void reset() {
        displayed = WeatherSchedule.Condition.CLEAR;
        previousIntensity = intensity = previousThunder = thunder = 0;
    }

    private static float approach(float current, float target) {
        if (Math.abs(target - current) <= INTENSITY_STEP + 1e-6F) return target;
        return current + Math.copySign(INTENSITY_STEP, target - current);
    }

    private static float interpolate(float previous, float current, float partialTick) {
        return previous + (current - previous) * Math.clamp(partialTick, 0, 1);
    }
}
