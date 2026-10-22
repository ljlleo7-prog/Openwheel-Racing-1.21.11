package com.openwheelracing.content.weather;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

public final class RegionalWeatherData extends SavedData {
    public static final Codec<WeatherRegion> REGION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("name").forGetter(WeatherRegion::name),
        Codec.INT.fieldOf("min_x").forGetter(WeatherRegion::minX),
        Codec.INT.fieldOf("min_z").forGetter(WeatherRegion::minZ),
        Codec.INT.fieldOf("max_x").forGetter(WeatherRegion::maxX),
        Codec.INT.fieldOf("max_z").forGetter(WeatherRegion::maxZ),
        Codec.doubleRange(0, 1).fieldOf("wet_chance").forGetter(WeatherRegion::wetChance),
        Codec.doubleRange(0, 1).fieldOf("storm_chance").forGetter(WeatherRegion::stormChance),
        Codec.doubleRange(0, 1).fieldOf("snow_chance").forGetter(WeatherRegion::snowChance),
        Codec.intRange(1200, 72000).fieldOf("period_ticks").forGetter(WeatherRegion::periodTicks),
        Codec.LONG.fieldOf("seed").forGetter(WeatherRegion::seed)
    ).apply(instance, WeatherRegion::new));
    private static final Codec<RegionalWeatherData> CODEC = REGION_CODEC.listOf().optionalFieldOf("regions", List.of())
        .xmap(RegionalWeatherData::new, RegionalWeatherData::regions).codec();
    private static final SavedDataType<RegionalWeatherData> TYPE = new SavedDataType<>(
        "openwheelracing_regional_weather", () -> new RegionalWeatherData(List.of()), CODEC, null);
    private final List<WeatherRegion> regions;

    private RegionalWeatherData(List<WeatherRegion> regions) {
        this.regions = new ArrayList<>(regions);
    }

    public static RegionalWeatherData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<WeatherRegion> regions() { return List.copyOf(regions); }

    public WeatherRegion regionAt(int x, int z, long fallbackSeed) {
        for (int i = regions.size() - 1; i >= 0; i--) {
            if (regions.get(i).contains(x, z)) return regions.get(i);
        }
        return RegionalWeather.fallback(fallbackSeed);
    }

    public boolean anyWet(long tick, boolean thunder) {
        for (var region : regions) {
            var sample = WeatherSchedule.sample(region, tick);
            if (thunder ? sample.thunder() : sample.wet()) return true;
        }
        return false;
    }

    public void put(WeatherRegion region) {
        if (regions.size() >= 128 && regions.stream().noneMatch(entry -> entry.name().equals(region.name()))) {
            throw new IllegalArgumentException("Maximum 128 weather regions per dimension");
        }
        regions.removeIf(entry -> entry.name().equals(region.name()));
        regions.add(region);
        setDirty();
    }

    public boolean remove(String name) {
        boolean removed = regions.removeIf(entry -> entry.name().equals(name));
        if (removed) setDirty();
        return removed;
    }
}
