package com.openwheelracing.content.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyTrackSelectionTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @Test void oldGlobalSelectionInAnotherDimensionDisablesOverworldLineUntilRecovered() throws Exception {
        var data = new TrackDefinitionsData();
        var track = track(OVERWORLD);
        data.upsert(track);
        var foreign = track(NETHER);
        data.upsert(foreign);
        data.setActiveTrack(foreign.trackId());
        var restored = roundTrip(data);
        assertTrue(restored.activeTrack(OVERWORLD).isEmpty());
        assertTrue(restored.recoverActiveTimingTrack(OVERWORLD));
        assertEquals(track, restored.activeTrack(OVERWORLD).orElseThrow());
        assertFalse(restored.recoverActiveTimingTrack(OVERWORLD));
    }

    @Test void missingLegacySelectionIsRecoveredButMultipleCandidatesAreNotGuessed() throws Exception {
        var data = new TrackDefinitionsData();
        var only = track(OVERWORLD);
        data.upsert(only);
        var encoded = codec().encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
        encoded.remove("active_track");
        var restored = codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(restored.recoverActiveTimingTrack(OVERWORLD));
        assertEquals(only.trackId(), restored.activeTrack().orElseThrow().trackId());
        data.upsert(track(OVERWORLD));
        encoded = codec().encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
        encoded.remove("active_track");
        restored = codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertFalse(restored.recoverActiveTimingTrack(OVERWORLD));
        assertTrue(restored.activeTrack().isEmpty());
    }

    @Test void explicitLocalSelectionIsPreservedEvenIfAnotherTrackHasALine() {
        var data = new TrackDefinitionsData();
        var empty = TrackDefinition.empty(UUID.randomUUID(), "Selected", OVERWORLD);
        data.upsert(empty);
        data.upsert(track(OVERWORLD));
        data.setActiveTrack(empty.trackId());
        assertFalse(data.recoverActiveTimingTrack(OVERWORLD));
        assertEquals(empty, data.activeTrack().orElseThrow());
    }

    private static TrackDefinition track(String dimension) {
        return new TrackDefinition(UUID.randomUUID(), "Legacy", dimension, List.of(),
            Optional.of(new TrackDefinition.StartFinishLine(new TrackDefinition.Point3(0, 64, -5),
                new TrackDefinition.Point3(0, 64, 5), 0)), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 1);
    }
    private static TrackDefinitionsData roundTrip(TrackDefinitionsData data) throws Exception {
        return codec().parse(JsonOps.INSTANCE, codec().encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
    }
    @SuppressWarnings("unchecked")
    private static Codec<TrackDefinitionsData> codec() throws Exception {
        var field = TrackDefinitionsData.class.getDeclaredField("CODEC");
        field.setAccessible(true);
        return (Codec<TrackDefinitionsData>) field.get(null);
    }
}
