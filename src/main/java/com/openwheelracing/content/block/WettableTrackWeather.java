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
        boolean locallyRaining = level.isRainingAt(pos.above());
        TrackMoisture current = WettableTrack.moisture(state);
        TrackWeatherPhase.Sample weather = TrackWeatherPhase.sample(level);
        TrackMoisture phaseTarget = randomizedTarget(pos, weather);
        boolean wetting = locallyRaining && current.level() < phaseTarget.level();
        boolean drying = !locallyRaining && current.level() > phaseTarget.level();
        TrackMoisture target = wetting ? current.wetter() : drying ? current.drier() : current;
        if (target == current) return;
        int neighbors = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            TrackMoisture adjacent = WettableTrack.moisture(level.getBlockState(pos.relative(direction)));
            if (wetting ? adjacent.level() >= target.level() : adjacent.level() <= target.level()) neighbors++;
        }
        double chance = TrackMoistureModel.transitionChance(current, wetting, level.isThundering(),
            level.getDayTime() % 24000L < 12000L, level.canSeeSky(pos.above()), neighbors);
        if (random.nextDouble() < chance) {
            level.setBlock(pos, state.setValue(WettableTrack.MOISTURE, target), 2);
            TrackMoistureTelemetryService.observe(level, pos, target);
        }
    }

    static TrackMoisture randomizedTarget(BlockPos pos, TrackWeatherPhase.Sample weather) {
        double progress = Math.max(0.0, Math.min(3.0, weather.progress()));
        int lower = (int) Math.floor(progress);
        if (lower >= 3) return TrackMoisture.SOAKING;
        double threshold = stableUnit(pos.asLong(), weather.weatherEpoch());
        int target = lower + (threshold < progress - lower ? 1 : 0);
        return TrackMoisture.values()[target];
    }

    static double stableUnit(long position, long salt) {
        long mixed = position ^ salt * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return (mixed >>> 11) * 0x1.0p-53;
    }
}
