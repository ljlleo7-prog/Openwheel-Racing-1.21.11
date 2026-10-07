package com.openwheelracing.content.block;

public final class TrackWeatherProgression {
    // Rain progression uses Minecraft seconds. Keep the first stage short enough
    // to signal rain, while giving drivers several minutes before the track is damp.
    private static final double[] NORMAL_STAGE_SECONDS = {240.0, 960.0, 960.0};
    private static final double[] THUNDER_STAGE_SECONDS = {180.0, 720.0, 720.0};
    private static final double NATURAL_DRY_STAGE_SECONDS = 420.0;
    private TrackWeatherProgression() {
    }

    public static int rainingTargetLevel(long rainingTicks, boolean thundering) {
        return (int) Math.floor(rainingProgress(rainingTicks, thundering));
    }

    public static double rainingProgress(long rainingTicks, boolean thundering) {
        return advanceRainingProgress(0.0, rainingTicks, thundering);
    }

    public static double advanceRainingProgress(double startingProgress, long rainingTicks, boolean thundering) {
        // Persistent normal rain settles at a spatial mix of damp and wet track;
        // only a thunderstorm can push the target into the soaking stage.
        double maximumProgress = thundering ? 3.0 : 1.5;
        double progress = Math.max(0.0, Math.min(maximumProgress, startingProgress));
        double seconds = Math.max(0L, rainingTicks) / 20.0;
        double[] stageSeconds = thundering ? THUNDER_STAGE_SECONDS : NORMAL_STAGE_SECONDS;
        while (seconds > 0.0 && progress < maximumProgress) {
            int stage = Math.min(2, (int) Math.floor(progress));
            double toNextStage = (stage + 1.0 - progress) * stageSeconds[stage];
            toNextStage = Math.min(toNextStage, (maximumProgress - progress) * stageSeconds[stage]);
            if (seconds < toNextStage) return progress + seconds / stageSeconds[stage];
            progress = Math.min(maximumProgress, stage + 1.0);
            seconds -= toNextStage;
        }
        return Math.min(maximumProgress, progress);
    }

    public static double dryingProgress(double progressWhenRainStopped, long dryTicks) {
        return Math.max(0.0, Math.min(3.0, progressWhenRainStopped)
            - Math.max(0L, dryTicks) / (NATURAL_DRY_STAGE_SECONDS * 20.0));
    }

    public static int dryingTargetLevel(int levelWhenRainStopped, long dryTicks) {
        return (int) Math.floor(dryingProgress(levelWhenRainStopped, dryTicks));
    }
}
