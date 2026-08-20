package com.openwheelracing.content.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

final class TrackWeatherPhaseData extends SavedData {
    private static final Codec<TrackWeatherPhaseData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.optionalFieldOf("raining", false).forGetter(data -> data.raining),
        Codec.BOOL.optionalFieldOf("thundering", false).forGetter(data -> data.thundering),
        Codec.LONG.optionalFieldOf("changed_at", 0L).forGetter(data -> data.changedAt),
        Codec.DOUBLE.optionalFieldOf("progress_when_changed", 0.0).forGetter(data -> data.progressWhenChanged)
    ).apply(instance, TrackWeatherPhaseData::new));
    private static final SavedDataType<TrackWeatherPhaseData> TYPE = new SavedDataType<>(
        OpenwheelRacing.MODID + "_track_weather_phase",
        TrackWeatherPhaseData::new,
        CODEC,
        null
    );

    private boolean raining;
    private boolean thundering;
    private long changedAt;
    private double progressWhenChanged;
    private transient double progress;

    TrackWeatherPhaseData() {
        this(false, false, 0L, 0.0);
    }

    private TrackWeatherPhaseData(boolean raining, boolean thundering, long changedAt, double progressWhenChanged) {
        this.raining = raining;
        this.thundering = thundering;
        this.changedAt = Math.max(0L, changedAt);
        this.progressWhenChanged = clampProgress(progressWhenChanged);
        this.progress = this.progressWhenChanged;
    }

    static TrackWeatherPhaseData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    TrackWeatherPhase.Sample sample(ServerLevel level) {
        long tick = level.getGameTime();
        boolean currentlyRaining = level.isRaining();
        boolean currentlyThundering = level.isThundering();
        if (raining != currentlyRaining || raining && thundering != currentlyThundering) {
            progress = calculateProgress(tick);
            raining = currentlyRaining;
            thundering = currentlyThundering;
            changedAt = tick;
            progressWhenChanged = progress;
            setDirty();
        }
        progress = calculateProgress(tick);
        return new TrackWeatherPhase.Sample(progress, changedAt, progressWhenChanged);
    }

    private double calculateProgress(long tick) {
        long elapsed = Math.max(0L, tick - changedAt);
        return raining
            ? TrackWeatherProgression.advanceRainingProgress(progressWhenChanged, elapsed, thundering)
            : TrackWeatherProgression.dryingProgress(progressWhenChanged, elapsed);
    }

    private static double clampProgress(double value) {
        return Math.max(0.0, Math.min(3.0, value));
    }
}
