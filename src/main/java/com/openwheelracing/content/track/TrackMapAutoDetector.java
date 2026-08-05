package com.openwheelracing.content.track;

import com.openwheelracing.registry.OWRBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TrackMapAutoDetector {
    public static final int MIN_RADIUS_BLOCKS = 128;
    public static final int MAX_RADIUS_BLOCKS = 2_048;
    private static final int BFS_STEPS_PER_TICK = 384;
    private static final int SEED_RADIUS = 8;
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

    public static void clearJobs() {
        JOBS.clear();
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
        if (block == OWRBlocks.START_FINISH.get()) {
            return SurfaceCell.START_FINISH;
        }
        if (block == OWRBlocks.CHECKPOINT.get()) {
            return SurfaceCell.CHECKPOINT;
        }
        if (block == OWRBlocks.KERB.get()) {
            return SurfaceCell.KERB;
        }
        if (block == Blocks.WHITE_CONCRETE) {
            return SurfaceCell.WHITE_CONCRETE;
        }
        if (block == OWRBlocks.ASPHALT_TRACK.get() || block == OWRBlocks.ASPHALT_TRACK_SLAB.get()) {
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

    public record Progress(boolean running, int scannedChunks, int totalChunks, int detectedCells) {
        public static final Progress IDLE = new Progress(false, 0, 0, 0);
    }

    private enum SurfaceCell {
        NONE,
        ASPHALT,
        PIT,
        START_FINISH,
        CHECKPOINT,
        WHITE_CONCRETE,
        KERB
    }

    private record Bounds(int minX, int minZ, int maxX, int maxZ) {
    }

    private static final class ScanJob {
        private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> levelKey;
        private final String dimensionId;
        private final BlockPos origin;
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;
        private final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        private final Set<Long> visited = new HashSet<>();
        private final Map<Integer, BitSet> asphalt = new HashMap<>();
        private final Map<Integer, BitSet> pit = new HashMap<>();
        private boolean seeded;
        private int scanned;
        private int detectedCells;

        private ScanJob(ServerLevel level, BlockPos center, int radius) {
            this.levelKey = level.dimension();
            this.dimensionId = level.dimension().identifier().toString();
            this.origin = center.immutable();
            this.minX = center.getX() - radius;
            this.maxX = center.getX() + radius;
            this.minY = Math.max(level.getMinY(), center.getY() - radius);
            this.maxY = Math.min(level.getMaxY() - 1, center.getY() + radius);
            this.minZ = center.getZ() - radius;
            this.maxZ = center.getZ() + radius;
        }

        private boolean tick(ServerLevel level, boolean hasTime) {
            if (!hasTime) {
                return false;
            }
            if (!seeded && !seed(level)) {
                return true;
            }
            int budget = BFS_STEPS_PER_TICK;
            while (budget-- > 0 && !frontier.isEmpty()) {
                scan(frontier.removeFirst(), level);
            }
            if (!frontier.isEmpty()) {
                return false;
            }
            finish(level);
            return true;
        }

        private boolean seed(ServerLevel level) {
            for (int dy = -SEED_RADIUS; dy <= SEED_RADIUS; dy++) {
                int y = origin.getY() + dy;
                if (y < minY || y > maxY) {
                    continue;
                }
                for (int dx = -SEED_RADIUS; dx <= SEED_RADIUS; dx++) {
                    for (int dz = -SEED_RADIUS; dz <= SEED_RADIUS; dz++) {
                        BlockPos pos = origin.offset(dx, dy, dz);
                        if (!inside(pos) || surfaceCell(level.getBlockState(pos)) == SurfaceCell.NONE) {
                            continue;
                        }
                        frontier.add(pos.immutable());
                        visited.add(pos.asLong());
                        seeded = true;
                        return true;
                    }
                }
            }
            return false;
        }

        private void scan(BlockPos pos, ServerLevel level) {
            scanned++;
            SurfaceCell cell = surfaceCell(level.getBlockState(pos));
            if (cell == SurfaceCell.NONE) {
                return;
            }
            store(pos, cell);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (!inside(next)) {
                            continue;
                        }
                        long key = next.asLong();
                        if (visited.add(key) && surfaceCell(level.getBlockState(next)) != SurfaceCell.NONE) {
                            frontier.add(next.immutable());
                        }
                    }
                }
            }
        }

        private void store(BlockPos pos, SurfaceCell cell) {
            Map<Integer, BitSet> target = cell == SurfaceCell.PIT ? pit : asphalt;
            target.computeIfAbsent(pos.getZ(), ignored -> new BitSet(maxX - minX + 1)).set(pos.getX() - minX);
            detectedCells++;
        }

        private boolean inside(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY && pos.getZ() >= minZ && pos.getZ() <= maxZ;
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
            return new Progress(true, scanned, Math.max(scanned + frontier.size(), 1), detectedCells);
        }
    }
}
