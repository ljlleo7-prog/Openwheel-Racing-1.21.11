package com.openwheelracing.content.race.timing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LapCountCorrectionPersistenceTest {
    @Test
    @SuppressWarnings("unchecked")
    void correctionSurvivesCheckpointSerializationAndOldCheckpointsDefaultToZero() throws Exception {
        UUID driver = UUID.randomUUID();
        var data = new LiveRaceTimingData();
        var participant = new LiveRaceTimingData.SavedParticipant(driver, 0, "Driver", 2, 50, 1, false, -1);
        data.update(new LiveRaceTimingData.Checkpoint(true, true, "", 12, "Race", UUID.randomUUID(), UUID.randomUUID(),
            1, 10, List.of(driver), 1, List.of(participant)));
        var field = LiveRaceTimingData.class.getDeclaredField("CODEC");
        field.setAccessible(true);
        Codec<LiveRaceTimingData> codec = (Codec<LiveRaceTimingData>) field.get(null);
        var encoded = codec.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        var restored = codec.parse(JsonOps.INSTANCE, encoded).getOrThrow().checkpoint();
        assertEquals(participant, restored.participants().getFirst());
        assertEquals("SERVER_RECOVERY", restored.suspensionReason());

        var engine = new LiveRaceClassificationEngine();
        var key = new RaceParticipantKey(driver, RaceParticipantKind.PLAYER);
        var saved = restored.participants().getFirst();
        engine.restore(100, List.of(new LiveRaceClassificationEngine.RestoredProgress(key, saved.displayName(),
            saved.completedLaps(), saved.routeDistanceMeters(), saved.stablePosition(), saved.beforeRaceLine(), saved.manualLapAdjustment())));
        assertFalse(engine.reconcileCompletedLaps(key, 3));
        assertEquals(2, engine.currentSnapshot(10).rows().getFirst().completedLaps());

        encoded.getAsJsonObject().getAsJsonArray("participants").get(0).getAsJsonObject().remove("manual_lap_adjustment");
        var legacy = codec.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(0, legacy.checkpoint().participants().getFirst().manualLapAdjustment());
    }
}
