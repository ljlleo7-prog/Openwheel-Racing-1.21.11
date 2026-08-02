package com.openwheelracing.content.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class TrackDefinitionsData extends SavedData {
    private static final Codec<TrackDefinitionsData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(Codec.STRING, TrackDefinition.CODEC).optionalFieldOf("tracks", Map.of()).forGetter(TrackDefinitionsData::packedTracks),
        TrackDefinition.UUID_CODEC.optionalFieldOf("active_track").forGetter(TrackDefinitionsData::activeTrackId)
    ).apply(instance, TrackDefinitionsData::new));

    private static final SavedDataType<TrackDefinitionsData> TYPE = new SavedDataType<>(
        OpenwheelRacing.MODID + "_track_definitions",
        TrackDefinitionsData::new,
        CODEC,
        null
    );

    private final Map<UUID, TrackDefinition> tracks = new HashMap<>();
    private Optional<UUID> activeTrackId = Optional.empty();
    private int revision;

    public TrackDefinitionsData() {
    }

    private TrackDefinitionsData(Map<String, TrackDefinition> tracks, Optional<UUID> activeTrackId) {
        tracks.forEach((key, definition) -> {
            try {
                UUID id = UUID.fromString(key);
                this.tracks.put(id, definition);
            } catch (IllegalArgumentException ignored) {
                this.tracks.put(definition.trackId(), definition);
            }
        });
        this.activeTrackId = activeTrackId.filter(this.tracks::containsKey);
    }

    public static TrackDefinitionsData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static TrackDefinitionsData getIfPresent(ServerLevel level) {
        return level.getDataStorage().get(TYPE);
    }

    public static void importLegacy(ServerLevel level, TrackDefinitionsData legacy, String dimensionId) {
        if (legacy == null || getIfPresent(level) != null) {
            return;
        }
        List<TrackDefinition> tracks = legacy.tracksInDimension(dimensionId);
        if (tracks.isEmpty()) {
            return;
        }
        TrackDefinitionsData copy = new TrackDefinitionsData();
        for (TrackDefinition track : tracks) {
            copy.upsert(track);
        }
        legacy.activeTrack(dimensionId).ifPresent(track -> copy.setActiveTrack(track.trackId(), dimensionId));
        copy.markChanged();
        level.getDataStorage().set(TYPE, copy);
    }

    public int getRevision() {
        return revision;
    }

    public List<TrackDefinition> tracks() {
        return tracks.values().stream()
            .sorted(Comparator.comparing(TrackDefinition::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public Optional<TrackDefinition> get(UUID trackId) {
        return Optional.ofNullable(tracks.get(trackId));
    }

    public Optional<TrackDefinition> activeTrack() {
        return activeTrackId.flatMap(this::get);
    }

    public Optional<TrackDefinition> activeTrack(String dimensionId) {
        return activeTrack().filter(track -> track.dimensionId().equals(dimensionId));
    }

    public UUID upsert(TrackDefinition definition) {
        tracks.put(definition.trackId(), definition);
        if (activeTrackId.isEmpty() || activeTrack(definition.dimensionId()).isEmpty()) {
            activeTrackId = Optional.of(definition.trackId());
        }
        markChanged();
        return definition.trackId();
    }

    public TrackDefinition createEmpty(String name, String dimensionId) {
        TrackDefinition definition = TrackDefinition.empty(UUID.randomUUID(), name, dimensionId);
        upsert(definition);
        return definition;
    }

    public boolean remove(UUID trackId, String dimensionId) {
        TrackDefinition track = tracks.get(trackId);
        if (track == null || !track.dimensionId().equals(dimensionId)) {
            return false;
        }
        tracks.remove(trackId);
        if (activeTrackId.filter(trackId::equals).isPresent()) {
            activeTrackId = tracks.values().stream()
                .filter(definition -> definition.dimensionId().equals(dimensionId))
                .map(TrackDefinition::trackId)
                .findFirst();
        }
        markChanged();
        return true;
    }

    public boolean remove(UUID trackId) {
        if (tracks.remove(trackId) == null) {
            return false;
        }
        if (activeTrackId.filter(trackId::equals).isPresent()) {
            activeTrackId = tracks.keySet().stream().findFirst();
        }
        markChanged();
        return true;
    }

    public boolean setActiveTrack(UUID trackId, String dimensionId) {
        TrackDefinition track = tracks.get(trackId);
        if (track == null || !track.dimensionId().equals(dimensionId)) {
            return false;
        }
        activeTrackId = Optional.of(trackId);
        markChanged();
        return true;
    }

    public boolean setActiveTrack(UUID trackId) {
        if (!tracks.containsKey(trackId)) {
            return false;
        }
        activeTrackId = Optional.of(trackId);
        markChanged();
        return true;
    }

    public List<TrackDefinition> tracksInDimension(String dimensionId) {
        List<TrackDefinition> matches = new ArrayList<>();
        for (TrackDefinition definition : tracks.values()) {
            if (definition.dimensionId().equals(dimensionId)) {
                matches.add(definition);
            }
        }
        matches.sort(Comparator.comparing(TrackDefinition::name, String.CASE_INSENSITIVE_ORDER));
        return matches;
    }

    private Optional<UUID> activeTrackId() {
        return activeTrackId;
    }

    private Map<String, TrackDefinition> packedTracks() {
        Map<String, TrackDefinition> packed = new HashMap<>();
        tracks.forEach((trackId, definition) -> packed.put(trackId.toString(), definition));
        return packed;
    }

    private void markChanged() {
        revision++;
        setDirty();
    }
}
