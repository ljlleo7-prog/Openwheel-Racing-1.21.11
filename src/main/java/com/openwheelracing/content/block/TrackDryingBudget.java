package com.openwheelracing.content.block;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class TrackDryingBudget {
    private static final int MAX_UPDATES_PER_LEVEL_TICK = 32;
    private static final Map<ServerLevel, Counter> COUNTERS = new IdentityHashMap<>();

    private TrackDryingBudget() {
    }

    public static boolean dryOneStage(ServerLevel level, BlockPos pos) {
        long tick = level.getGameTime();
        Counter counter = COUNTERS.computeIfAbsent(level, ignored -> new Counter());
        if (counter.tick != tick) {
            counter.tick = tick;
            counter.updates = 0;
        }
        if (counter.updates >= MAX_UPDATES_PER_LEVEL_TICK) return false;
        BlockState state = level.getBlockState(pos);
        TrackMoisture moisture = WettableTrack.moisture(state);
        if (moisture == TrackMoisture.DRY || !state.hasProperty(WettableTrack.MOISTURE)) return false;
        counter.updates++;
        level.setBlock(pos, state.setValue(WettableTrack.MOISTURE, moisture.drier()), 2);
        return true;
    }

    private static final class Counter {
        long tick = Long.MIN_VALUE;
        int updates;
    }
}
