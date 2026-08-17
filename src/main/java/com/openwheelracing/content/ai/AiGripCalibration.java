package com.openwheelracing.content.ai;

import java.util.SplittableRandom;

/** Bounded deterministic pace calibration. A failed trial freezes the last proven setting. */
public final class AiGripCalibration {
    private static final int LEVEL_COUNT = 5;
    private final Settings[] levels;
    private final double maximumStepPercent;
    private final boolean randomSteps;
    private int provenIndex;
    private int trialIndex;
    private int consecutiveClean;
    private int trialLaps;
    private boolean frozen;

    public AiGripCalibration() { this(2.5, false, 0L); }

    public AiGripCalibration(double maximumStepPercent, boolean randomSteps, long seed) {
        this.maximumStepPercent = Math.max(0.0, Math.min(10.0, maximumStepPercent));
        this.randomSteps = randomSteps;
        this.levels = new Settings[LEVEL_COUNT];
        SplittableRandom random = new SplittableRandom(seed);
        double scale = 1.0;
        for (int index = 0; index < LEVEL_COUNT; index++) {
            if (index > 0) {
                double incrementPercent = randomSteps ? random.nextDouble() * this.maximumStepPercent : this.maximumStepPercent;
                scale *= 1.0 + incrementPercent / 100.0;
            }
            double paceDelta = scale - 1.0;
            levels[index] = new Settings(scale, Math.min(0.70, 0.50 + paceDelta * 0.80),
                Math.min(1.20, 0.75 + paceDelta * 2.0));
        }
    }

    public Settings settings() { return levels[trialIndex]; }
    public double paceScale() { return settings().paceScale(); }
    public double provenScale() { return levels[provenIndex].paceScale(); }
    public boolean frozen() { return frozen; }

    public boolean manualPush() {
        if (trialIndex >= levels.length - 1) return false;
        frozen = false;
        consecutiveClean = 0;
        trialIndex = Math.min(levels.length - 1, Math.max(trialIndex, provenIndex + 1));
        return true;
    }

    public void recordLap(boolean clean) {
        if (frozen || trialIndex == levels.length - 1 && provenIndex == trialIndex) return;
        trialLaps++;
        if (!clean) {
            trialIndex = provenIndex;
            consecutiveClean = 0;
            frozen = true;
            return;
        }
        if (++consecutiveClean < 2) return;
        provenIndex = trialIndex;
        consecutiveClean = 0;
        if (trialIndex < levels.length - 1) trialIndex++;
    }

    public String status() {
        return String.format(java.util.Locale.ROOT,
            "trial=%.4f proven=%.4f step_max=%.3f%% mode=%s clean_streak=%d laps=%d %s",
            paceScale(), provenScale(), maximumStepPercent, randomSteps ? "random" : "fixed", consecutiveClean,
            trialLaps, frozen ? "frozen" : "testing");
    }

    public record Settings(double paceScale, double brakingCapabilityFraction, double speedErrorGain) {
    }
}
