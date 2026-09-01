package com.openwheelracing.content.track.survey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.race.PitLaneSpeedMath;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PitLaneSurveyData extends SavedData {
    private static final Codec<PitLaneSpeedMath.Point> POINT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.DOUBLE.fieldOf("x").forGetter(PitLaneSpeedMath.Point::x),
        Codec.DOUBLE.fieldOf("y").forGetter(PitLaneSpeedMath.Point::y),
        Codec.DOUBLE.fieldOf("z").forGetter(PitLaneSpeedMath.Point::z)
    ).apply(instance, PitLaneSpeedMath.Point::new));
    private static final Codec<Route> ROUTE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        TrackDefinitionCodecs.UUID_CODEC.fieldOf("track_id").forGetter(Route::trackId),
        POINT_CODEC.listOf().fieldOf("points").forGetter(Route::points)
    ).apply(instance, Route::new));
    private static final Codec<PitLaneSurveyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(Codec.STRING, ROUTE_CODEC).optionalFieldOf("routes", Map.of()).forGetter(PitLaneSurveyData::packed)
    ).apply(instance, PitLaneSurveyData::new));
    private static final SavedDataType<PitLaneSurveyData> TYPE = new SavedDataType<>(OpenwheelRacing.MODID + "_pit_lane_surveys",
        PitLaneSurveyData::new, CODEC, null);

    private final Map<UUID, Route> routes = new HashMap<>();
    private int revision;

    public PitLaneSurveyData() {
    }

    private PitLaneSurveyData(Map<String, Route> saved) {
        saved.values().forEach(route -> routes.put(route.trackId(), route));
    }

    public static PitLaneSurveyData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Route> get(UUID trackId) { return Optional.ofNullable(routes.get(trackId)); }
    public int revision() { return revision; }

    public void put(Route route) {
        routes.put(route.trackId(), route);
        revision++;
        setDirty();
    }

    public boolean clear(UUID trackId) {
        if (routes.remove(trackId) == null) return false;
        revision++;
        setDirty();
        return true;
    }

    private Map<String, Route> packed() {
        Map<String, Route> packed = new HashMap<>();
        routes.forEach((id, route) -> packed.put(id.toString(), route));
        return packed;
    }

    public record Route(UUID trackId, List<PitLaneSpeedMath.Point> points) {
        public Route { points = List.copyOf(points); }
    }

    private static final class TrackDefinitionCodecs {
        private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    }
}
