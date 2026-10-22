package com.openwheelracing.content.block;

import com.openwheelracing.content.weather.RegionalWeather;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import com.openwheelracing.content.track.TrackMapData;
import com.openwheelracing.content.track.TrackMapSnapshot;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

public final class TrackWeatherChunkProgression {
    private static final int COLUMNS_PER_TICK = 2_048;
    private static final int BLOCK_UPDATES_PER_TICK = 256;
    private static final int SURFACE_SEARCH_DEPTH = 4;
    private static final long SWEEP_INTERVAL_TICKS = 20;
    private static final int COLUMNS_PER_CHUNK_SLICE = 16;
    private static final Map<ServerLevel, LevelQueue> QUEUES = new IdentityHashMap<>();

    private TrackWeatherChunkProgression() {
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long chunkPos = event.getChunk().getPos().toLong();
        if (!RegionalWeather.enabled(level) && !TrackWeatherChunkData.get(level).contains(chunkPos)
            && outsideKnownTrackBounds(level, event.getChunk().getPos())) return;
        LevelQueue queue = QUEUES.computeIfAbsent(level, ignored -> new LevelQueue());
        if (!queue.queued.add(chunkPos)) return;
        TrackWeatherPhase.Sample current = TrackWeatherPhase.sample(level, event.getChunk().getPos().getMiddleBlockPosition(64));
        TrackWeatherChunkData.Checkpoint checkpoint = TrackWeatherChunkData.get(level).get(chunkPos);
        long sampledAt = checkpoint == null ? current.weatherEpoch() : checkpoint.sampledAt();
        double startProgress = checkpoint == null
            ? current.progressAtEpoch()
            : checkpoint.progress();
        queue.work.addLast(new ChunkWork(chunkPos, Math.max(0L, sampledAt), startProgress,
            level.getGameTime(), current.progress(), current.weatherEpoch()));
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long chunkPos = event.getChunk().getPos().toLong();
        LevelQueue queue = QUEUES.get(level);
        if (queue != null) {
            queue.loadedTrack.remove(chunkPos);
            if (queue.queued.remove(chunkPos)) queue.work.removeIf(work -> work.chunkPos == chunkPos);
        }
        TrackWeatherChunkData data = TrackWeatherChunkData.get(level);
        if (data.contains(chunkPos)) {
            data.put(chunkPos, level.getGameTime(), TrackWeatherPhase.sample(level, event.getChunk().getPos().getMiddleBlockPosition(64)).progress());
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            TrackAmbientTemperature.tick(level);
            enqueueRegionalSweep(level);
            process(level);
        }
    }

    static void observeTrack(ServerLevel level, BlockPos pos) {
        QUEUES.computeIfAbsent(level, ignored -> new LevelQueue()).loadedTrack.add(new ChunkPos(pos).toLong());
    }

    private static void enqueueRegionalSweep(ServerLevel level) {
        if (!RegionalWeather.enabled(level)) return;
        LevelQueue queue = QUEUES.get(level);
        if (queue == null || level.getGameTime() < queue.nextSweep) return;
        queue.nextSweep = level.getGameTime() + SWEEP_INTERVAL_TICKS;
        for (long chunkPos : queue.loadedTrack) {
            if (!queue.queued.add(chunkPos)) continue;
            var pos = new ChunkPos(chunkPos).getMiddleBlockPosition(64);
            var phase = TrackWeatherPhase.sample(level, pos);
            queue.work.addLast(new ChunkWork(chunkPos, level.getGameTime(), phase.progress(),
                level.getGameTime(), phase.progress(), phase.weatherEpoch()));
        }
    }

    public static void clearAll() {
        QUEUES.clear();
        TrackAmbientTemperature.clearAll();
    }

    private static boolean outsideKnownTrackBounds(ServerLevel level, ChunkPos chunk) {
        TrackMapSnapshot map = TrackMapData.get(level)
            .snapshot(level.dimension().identifier().toString())
            .orElse(TrackMapSnapshot.EMPTY);
        if (!map.present()) return false;
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        return minX + 15 < map.minX() || minX > map.maxX() || minZ + 15 < map.minZ() || minZ > map.maxZ();
    }

