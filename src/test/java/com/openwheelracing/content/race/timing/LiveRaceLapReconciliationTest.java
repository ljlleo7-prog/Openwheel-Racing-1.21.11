package com.openwheelracing.content.race.timing;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveRaceLapReconciliationTest {
    private static final double LENGTH = 1000;
    private static final RaceParticipantKey DRIVER = new RaceParticipantKey(new UUID(0, 901), RaceParticipantKind.PLAYER);

    @Test void recordFirstMustNotBeCountedAgainWhenThePositionTrackerLaterWraps() {
        var engine = new LiveRaceClassificationEngine();
        observe(engine, 990, 1, RaceProgressConfidence.CONFIRMED);
        assertTrue(engine.reconcileCompletedLaps(DRIVER, 1));
        observe(engine, 10, 2, RaceProgressConfidence.CONFIRMED);
        assertEquals(1, laps(engine));
    }

    @Test void trackerFirstAndRepeatedRecordUpdatesNeverAddTheSameLap() {
        var engine = new LiveRaceClassificationEngine();
        observe(engine, 990, 1, RaceProgressConfidence.CONFIRMED);
        observe(engine, 10, 2, RaceProgressConfidence.CONFIRMED);
        assertEquals(1, laps(engine));
        for (int i = 0; i < 20; i++) assertFalse(engine.reconcileCompletedLaps(DRIVER, 1));
        assertEquals(1, laps(engine));
        driveLap(engine, 20);
        assertEquals(2, laps(engine));
        assertFalse(engine.reconcileCompletedLaps(DRIVER, 1));
        assertFalse(engine.reconcileCompletedLaps(DRIVER, 2));
        assertEquals(2, laps(engine));
    }

    @Test void anEntireUntrackedLapCanBeRecoveredWithoutASecondCountOnReacquisition() {
        var engine = new LiveRaceClassificationEngine();
        observe(engine, 990, 1, RaceProgressConfidence.CONFIRMED);
        observe(engine, 10, 200, RaceProgressConfidence.UNTRACKED);
        assertEquals(0, laps(engine));
        assertTrue(engine.reconcileCompletedLaps(DRIVER, 1));
        observe(engine, 10, 201, RaceProgressConfidence.CONFIRMED);
        assertEquals(1, laps(engine));
        driveLap(engine, 210);
        assertEquals(2, laps(engine));
        assertFalse(engine.reconcileCompletedLaps(DRIVER, 2));
    }

    @Test void aCarNeverLocalizedBeforeItsRecordedFinishStillReceivesOneLap() {
        var engine = new LiveRaceClassificationEngine();
        observe(engine, 990, 1, RaceProgressConfidence.UNTRACKED);
        assertTrue(engine.reconcileCompletedLaps(DRIVER, 1));
        observe(engine, 10, 2, RaceProgressConfidence.UNTRACKED);
        assertEquals(1, laps(engine));
        observe(engine, 10, 3, RaceProgressConfidence.CONFIRMED);
        assertEquals(1, laps(engine));
    }

    @Test void directorAdjustmentsRemainSeparateFromTheRecoveredPassage() {
        var engine = new LiveRaceClassificationEngine();
        observe(engine, 990, 1, RaceProgressConfidence.CONFIRMED);
        assertTrue(engine.adjustCompletedLaps(DRIVER, 1));
        assertTrue(engine.reconcileCompletedLaps(DRIVER, 1));
        observe(engine, 10, 2, RaceProgressConfidence.CONFIRMED);
        assertEquals(2, laps(engine));
        assertTrue(engine.adjustCompletedLaps(DRIVER, -1));
        assertFalse(engine.reconcileCompletedLaps(DRIVER, 1));
        assertEquals(1, laps(engine));
        driveLap(engine, 20);
        assertEquals(2, laps(engine));
    }

    @Test void aMultiLapRecordFloorIsNotAddedToTheTrackerTotal() {
        var engine = new LiveRaceClassificationEngine();
        observe(engine, 990, 1, RaceProgressConfidence.CONFIRMED);
        assertTrue(engine.reconcileCompletedLaps(DRIVER, 3));
        observe(engine, 10, 2, RaceProgressConfidence.CONFIRMED);
        assertEquals(3, laps(engine));
        assertFalse(engine.reconcileCompletedLaps(DRIVER, 2));
        assertFalse(engine.reconcileCompletedLaps(DRIVER, 3));
        driveLap(engine, 20);
        assertEquals(4, laps(engine));
    }

    @Test void aggregateFloorsCannotIdentifyDifferentMissingPassages() {
        // Diagnostic of the remaining limitation, not a claim of complete recovery:
        // position sees laps 1/2; timer sees laps 2/3. Both totals are 2 for 3 real laps.
        var engine = new LiveRaceClassificationEngine();
        observe(engine, 0, 1, RaceProgressConfidence.CONFIRMED);
        driveLap(engine, 10); // Real lap 1: only the position source counted it.
        driveLap(engine, 220); // Real lap 2: both sources count, timer total is only 1.
        assertFalse(engine.reconcileCompletedLaps(DRIVER, 1));
        observe(engine, 990, 600, RaceProgressConfidence.UNTRACKED);
        observe(engine, 10, 610, RaceProgressConfidence.UNTRACKED);
        assertFalse(engine.reconcileCompletedLaps(DRIVER, 2)); // Real lap 3: only the timer saw it.
        assertEquals(2, laps(engine));
        System.out.println("Reconciliation diagnostic: 3 distinct real laps, tracker=2, records=2, aggregate result=2; event identities required.");
    }

    private static void driveLap(LiveRaceClassificationEngine engine, long start) {
        for (int i = 1; i <= 19; i++) observe(engine, i * 50, start + i * 10, RaceProgressConfidence.CONFIRMED);
        observe(engine, 10, start + 200, RaceProgressConfidence.CONFIRMED);
    }

    private static void observe(LiveRaceClassificationEngine engine, double distance, long tick, RaceProgressConfidence confidence) {
        engine.advance(LENGTH, tick, tick * 50, List.of(new RaceTimingObservation(DRIVER, "Driver", 42,
            distance, 80, confidence, tick, tick * 50, 1)));
    }
    private static int laps(LiveRaceClassificationEngine engine) { return engine.currentSnapshot(0).rows().getFirst().completedLaps(); }
}
