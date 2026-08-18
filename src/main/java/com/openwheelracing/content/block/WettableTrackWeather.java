package com.openwheelracing.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

final class WettableTrackWeather {
    private WettableTrackWeather() {
    }

    static void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean raining = level.isRainingAt(pos.above());
        TrackMoisture current = WettableTrack.moisture(state);
        TrackMoisture target = TrackMoistureModel.transitioned(current, raining);
        if (target == current) return;
        int neighbors = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            TrackMoisture adjacent = WettableTrack.moisture(level.getBlockState(pos.relative(direction)));
            if (raining ? adjacent.level() > current.level() : adjacent.level() < current.level()) neighbors++;
        }
        double chance = TrackMoistureModel.transitionChance(current, raining, level.isThundering(),
            level.getDayTime() % 24000L < 12000L, level.canSeeSky(pos.above()), neighbors);
        if (random.nextDouble() < chance) level.setBlock(pos, state.setValue(WettableTrack.MOISTURE, target), 2);
    }
}