    private static void process(ServerLevel level) {
        LevelQueue queue = QUEUES.get(level);
        if (queue == null || queue.work.isEmpty()) return;
        int columns = 0;
        int updates = 0;
        while (columns < COLUMNS_PER_TICK && updates < BLOCK_UPDATES_PER_TICK && !queue.work.isEmpty()) {
            ChunkWork work = queue.work.peekFirst();
            int chunkX = ChunkPos.getX(work.chunkPos);
            int chunkZ = ChunkPos.getZ(work.chunkPos);
            if (!level.hasChunk(chunkX, chunkZ)) {
                finish(queue, work, false, level);
                continue;
            }
            int localX = work.column & 15;
            int localZ = work.column >>> 4;
            int x = chunkX * 16 + localX;
            int z = chunkZ * 16 + localZ;
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, surfaceY, z);
            boolean updated = false;
            for (int depth = 0; depth < SURFACE_SEARCH_DEPTH; depth++) {
                pos.setY(surfaceY - depth);
                var state = level.getBlockState(pos);
                if (!state.hasProperty(WettableTrack.MOISTURE)) continue;
                work.foundTrack = true;
                TrackMoisture current = WettableTrack.moisture(state);
                TrackMoisture target = WettableTrackWeather.randomizedTarget(pos, RegionalWeather.enabled(level)
                    ? TrackWeatherPhase.sample(level, pos) : new TrackWeatherPhase.Sample(work.endProgress, work.weatherEpoch));
                TrackMoisture caughtUp = caughtUpMoisture(level, pos, current, target, work);
                TrackMoistureTelemetryService.observe(level, pos, caughtUp);
                if (caughtUp != current) {
                    level.setBlock(pos, state.setValue(WettableTrack.MOISTURE, caughtUp), 2);
                    updates++;
                    updated = true;
                }
                break;
            }
            work.column++;
            columns++;
            if (work.column >= 256) {
                finish(queue, work, work.foundTrack, level);
            } else if (work.column % COLUMNS_PER_CHUNK_SLICE == 0) {
                queue.work.removeFirst();
                queue.work.addLast(work);
            }
            if (updated && updates >= BLOCK_UPDATES_PER_TICK) break;
        }
        if (queue.work.isEmpty() && queue.loadedTrack.isEmpty()) QUEUES.remove(level);
    }

    private static TrackMoisture caughtUpMoisture(ServerLevel level, BlockPos pos, TrackMoisture current,
                                                   TrackMoisture target, ChunkWork work) {
        if (RegionalWeather.enabled(level)) {
            int nextLevel = TrackWeatherResponseModel.transitionLevel(current.level(), target.level(),
                RegionalWeather.sample(level, pos).wet(), RegionalWeather.exposed(level, pos.above()));
            return TrackMoisture.values()[nextLevel];
        }
        if (current == target) return current;
        boolean wetting = target.level() > current.level();
        if (wetting && !level.isRainingAt(pos.above())) return current;
        long elapsed = Math.max(0L, work.sampledUntil - work.sampledAt);
        int result = current.level();
        double threshold = TrackMoistureField.threshold(pos.getX(), pos.getZ(), work.weatherEpoch);
        boolean day = level.getDayTime() % 24000L < 12000L;
        while (wetting ? result < target.level() : result > target.level()) {
            int transitionStage = wetting ? result : result - 1;
            double eligibleTicks = TrackChunkCatchUpModel.eligibleTicks(work.startProgress, work.endProgress,
                elapsed, transitionStage, threshold, wetting);
            double chance = TrackChunkCatchUpModel.transitionProbability(result, wetting,
                level.isThundering(), day, eligibleTicks);
            long salt = work.sampledAt ^ work.sampledUntil ^ (long) transitionStage * 0xD1B54A32D192ED03L;
            if (WettableTrackWeather.stableUnit(pos.asLong(), salt) >= chance) break;
            result += wetting ? 1 : -1;
        }
        return TrackMoisture.values()[Mth.clamp(result, 0, 3)];
    }

    private static void finish(LevelQueue queue, ChunkWork work, boolean trackChunk, ServerLevel level) {
        queue.work.removeFirst();
        queue.queued.remove(work.chunkPos);
        if (trackChunk) {
            queue.loadedTrack.add(work.chunkPos);
            TrackWeatherChunkData.get(level).put(work.chunkPos, work.sampledUntil, work.endProgress);
        } else {
            queue.loadedTrack.remove(work.chunkPos);
        }
    }

    private static final class LevelQueue {
        final ArrayDeque<ChunkWork> work = new ArrayDeque<>();
        final LongOpenHashSet queued = new LongOpenHashSet();
        final LongOpenHashSet loadedTrack = new LongOpenHashSet();
        long nextSweep;
    }

    private static final class ChunkWork {
        final long chunkPos;
        final long sampledAt;
        final double startProgress;
        final long sampledUntil;
        final double endProgress;
        final long weatherEpoch;
        int column;
        boolean foundTrack;

        ChunkWork(long chunkPos, long sampledAt, double startProgress, long sampledUntil,
                  double endProgress, long weatherEpoch) {
            this.chunkPos = chunkPos;
            this.sampledAt = sampledAt;
            this.startProgress = startProgress;
            this.sampledUntil = sampledUntil;
            this.endProgress = endProgress;
            this.weatherEpoch = weatherEpoch;
        }
    }
}
