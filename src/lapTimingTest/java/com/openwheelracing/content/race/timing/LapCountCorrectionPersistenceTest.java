package com.openwheelracing.content.race.timing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.openwheelracing.content.race.OWRLapRecords;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LapCountCorrectionPersistenceTest {
    @Test
    void aRecordFirstFinishAnchorSurvivesTheExistingCheckpointFormat() {
        UUID driver = UUID.randomUUID();
        var key = new RaceParticipantKey(driver, RaceParticipantKind.PLAYER);
        var engine = new LiveRaceClassificationEngine();
        engine.advance(1000, 1, 50, List.of(new RaceTimingObservation(key, "Driver", 7, 990, 80, RaceProgressConfidence.CONFIRMED, 1, 50, 1)));
        assertTrue(engine.reconcileCompletedLaps(key, 1));
        var row = engine.currentSnapshot(1).rows().getFirst();
        assertEquals(0, row.routeDistanceMeters());
        var saved = new LiveRaceTimingData.SavedParticipant(driver, 0, "Driver", row.completedLaps(), row.routeDistanceMeters(), 1, false, 0);
        var restored = new LiveRaceClassificationEngine();
        restored.restore(1000, List.of(new LiveRaceClassificationEngine.RestoredProgress(key, "Driver", saved.completedLaps(), saved.routeDistanceMeters(), 1, false, 0)));
        assertFalse(restored.reconcileCompletedLaps(key, 1));
        restored.advance(1000, 2, 100, List.of(new RaceTimingObservation(key, "Driver", 7, 10, 80, RaceProgressConfidence.CONFIRMED, 2, 100, 1)));
        assertEquals(1, restored.currentSnapshot(2).rows().getFirst().completedLaps());
    }

    @Test
    void compensatedRaceCreditPreservesDirectorAdjustmentsWithoutDoubleReconciliation() {
        UUID driver = UUID.randomUUID();
        var key = new RaceParticipantKey(driver, RaceParticipantKind.PLAYER);
        var engine = new LiveRaceClassificationEngine();
        engine.restore(200, List.of(new LiveRaceClassificationEngine.RestoredProgress(key, "Driver", 0, 20, 1, false, 0)));
        assertTrue(engine.adjustCompletedLaps(key, 1));
        var records = new OWRLapRecords();
        var car = new OWRLapRecords.CarSnapshot(1, 2, 3, 4, 0, 0, true);
        records.recordCompensatedLap(driver, "Driver", 60_000, 1200, "test", 0, car);
        int credited = records.getRaceSessionLaps(records.getActiveSessionId()).size();
        assertTrue(engine.reconcileCompletedLaps(key, credited));
        assertEquals(2, engine.currentSnapshot(1200).rows().getFirst().completedLaps());
        assertFalse(engine.reconcileCompletedLaps(key, credited));
        assertTrue(engine.adjustCompletedLaps(key, -1));
        assertEquals(1, engine.currentSnapshot(1200).rows().getFirst().completedLaps());
        records.recordLap(driver, "Driver", 65_000, 2500, "test", 0, 0, car);
        assertTrue(engine.reconcileCompletedLaps(key, records.getRaceSessionLaps(records.getActiveSessionId()).size()));
        assertEquals(2, engine.currentSnapshot(2500).rows().getFirst().completedLaps());
        assertEquals(65_000, records.getBestLap(driver));
    }

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
