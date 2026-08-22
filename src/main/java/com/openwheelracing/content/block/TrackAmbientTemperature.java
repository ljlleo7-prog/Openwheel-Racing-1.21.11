package com.openwheelracing.content.block;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;

public final class TrackAmbientTemperature {
    private static final double RAIN_TARGET_C = 20.0;
    private static final double DAY_TARGET_C = 36.0;
    private static final double NIGHT_TARGET_C = 25.0;
    private static final double RAIN_TIME_CONSTANT_SECONDS = 45.0;
    private static final double DRY_TIME_CONSTANT_SECONDS = 480.0;
    private static final long UPDATE_INTERVAL_TICKS = 20L;
    private static final Map<ServerLevel, State> STATES = new IdentityHashMap<>();

    private TrackAmbientTemperature() {
    }

    public static double get(ServerLevel level) {
        State state = STATES.get(level);
        if (state != null) return state.temperatureC;
        double initial = level.isRaining() ? RAIN_TARGET_C : dryTarget(level);
        STATES.put(level, new State(initial, level.getGameTime()));
        return initial;
    }

    public static void tick(ServerLevel level) {
        State state = STATES.computeIfAbsent(level, ignored -> new State(level.isRaining() ? RAIN_TARGET_C : dryTarget(level), level.getGameTime()));
        long now = level.getGameTime();
        long elapsedTicks = now - state.updatedAt;
        if (elapsedTicks < UPDATE_INTERVAL_TICKS) return;
        double target = level.isRaining() ? RAIN_TARGET_C : dryTarget(level);
        double tau = level.isRaining() ? RAIN_TIME_CONSTANT_SECONDS : DRY_TIME_CONSTANT_SECONDS;
        double alpha = 1.0 - Math.exp(-(elapsedTicks / 20.0) / tau);
        state.temperatureC += (target - state.temperatureC) * alpha;
        state.updatedAt = now;
    }

    public static void clearAll() {
        STATES.clear();
    }

    static double progress(double currentC, double targetC, double elapsedSeconds, double timeConstantSeconds) {
        double alpha = 1.0 - Math.exp(-Math.max(0.0, elapsedSeconds) / Math.max(1.0, timeConstantSeconds));
        return currentC + (targetC - currentC) * alpha;
    }

    private static double dryTarget(ServerLevel level) {
        long dayTime = Math.floorMod(level.getDayTime(), 24_000L);
        return dayTime < 12_000L ? DAY_TARGET_C : NIGHT_TARGET_C;
    }

    private static final class State {
        double temperatureC;
        long updatedAt;

        State(double temperatureC, long updatedAt) {
            this.temperatureC = temperatureC;
            this.updatedAt = updatedAt;
        }
    }
}
