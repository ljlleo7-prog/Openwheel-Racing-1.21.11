package com.openwheelracing.content.block;

import com.openwheelracing.content.weather.RegionalWeather;
import com.openwheelracing.content.weather.WeatherRegion;
import com.openwheelracing.content.weather.WeatherSchedule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

final class RegionalTemperature {
    private static final Map<ServerLevel, Map<WeatherRegion, State>> STATES = new IdentityHashMap<>();

    private RegionalTemperature() {
    }

    static boolean enabled(ServerLevel level) { return RegionalWeather.enabled(level); }

    static double get(ServerLevel level, BlockPos pos) {
        if (!enabled(level)) return TrackAmbientTemperature.get(level);
        WeatherRegion region = RegionalWeather.region(level, pos);
        var states = STATES.computeIfAbsent(level, ignored -> new HashMap<>());
        if (states.size() > 256) states.clear();
        long now = level.getGameTime();
        State state = states.computeIfAbsent(region, ignored -> new State(target(level, WeatherSchedule.sample(region, now)), now));
        while (state.updatedAt < now) {
            var weather = WeatherSchedule.sample(region, state.updatedAt);
            long end = Math.min(now, weather.endsAt());
            state.temperatureC = TrackAmbientTemperature.progress(state.temperatureC, target(level, weather),
                (end - state.updatedAt) / 20.0, weather.wet() ? 45.0 : 480.0);
            state.updatedAt = end;
        }
        return state.temperatureC;
    }

    static void clearAll() { STATES.clear(); }

    private static double target(ServerLevel level, WeatherSchedule.Sample weather) {
        return weather.condition() == WeatherSchedule.Condition.SNOW ? -2.0 : weather.wet() ? 20.0
            : Math.floorMod(level.getDayTime(), 24000) < 12000 ? 36.0 : 25.0;
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
