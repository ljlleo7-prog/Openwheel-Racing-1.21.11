package com.openwheelracing.content.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TrackMapData extends SavedData {
    private static final Codec<TrackMapSnapshot.CellRun> CELL_RUN_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("z").forGetter(TrackMapSnapshot.CellRun::z),
        Codec.INT.fieldOf("start_x").forGetter(TrackMapSnapshot.CellRun::startX),
        Codec.INT.fieldOf("end_x").forGetter(TrackMapSnapshot.CellRun::endX)
    ).apply(instance, TrackMapSnapshot.CellRun::new));
    private static final Codec<TrackMapSnapshot.MapPoint> MAP_POINT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("x").forGetter(TrackMapSnapshot.MapPoint::x),
        Codec.INT.fieldOf("z").forGetter(TrackMapSnapshot.MapPoint::z)
    ).apply(instance, TrackMapSnapshot.MapPoint::new));
    private static final Codec<StoredMap> STORED_MAP_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("source", "auto_detected").forGetter(StoredMap::source),
        Codec.STRING.optionalFieldOf("name", "Auto-Detected Track").forGetter(StoredMap::name),
        Codec.INT.optionalFieldOf("revision", 0).forGetter(StoredMap::revision),
        Codec.INT.fieldOf("min_x").forGetter(StoredMap::minX),
        Codec.INT.fieldOf("min_z").forGetter(StoredMap::minZ),
        Codec.INT.fieldOf("max_x").forGetter(StoredMap::maxX),
        Codec.INT.fieldOf("max_z").forGetter(StoredMap::maxZ),
        CELL_RUN_CODEC.listOf().optionalFieldOf("asphalt", List.of()).forGetter(StoredMap::asphaltRuns),
        CELL_RUN_CODEC.listOf().optionalFieldOf("pit", List.of()).forGetter(StoredMap::pitRuns),
        MAP_POINT_CODEC.listOf().optionalFieldOf("start_finish_markers", List.of()).forGetter(StoredMap::startFinishMarkers),
        MAP_POINT_CODEC.listOf().optionalFieldOf("checkpoint_markers", List.of()).forGetter(StoredMap::checkpointMarkers)
    ).apply(instance, StoredMap::new));
    private static final Codec<TrackMapData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(Codec.STRING, STORED_MAP_CODEC).optionalFieldOf("maps", Map.of()).forGetter(TrackMapData::maps)
    ).apply(instance, TrackMapData::new));
    private static final SavedDataType<TrackMapData> TYPE = new SavedDataType<>(
        OpenwheelRacing.MODID + "_track_maps",
        TrackMapData::new,
        CODEC,
        null
    );

    private final Map<String, StoredMap> maps = new HashMap<>();

    public TrackMapData() {
    }

    private TrackMapData(Map<String, StoredMap> maps) {
        this.maps.putAll(maps);
    }

    public static TrackMapData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<TrackMapSnapshot> snapshot(String dimensionId) {
        StoredMap map = maps.get(dimensionId);
        if (map == null) {
            return Optional.empty();
        }
        return Optional.of(map.snapshot(dimensionId));
    }

    public boolean clear(String dimensionId) {
        if (maps.remove(dimensionId) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public TrackMapSnapshot upsertAutoDetected(String dimensionId, String name, int minX, int minZ, int maxX, int maxZ, List<TrackMapSnapshot.CellRun> asphaltRuns, List<TrackMapSnapshot.CellRun> pitRuns, List<TrackMapSnapshot.MapPoint> startFinishMarkers, List<TrackMapSnapshot.MapPoint> checkpointMarkers) {
        int revision = maps.getOrDefault(dimensionId, StoredMap.empty()).revision() + 1;
        StoredMap map = new StoredMap("auto_detected", name, revision, minX, minZ, maxX, maxZ, asphaltRuns, pitRuns, startFinishMarkers, checkpointMarkers);
        maps.put(dimensionId, map);
        setDirty();
        return map.snapshot(dimensionId);
    }

    private Map<String, StoredMap> maps() {
        return maps;
    }

    private record StoredMap(String source, String name, int revision, int minX, int minZ, int maxX, int maxZ, List<TrackMapSnapshot.CellRun> asphaltRuns, List<TrackMapSnapshot.CellRun> pitRuns, List<TrackMapSnapshot.MapPoint> startFinishMarkers, List<TrackMapSnapshot.MapPoint> checkpointMarkers) {
        private StoredMap {
            source = source == null ? "auto_detected" : source;
            name = name == null || name.isBlank() ? "Auto-Detected Track" : name;
            asphaltRuns = List.copyOf(asphaltRuns == null ? List.of() : asphaltRuns);
            pitRuns = List.copyOf(pitRuns == null ? List.of() : pitRuns);
            startFinishMarkers = List.copyOf(startFinishMarkers == null ? List.of() : startFinishMarkers);
            checkpointMarkers = List.copyOf(checkpointMarkers == null ? List.of() : checkpointMarkers);
        }

        private static StoredMap empty() {
            return new StoredMap("auto_detected", "Auto-Detected Track", 0, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of());
        }

        private TrackMapSnapshot snapshot(String dimensionId) {
            return new TrackMapSnapshot(true, source, name, dimensionId, revision, minX, minZ, maxX, maxZ, asphaltRuns, pitRuns, startFinishMarkers, checkpointMarkers);
        }
    }
}
