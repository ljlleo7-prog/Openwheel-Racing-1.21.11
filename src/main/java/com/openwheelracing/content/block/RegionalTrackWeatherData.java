package com.openwheelracing.content.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.content.weather.RegionalWeatherData;
import com.openwheelracing.content.weather.WeatherRegion;
import com.openwheelracing.content.weather.WeatherSurfaceState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RegionalTrackWeatherData extends SavedData {
    private static final Codec<WeatherSurfaceState> STATE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        RegionalWeatherData.REGION_CODEC.fieldOf("region").forGetter(WeatherSurfaceState::region),
        Codec.LONG.fieldOf("updated_at").forGetter(WeatherSurfaceState::updatedAt),
        Codec.DOUBLE.fieldOf("progress").forGetter(WeatherSurfaceState::progress),
        Codec.LONG.fieldOf("epoch").forGetter(WeatherSurfaceState::epoch),
        Codec.DOUBLE.fieldOf("progress_at_epoch").forGetter(WeatherSurfaceState::progressAtEpoch),
        Codec.BOOL.fieldOf("raining").forGetter(WeatherSurfaceState::raining),
        Codec.BOOL.fieldOf("thundering").forGetter(WeatherSurfaceState::thundering)
    ).apply(instance, WeatherSurfaceState::new));
    private static final Codec<RegionalTrackWeatherData> CODEC = STATE_CODEC.listOf().optionalFieldOf("phases", List.of())
        .xmap(RegionalTrackWeatherData::new, data -> List.copyOf(data.states.values())).codec();
    private static final SavedDataType<RegionalTrackWeatherData> TYPE = new SavedDataType<>(
        "openwheelracing_regional_track_weather", () -> new RegionalTrackWeatherData(List.of()), CODEC, null);
    private final Map<String, WeatherSurfaceState> states = new HashMap<>();

    private RegionalTrackWeatherData(List<WeatherSurfaceState> entries) {
        for (var state : entries) states.put(state.region().name(), state);
    }

    static RegionalTrackWeatherData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    TrackWeatherPhase.Sample sample(WeatherRegion region, long tick) {
        var previous = states.get(region.name());
        var next = previous == null ? WeatherSurfaceState.initial(region, tick)
            : previous.region().equals(region) ? previous.advance(tick) : previous.reconfigure(region, tick);
        if (!next.equals(previous)) {
            states.put(region.name(), next);
            setDirty();
        }
        return new TrackWeatherPhase.Sample(next.progress(), next.epoch(), next.progressAtEpoch(), !next.raining());
    }
}
