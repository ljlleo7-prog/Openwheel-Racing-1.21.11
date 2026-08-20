package com.openwheelracing.content.block;

import net.minecraft.server.level.ServerLevel;

final class TrackWeatherPhase {
    private TrackWeatherPhase() {
    }

    static Sample sample(ServerLevel level) {
        return TrackWeatherPhaseData.get(level).sample(level);
    }

    record Sample(double progress, long weatherEpoch, double progressAtEpoch) {
        Sample(double progress, long weatherEpoch) {
            this(progress, weatherEpoch, progress);
        }
    }

}
