package com.openwheelracing.content.ai;

public final class BasicAiPlayerPrior {
    private static final boolean ENABLED = false;

    private BasicAiPlayerPrior() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static double blendSpeed(double baseline, double playerSpeed, int sampleCount, double previousResidual) {
        if (!(playerSpeed > 0.0) || sampleCount <= 0) {
            return baseline + previousResidual * 0.9;
        }
        double confidence = Math.min(0.18, sampleCount / 20.0 * 0.18);
        double residual = clamp(playerSpeed - baseline, -12.0, 12.0) * confidence;
        double filtered = previousResidual * 0.85 + residual * 0.15;
        return Math.max(0.0, baseline + clamp(filtered, -8.0, 8.0));
    }

    public static double phaseWeight(BasicAiTrainingPhase phase, BasicAiTrafficMode mode) {
        if (phase == null || mode != BasicAiTrafficMode.RACE) {
            return 0.0;
        }
        return phase == BasicAiTrainingPhase.FLYING ? 1.0 : phase == BasicAiTrainingPhase.RACE_START ? 0.35 : 0.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
