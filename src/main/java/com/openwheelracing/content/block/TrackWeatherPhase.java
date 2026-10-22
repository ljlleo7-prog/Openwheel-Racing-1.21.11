package com.openwheelracing.content.block;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import com.openwheelracing.content.weather.RegionalWeather;

final class TrackWeatherPhase {
    static Sample sample(ServerLevel level, BlockPos pos) {
        if (!RegionalWeather.enabled(level)) return sample(level);
        return RegionalTrackWeatherData.get(level).sample(RegionalWeather.region(level, pos), level.getGameTime());
    }

    private TrackWeatherPhase() {
    }

    static Sample sample(ServerLevel level) {
        return TrackWeatherPhaseData.get(level).sample(level);
    }

    record Sample(double progress, long weatherEpoch, double progressAtEpoch, boolean drying) {
        Sample(double progress, long weatherEpoch, double progressAtEpoch) {
            this(progress, weatherEpoch, progressAtEpoch, false);
        }

        Sample(double progress, long weatherEpoch) {
            this(progress, weatherEpoch, progress);
        }
    }

}
