package com.openwheelracing.content.ai;

/** Distance-based tyre warming with a conservative corner-speed and late braking envelope. */
public final class AiFormationHeating {
    public static final double STRAIGHT_SPEED_MPS = 200.0 / 3.6;
    private static final double MAX_WEAVE_METERS = 0.8;

    public record Sample(double speed, double offset, double headingOffset, double curvatureOffset, boolean braking, boolean weaving) { }

    private AiFormationHeating() { }

    public static Sample sample(AiTrackPlan plan, double distance, double speed, BasicAiGripModel.State grip,
                                double gap, boolean traffic, boolean roadClear) {
        return sample(plan, distance, speed, grip, gap, traffic, roadClear, distance * waveNumber(speed, grip), 1.0);
    }

    public static Sample sample(AiTrackPlan plan, double distance, double speed, BasicAiGripModel.State grip,
                                double gap, boolean traffic, boolean roadClear, double phase, double intensity) {
        double target = cornerSpeed(roadCurvature(plan, distance), grip, speed);
        boolean straight = Math.abs(roadCurvature(plan, distance)) < 0.0015;
        double braking = Math.max(2.0, grip.brakeAcceleration(speed) * 0.90);
        double reaction = Math.max(5.0, speed * 0.35);
        double horizon = Math.min(plan.routeLength() * 0.5, speed * speed / (2.0 * braking) + reaction + 100.0);
        for (double ahead = 4.0; ahead <= horizon; ahead += 4.0) {
            double curvature = roadCurvature(plan, distance + ahead);
            double next = cornerSpeed(curvature, grip, speed);
            target = Math.min(target, Math.sqrt(next * next + 2.0 * braking * Math.max(0.0, ahead - reaction)));
            if (ahead < Math.max(60.0, speed * 2.0) && Math.abs(curvature) >= 0.0015) straight = false;
        }
        // No weave while braking, near another car, on narrow/off-track pavement, or entering a bend.
        boolean weave = straight && !traffic && roadClear && gap > Math.max(18.0, speed * 0.65)
            && speed > 15.0 && target >= speed - 1.0;
        double wave = waveNumber(speed, grip);
        double amplitude = weave ? Math.min(MAX_WEAVE_METERS,
            grip.lateralAcceleration(speed) * 0.75 / Math.max(1.0, speed * speed * wave * wave))
                * Math.clamp(intensity, 0.0, 1.0) : 0.0;
        return new Sample(target, amplitude * Math.sin(phase), amplitude * wave * Math.cos(phase),
            -amplitude * wave * wave * Math.sin(phase), target < speed - 2.0, weave);
    }

    public static double waveNumber(double speed, BasicAiGripModel.State grip) {
        double wave = Math.sqrt(grip.lateralAcceleration(speed) * 0.75 / Math.max(1.0, speed * speed * MAX_WEAVE_METERS));
        return Math.clamp(wave, 2.0 * Math.PI / 180.0, 2.0 * Math.PI / 24.0);
    }

    static double roadCurvature(AiTrackPlan plan, double distance) {
        double local = plan.sample(distance).curvature();
        if (Math.abs(local) >= 0.01) return local; // Preserve genuine sharp-corner peaks.
        double total = 0.0;
        for (double offset = -12.0; offset <= 12.0; offset += 4.0) total += plan.sample(distance + offset).curvature();
        return total / 7.0;
    }

    private static double cornerSpeed(double curvature, BasicAiGripModel.State grip, double speed) {
        double bend = Math.abs(curvature);
        if (bend < 0.0015) return STRAIGHT_SPEED_MPS;
        return Math.min(STRAIGHT_SPEED_MPS, Math.sqrt(Math.max(1.0, grip.lateralAcceleration(speed) * 0.70) / bend));
    }
}
