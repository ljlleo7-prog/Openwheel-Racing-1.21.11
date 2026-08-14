package com.openwheelracing.content.track.survey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class TrackSurveyData extends SavedData {
    private static final Codec<TrackSurveyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(Codec.STRING, SurveyRoute.CODEC).optionalFieldOf("routes", Map.of()).forGetter(TrackSurveyData::packedRoutes)
    ).apply(instance, TrackSurveyData::new));
    private static final SavedDataType<TrackSurveyData> TYPE = new SavedDataType<>(
        OpenwheelRacing.MODID + "_track_surveys",
        TrackSurveyData::new,
        CODEC,
        null
    );

    private final Map<UUID, SurveyRoute> routes = new HashMap<>();
    private int revision;

    public TrackSurveyData() {
    }

    private TrackSurveyData(Map<String, SurveyRoute> routes) {
        routes.values().forEach(route -> this.routes.put(route.trackId(), route));
    }

    public static TrackSurveyData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<SurveyRoute> get(UUID trackId) {
        return Optional.ofNullable(routes.get(trackId));
    }

    public int revision() {
        return revision;
    }

    public void put(SurveyRoute route) {
        routes.put(route.trackId(), route);
        revision++;
        setDirty();
    }

    public boolean clear(UUID trackId) {
        if (routes.remove(trackId) == null) {
            return false;
        }
        revision++;
        setDirty();
        return true;
    }

    private Map<String, SurveyRoute> packedRoutes() {
        Map<String, SurveyRoute> packed = new HashMap<>();
        routes.forEach((trackId, route) -> packed.put(trackId.toString(), route));
        return packed;
    }
}
