package com.openwheelracing.content.block;

import com.openwheelracing.content.race.TrackMoistureSnapshot;
import com.openwheelracing.content.track.TrackDefinitionsData;
import com.openwheelracing.content.track.survey.SurveyRoute;
import com.openwheelracing.content.track.survey.TrackSurveyData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

public final class TrackMoistureTelemetryService {
    private static final int SECTOR_COUNT = 32;
    private static final Map<ServerLevel, Cache> CACHES = new IdentityHashMap<>();
    private static final Map<ServerLevel, SurfaceCache> SURFACES = new IdentityHashMap<>();

    private TrackMoistureTelemetryService() {
    }

    public static TrackMoistureSnapshot snapshot(ServerLevel level) {
        long interval = level.getGameTime() / 20L;
        Cache cached = CACHES.get(level);
        if (cached != null && cached.interval == interval) return cached.snapshot;
        TrackMoistureSnapshot sampled = sample(level, (int) Math.min(Integer.MAX_VALUE, interval));
        CACHES.put(level, new Cache(interval, sampled));
        return sampled;
    }

    public static TrackMoistureSnapshot surfaceSnapshot(ServerLevel level) {
        TrackMoistureSnapshot live = snapshot(level);
        SurfaceCache surface = SURFACES.get(level);
        if (surface == null || surface.tiles.isEmpty()) return live;
        ArrayList<TrackMoistureSnapshot.Tile> tiles = new ArrayList<>(surface.tiles.size());
        surface.tiles.long2ObjectEntrySet().fastForEach(entry -> {
            TileData tile = entry.getValue();
            tiles.add(new TrackMoistureSnapshot.Tile(ChunkPos.getX(entry.getLongKey()), ChunkPos.getZ(entry.getLongKey()), tile.observed, tile.levels));
        });
        return new TrackMoistureSnapshot(live.revision(), live.drySamples(), live.dampSamples(), live.wetSamples(),
            live.soakingSamples(), live.loadedSamples(), live.estimatedSamples(), surface.revision, live.sectors(), tiles);
    }

    public static void observe(ServerLevel level, BlockPos pos, TrackMoisture moisture) {
        SurfaceCache surface = SURFACES.computeIfAbsent(level, ignored -> new SurfaceCache());
        long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        TileData tile = surface.tiles.computeIfAbsent(chunk, ignored -> new TileData());
        int index = (pos.getZ() & 15) * 16 + (pos.getX() & 15);
        int observedIndex = index >>> 3;
        int observedMask = 1 << (index & 7);
        int levelIndex = index >>> 2;
        int shift = (index & 3) * 2;
        int previous = tile.levels[levelIndex] >>> shift & 3;
        boolean wasObserved = (tile.observed[observedIndex] & observedMask) != 0;
        if (wasObserved && previous == moisture.level()) return;
        tile.observed[observedIndex] |= (byte) observedMask;
        tile.levels[levelIndex] = (byte) (tile.levels[levelIndex] & ~(3 << shift) | moisture.level() << shift);
        surface.revision++;
    }

    public static void clearAll() {
        CACHES.clear();
        SURFACES.clear();
    }

    private static TrackMoistureSnapshot sample(ServerLevel level, int revision) {
        var route = TrackDefinitionsData.get(level).activeTrack(level.dimension().identifier().toString())
            .flatMap(track -> TrackSurveyData.get(level).get(track.trackId()));
        if (route.isEmpty() || route.get().nodes().isEmpty()) return TrackMoistureSnapshot.EMPTY;
        SurveyRoute survey = route.get();
        int sectors = Math.min(SECTOR_COUNT, survey.nodes().size());
        int[] counts = new int[4];
        int loaded = 0;
        int estimated = 0;
        ArrayList<TrackMoistureSnapshot.Sector> rows = new ArrayList<>(sectors);
        for (int sector = 0; sector < sectors; sector++) {
            int index = Math.min(survey.nodes().size() - 1, sector * survey.nodes().size() / sectors);
            SurveyRoute.Node node = survey.nodes().get(index);
            Sample center = moistureAt(level, node.position().x(), node.position().y(), node.position().z());
            counts[center.level]++;
            if (center.estimated) estimated++; else loaded++;
            rows.add(new TrackMoistureSnapshot.Sector((int) Math.floor(node.position().x()),
                (int) Math.floor(node.position().z()), center.level, center.estimated));
        }
        int surfaceRevision = SURFACES.containsKey(level) ? SURFACES.get(level).revision : 0;
        return new TrackMoistureSnapshot(revision, counts[0], counts[1], counts[2], counts[3], loaded, estimated, surfaceRevision, rows, java.util.List.of());
    }

    private static Sample moistureAt(ServerLevel level, double x, double y, double z) {
        BlockPos base = BlockPos.containing(x, y - 0.15, z);
        boolean loaded = level.hasChunkAt(base);
        if (loaded) {
            BlockPos.MutableBlockPos cursor = base.mutable();
            for (int depth = 0; depth < 4; depth++) {
                cursor.setY(base.getY() - depth);
                var state = level.getBlockState(cursor);
                if (state.hasProperty(WettableTrack.MOISTURE)) {
                    TrackMoisture moisture = WettableTrack.moisture(state);
                    observe(level, cursor, moisture);
                    return new Sample(moisture.level(), false);
                }
            }
        }
        TrackWeatherPhase.Sample weather = TrackWeatherPhase.sample(level);
        return new Sample(WettableTrackWeather.randomizedTarget(base, weather).level(), true);
    }

    private record Sample(int level, boolean estimated) {
    }

    private record Cache(long interval, TrackMoistureSnapshot snapshot) {
    }

    private static final class SurfaceCache {
        final Long2ObjectOpenHashMap<TileData> tiles = new Long2ObjectOpenHashMap<>();
        int revision;
    }

    private static final class TileData {
        final byte[] observed = new byte[32];
        final byte[] levels = new byte[64];
    }
}
