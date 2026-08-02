package com.openwheelracing.content.track;

import com.openwheelracing.registry.OWRBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TrackMapAutoDetector {
    public static final int MIN_RADIUS_BLOCKS = 128;
    public static final int MAX_RADIUS_BLOCKS = 2_048;
    private static final int CHUNKS_PER_TICK = 2;
    private static final Map<String, ScanJob> JOBS = new HashMap<>();

    private TrackMapAutoDetector() {
    }

    public static void begin(ServerLevel level, BlockPos center, int radiusBlocks) {
        int radius = Math.max(MIN_RADIUS_BLOCKS, Math.min(MAX_RADIUS_BLOCKS, radiusBlocks));
        String dimensionId = level.dimension().identifier().toString();
        if (JOBS.containsKey(dimensionId)) {
            return;
        }
        TrackMapData.get(level).clear(dimensionId);
        JOBS.put(dimensionId, new ScanJob(level, center, radius));
    }

    public static Progress progress(ServerLevel level) {
        ScanJob job = JOBS.get(level.dimension().identifier().toString());
        return job == null ? Progress.IDLE : job.progress();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (JOBS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        ArrayList<String> finished = new ArrayList<>();
        for (ScanJob job : new ArrayList<>(JOBS.values())) {
            ServerLevel level = server.getLevel(job.levelKey);
            if (level == null || job.tick(level, event.hasTime())) {
                finished.add(job.dimensionId);
            }
        }
        for (String dimensionId : finished) {
            JOBS.remove(dimensionId);
        }
    }

    private static SurfaceCell surfaceCell(BlockState state) {
        Block block = state.getBlock();
        if (block == OWRBlocks.PIT_LANE.get() || block == OWRBlocks.PIT_LANE_SLAB.get() || block == OWRBlocks.PIT_STOP_MARK.get()) {
            return SurfaceCell.PIT;
        }
        if (block == OWRBlocks.ASPHALT_TRACK.get() || block == OWRBlocks.ASPHALT_TRACK_SLAB.get() || block == OWRBlocks.START_FINISH.get() || block == OWRBlocks.CHECKPOINT.get()) {
            return SurfaceCell.ASPHALT;
        }
        return SurfaceCell.NONE;
    }

    private static List<TrackMapSnapshot.CellRun> runs(Map<Integer, BitSet> cells, int minX) {
        ArrayList<TrackMapSnapshot.CellRun> runs = new ArrayList<>();
        cells.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            BitSet row = entry.getValue();
            int start = row.nextSetBit(0);
            while (start >= 0) {
                int endExclusive = row.nextClearBit(start);
                runs.add(new TrackMapSnapshot.CellRun(entry.getKey(), minX + start, minX + endExclusive - 1));
                start = row.nextSetBit(endExclusive);
            }
        });
        runs.sort(Comparator.comparingInt(TrackMapSnapshot.CellRun::z).thenComparingInt(TrackMapSnapshot.CellRun::startX));
        return List.copyOf(runs);
    }

    private static Bounds bounds(List<TrackMapSnapshot.CellRun> asphaltRuns, List<TrackMapSnapshot.CellRun> pitRuns) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (TrackMapSnapshot.CellRun run : concat(asphaltRuns, pitRuns)) {
            minX = Math.min(minX, run.startX());
            maxX = Math.max(maxX, run.endX());
            minZ = Math.min(minZ, run.z());
            maxZ = Math.max(maxZ, run.z());
        }
        return new Bounds(minX, minZ, maxX, maxZ);
    }

    private static List<TrackMapSnapshot.CellRun> concat(List<TrackMapSnapshot.CellRun> first, List<TrackMapSnapshot.CellRun> second) {
        ArrayList<TrackMapSnapshot.CellRun> combined = new ArrayList<>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return combined;
    }

    private static Path regionPath(ServerLevel level, int regionX, int regionZ) {
        Path root = level.getServer().getWorldPath(LevelResource.ROOT);
        String path = level.dimension().identifier().getPath();
        Path dimensionRoot = switch (level.dimension().identifier().toString()) {
            case "minecraft:overworld" -> root;
            case "minecraft:the_nether" -> root.resolve("DIM-1");
            case "minecraft:the_end" -> root.resolve("DIM1");
            default -> root.resolve("dimensions").resolve(level.dimension().identifier().getNamespace()).resolve(path);
        };
        return dimensionRoot.resolve("region").resolve("r." + regionX + "." + regionZ + ".mca");
    }

    public record Progress(boolean running, int scannedChunks, int totalChunks, int detectedCells) {
        public static final Progress IDLE = new Progress(false, 0, 0, 0);
    }

    private enum SurfaceCell {
        NONE,
        ASPHALT,
        PIT
    }

    private record Bounds(int minX, int minZ, int maxX, int maxZ) {
    }

    private static final class ScanJob {
        private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> levelKey;
        private final String dimensionId;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final ArrayDeque<ChunkPos> chunks = new ArrayDeque<>();
        private final Map<Integer, BitSet> asphalt = new HashMap<>();
        private final Map<Integer, BitSet> pit = new HashMap<>();
        private final int totalChunks;
        private int scannedChunks;
        private int detectedCells;

        private ScanJob(ServerLevel level, BlockPos center, int radius) {
            this.levelKey = level.dimension();
            this.dimensionId = level.dimension().identifier().toString();
            this.minX = center.getX() - radius;
            this.maxX = center.getX() + radius;
            this.minZ = center.getZ() - radius;
            this.maxZ = center.getZ() + radius;
            for (int chunkX = Math.floorDiv(minX, 16); chunkX <= Math.floorDiv(maxX, 16); chunkX++) {
                for (int chunkZ = Math.floorDiv(minZ, 16); chunkZ <= Math.floorDiv(maxZ, 16); chunkZ++) {
                    ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
                    if (Files.isRegularFile(regionPath(level, chunk.getRegionX(), chunk.getRegionZ()))) {
                        chunks.add(chunk);
                    }
                }
            }
            this.totalChunks = chunks.size();
        }

        private boolean tick(ServerLevel level, boolean hasTime) {
            if (!hasTime) {
                return false;
            }
            int budget = CHUNKS_PER_TICK;
            while (budget-- > 0 && !chunks.isEmpty()) {
                scanChunk(level, chunks.removeFirst());
                scannedChunks++;
            }
            if (!chunks.isEmpty()) {
                return false;
            }
            finish(level);
            return true;
        }

        private void scanChunk(ServerLevel level, ChunkPos pos) {
            ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
            if (chunk == null) {
                return;
            }
            int startX = Math.max(minX, pos.getMinBlockX());
            int endX = Math.min(maxX, pos.getMaxBlockX());
            int startZ = Math.max(minZ, pos.getMinBlockZ());
            int endZ = Math.min(maxZ, pos.getMaxBlockZ());
            for (int z = startZ; z <= endZ; z++) {
                for (int x = startX; x <= endX; x++) {
                    int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    if (y < level.getMinY()) {
                        continue;
                    }
                    SurfaceCell cell = surfaceCell(chunk.getBlockState(new BlockPos(x, y, z)));
                    if (cell == SurfaceCell.NONE) {
                        continue;
                    }
                    Map<Integer, BitSet> target = cell == SurfaceCell.PIT ? pit : asphalt;
                    target.computeIfAbsent(z, ignored -> new BitSet(maxX - minX + 1)).set(x - minX);
                    detectedCells++;
                }
            }
        }

        private void finish(ServerLevel level) {
            TrackMapData data = TrackMapData.get(level);
            List<TrackMapSnapshot.CellRun> asphaltRuns = runs(asphalt, minX);
            List<TrackMapSnapshot.CellRun> pitRuns = runs(pit, minX);
            if (asphaltRuns.isEmpty() && pitRuns.isEmpty()) {
                return;
            }
            Bounds bounds = bounds(asphaltRuns, pitRuns);
            data.upsertAutoDetected(dimensionId, "Auto-Detected Track", bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(), asphaltRuns, pitRuns);
        }

        private Progress progress() {
            return new Progress(true, scannedChunks, totalChunks, detectedCells);
        }
    }
}
