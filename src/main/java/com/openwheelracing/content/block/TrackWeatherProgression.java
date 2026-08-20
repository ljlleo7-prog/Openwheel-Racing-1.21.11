package com.openwheelracing.content.block;

public final class TrackWeatherProgression {
    private static final double[] NORMAL_STAGE_SECONDS = {30.0, 100.0, 590.0};
    private static final double[] THUNDER_STAGE_SECONDS = {20.0, 50.0, 230.0};
    private TrackWeatherProgression() {
    }

    public static int rainingTargetLevel(long rainingTicks, boolean thundering) {
        return (int) Math.floor(rainingProgress(rainingTicks, thundering));
    }

    public static double rainingProgress(long rainingTicks, boolean thundering) {
        return advanceRainingProgress(0.0, rainingTicks, thundering);
    }

    public static double advanceRainingProgress(double startingProgress, long rainingTicks, boolean thundering) {
        double progress = Math.max(0.0, Math.min(3.0, startingProgress));
        double seconds = Math.max(0L, rainingTicks) / 20.0;
        double[] stageSeconds = thundering ? THUNDER_STAGE_SECONDS : NORMAL_STAGE_SECONDS;
        while (seconds > 0.0 && progress < 3.0) {
            int stage = Math.min(2, (int) Math.floor(progress));
            double toNextStage = (stage + 1.0 - progress) * stageSeconds[stage];
            if (seconds < toNextStage) return progress + seconds / stageSeconds[stage];
            progress = stage + 1.0;
            seconds -= toNextStage;
        }
        return Math.min(3.0, progress);
    }

    public static double dryingProgress(double progressWhenRainStopped, long dryTicks) {
        return Math.max(0.0, Math.min(3.0, progressWhenRainStopped) - Math.max(0L, dryTicks) / 900.0);
    }

    public static int dryingTargetLevel(int levelWhenRainStopped, long dryTicks) {
        return (int) Math.floor(dryingProgress(levelWhenRainStopped, dryTicks));
    }
}
