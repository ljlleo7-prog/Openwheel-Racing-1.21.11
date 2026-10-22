package com.openwheelracing.content.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

public final class RegionalWeather {
    private static String clientDimension = "";
    private static List<WeatherRegion> clientRegions = List.of();
    private static BlockPos camera = BlockPos.ZERO;
    private static final WeatherVisualTransition CLIENT_VISUALS = new WeatherVisualTransition();

    private RegionalWeather() {
    }

    public static boolean enabled(Level level) {
        return level.canHaveWeather() && (level instanceof ServerLevel
            || clientDimension.equals(level.dimension().identifier().toString()) && !clientRegions.isEmpty());
    }

    public static WeatherRegion fallback(long seed) {
        return new WeatherRegion("default", Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE,
            Integer.MAX_VALUE, 0.3, 0.2, 0, 12000, seed);
    }

    public static List<WeatherRegion> regions(ServerLevel level) {
        var result = new java.util.ArrayList<WeatherRegion>();
        result.add(fallback(level.getSeed() ^ level.dimension().identifier().hashCode()));
        result.addAll(RegionalWeatherData.get(level).regions());
        return List.copyOf(result);
    }

    public static WeatherRegion region(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            return RegionalWeatherData.get(server).regionAt(pos.getX(), pos.getZ(), server.getSeed() ^ level.dimension().identifier().hashCode());
        }
        List<WeatherRegion> regions = clientRegions;
        for (int i = regions.size() - 1; i >= 0; i--) {
            if (regions.get(i).contains(pos.getX(), pos.getZ())) return regions.get(i);
        }
        return fallback(0);
    }

    public static WeatherSchedule.Sample sample(Level level, BlockPos pos) {
        return WeatherSchedule.sample(region(level, pos), level.getGameTime());
    }

    public static Biome.Precipitation precipitation(Level level, BlockPos pos) {
        return switch (sample(level, pos).condition()) {
            case CLEAR -> Biome.Precipitation.NONE;
            case SNOW -> Biome.Precipitation.SNOW;
            default -> Biome.Precipitation.RAIN;
        };
    }

    public static boolean exposed(Level level, BlockPos pos) {
        return level.canSeeSky(pos) && level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY();
    }

    /** Dimension-level gates must allow precipitation in any wet region. */
    public static boolean anyWet(Level level, boolean thunder) {
        if (!(level instanceof ServerLevel server)) {
            var sample = sample(level, camera);
            return thunder ? sample.thunder() : sample.wet();
        }
        var fallback = WeatherSchedule.sample(fallback(server.getSeed() ^ level.dimension().identifier().hashCode()), level.getGameTime());
        return (thunder ? fallback.thunder() : fallback.wet()) || RegionalWeatherData.get(server).anyWet(level.getGameTime(), thunder);
    }

    public static void tickClientVisuals(Level level) {
        if (enabled(level)) CLIENT_VISUALS.tick(sample(level, camera).condition());
        else CLIENT_VISUALS.reset();
    }

    public static float rainLevel(Level level, float partialTick) {
        return level instanceof ServerLevel ? (anyWet(level, false) ? 1 : 0) : CLIENT_VISUALS.intensity(partialTick);
    }

    public static float thunderLevel(Level level, float partialTick) {
        return level instanceof ServerLevel ? (anyWet(level, true) ? 1 : 0) : CLIENT_VISUALS.thunder(partialTick);
    }

    public static Biome.Precipitation visualPrecipitation() {
        return switch (CLIENT_VISUALS.displayed()) {
            case CLEAR -> Biome.Precipitation.NONE;
            case SNOW -> Biome.Precipitation.SNOW;
            default -> Biome.Precipitation.RAIN;
        };
    }

    public static void updateCamera(BlockPos pos) { camera = pos.immutable(); }

    public static void applyClient(String dimension, List<WeatherRegion> regions) {
        if (!clientDimension.equals(dimension)) CLIENT_VISUALS.reset();
        clientDimension = dimension;
        clientRegions = List.copyOf(regions);
    }

    public static void clearClient() {
        CLIENT_VISUALS.reset();
        clientDimension = "";
        clientRegions = List.of();
        camera = BlockPos.ZERO;
    }

    public static List<Component> forecastLines(Level level, BlockPos pos) {
        var region = region(level, pos);
        long now = level.getGameTime();
        var current = WeatherSchedule.sample(region, now);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("weather.openwheelracing.forecast.region", region.name()));
        for (int i = 0; i < 4; i++) {
            var sample = i == 0 ? current : WeatherSchedule.sample(region, current.endsAt() + (long) (i - 1) * region.periodTicks());
            var condition = Component.translatable("weather.openwheelracing." + sample.condition().name().toLowerCase(java.util.Locale.ROOT));
            lines.add(i == 0 ? Component.translatable("weather.openwheelracing.forecast.now", condition, (sample.endsAt() - now + 19) / 20)
                : Component.translatable("weather.openwheelracing.forecast.later", (sample.startsAt() - now + 19) / 20, condition));
        }
        return List.copyOf(lines);
    }
}
