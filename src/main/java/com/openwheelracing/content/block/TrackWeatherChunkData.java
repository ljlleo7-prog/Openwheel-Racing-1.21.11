package com.openwheelracing.content.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

final class TrackWeatherChunkData extends SavedData {
    private static final int PROGRESS_SCALE = 65_535;
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("chunk").forGetter(Entry::chunkPos),
        Codec.LONG.fieldOf("checkpoint").forGetter(Entry::checkpoint)
    ).apply(instance, Entry::new));
    private static final Codec<TrackWeatherChunkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ENTRY_CODEC.listOf().optionalFieldOf("chunks", List.of()).forGetter(TrackWeatherChunkData::entries)
    ).apply(instance, TrackWeatherChunkData::new));
    private static final SavedDataType<TrackWeatherChunkData> TYPE = new SavedDataType<>(
        OpenwheelRacing.MODID + "_track_weather_chunks",
        TrackWeatherChunkData::new,
        CODEC,
        null
    );

    private final Long2LongOpenHashMap checkpoints = new Long2LongOpenHashMap();

    TrackWeatherChunkData() {
        checkpoints.defaultReturnValue(Long.MIN_VALUE);
    }

    private TrackWeatherChunkData(List<Entry> entries) {
        this();
        for (Entry entry : entries) checkpoints.put(entry.chunkPos(), entry.checkpoint());
    }

    static TrackWeatherChunkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    Checkpoint get(long chunkPos) {
        long packed = checkpoints.get(chunkPos);
        if (packed == Long.MIN_VALUE) return null;
        return new Checkpoint(packed >>> 16, decodeProgress((int) (packed & 0xFFFFL)));
    }

    void put(long chunkPos, long sampledAt, double progress) {
        long safeTime = Math.max(0L, sampledAt) & 0x0000FFFFFFFFFFFFL;
        int encodedProgress = (int) Math.round(Math.max(0.0, Math.min(3.0, progress)) / 3.0 * PROGRESS_SCALE);
        long packed = safeTime << 16 | encodedProgress & 0xFFFFL;
        if (checkpoints.put(chunkPos, packed) != packed) setDirty();
    }

    boolean contains(long chunkPos) {
        return checkpoints.containsKey(chunkPos);
    }

    int size() {
        return checkpoints.size();
    }

    private List<Entry> entries() {
        ArrayList<Entry> entries = new ArrayList<>(checkpoints.size());
        checkpoints.long2LongEntrySet().fastForEach(entry -> entries.add(new Entry(entry.getLongKey(), entry.getLongValue())));
        return entries;
    }

    private static double decodeProgress(int encoded) {
        return (encoded & 0xFFFF) * 3.0 / PROGRESS_SCALE;
    }

    record Checkpoint(long sampledAt, double progress) {
    }

    private record Entry(long chunkPos, long checkpoint) {
    }
}
