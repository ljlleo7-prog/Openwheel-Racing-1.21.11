package com.openwheelracing.content.race.timing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveRaceClassificationEngineTest {
    private static final double ROUTE_LENGTH = 100.0;
    private static final RaceParticipantKey ALPHA = key("00000000-0000-0000-0000-000000000001", RaceParticipantKind.PLAYER);
    private static final RaceParticipantKey BRAVO = key("00000000-0000-0000-0000-000000000002", RaceParticipantKind.AI);

    @Test
    void ordersInitialParticipantsByRouteDistance() {
        LiveRaceClassificationEngine engine = new LiveRaceClassificationEngine();

        LiveRaceTimingSnapshot snapshot = engine.advance(ROUTE_LENGTH, 1, 50, List.of(
            observation(ALPHA, "Alpha", 20, 1, 50, RaceProgressConfidence.CONFIRMED),
            observation(BRAVO, "Bravo", 60, 1, 50, RaceProgressConfidence.CONFIRMED)
        ));

        assertEquals(BRAVO, snapshot.rows().get(0).participant());
        assertEquals(ALPHA, snapshot.rows().get(1).participant());
        assertEquals(0, snapshot.rows().get(0).completedLaps());
    }

    @Test
    void forwardSeamCrossingIncrementsOneLap() {
        LiveRaceClassificationEngine engine = new LiveRaceClassificationEngine();
        engine.advance(ROUTE_LENGTH, 1, 50, List.of(observation(ALPHA, "Alpha", 90, 1, 50, RaceProgressConfidence.CONFIRMED)));

        LiveRaceTimingSnapshot snapshot = engine.advance(ROUTE_LENGTH, 2, 100, List.of(
            observation(ALPHA, "Alpha", 3, 2, 100, RaceProgressConfidence.CONFIRMED)
        ));

        assertEquals(1, snapshot.rows().getFirst().completedLaps());
        assertEquals(103.0, snapshot.rows().getFirst().absoluteProgressMeters(), 1.0E-6);
    }

    @Test
    void ambiguousObservationHoldsConfirmedProgressAndOrder() {
        LiveRaceClassificationEngine engine = new LiveRaceClassificationEngine();
        engine.advance(ROUTE_LENGTH, 1, 50, List.of(
            observation(ALPHA, "Alpha", 60, 1, 50, RaceProgressConfidence.CONFIRMED),
            observation(BRAVO, "Bravo", 55, 1, 50, RaceProgressConfidence.CONFIRMED)
        ));

        LiveRaceTimingSnapshot snapshot = engine.advance(ROUTE_LENGTH, 2, 100, List.of(
            observation(ALPHA, "Alpha", 60, 2, 100, RaceProgressConfidence.AMBIGUOUS),
            observation(BRAVO, "Bravo", 58, 2, 100, RaceProgressConfidence.CONFIRMED)
        ));

        assertEquals(ALPHA, snapshot.rows().get(0).participant());
        assertEquals(RaceProgressConfidence.AMBIGUOUS, snapshot.rows().get(0).confidence());
    }

    @Test
    void closeSwapRequiresFourConsistentTicks() {
        LiveRaceClassificationEngine engine = new LiveRaceClassificationEngine(4.0, 1.5, 4);
        engine.advance(ROUTE_LENGTH, 1, 50, List.of(
            observation(ALPHA, "Alpha", 50, 1, 50, RaceProgressConfidence.CONFIRMED),
            observation(BRAVO, "Bravo", 49, 1, 50, RaceProgressConfidence.CONFIRMED)
        ));

        for (int tick = 2; tick <= 4; tick++) {
            LiveRaceTimingSnapshot snapshot = engine.advance(ROUTE_LENGTH, tick, tick * 50L, List.of(
                observation(ALPHA, "Alpha", 50 + tick, tick, tick * 50L, RaceProgressConfidence.CONFIRMED),
                observation(BRAVO, "Bravo", 50.5 + tick, tick, tick * 50L, RaceProgressConfidence.CONFIRMED)
            ));
            assertEquals(ALPHA, snapshot.rows().getFirst().participant());
        }

        LiveRaceTimingSnapshot confirmed = engine.advance(ROUTE_LENGTH, 5, 250, List.of(
            observation(ALPHA, "Alpha", 55, 5, 250, RaceProgressConfidence.CONFIRMED),
            observation(BRAVO, "Bravo", 55.5, 5, 250, RaceProgressConfidence.CONFIRMED)
        ));
        assertEquals(BRAVO, confirmed.rows().getFirst().participant());
        assertEquals(2, confirmed.recentPositionChanges().size());
    }

    @Test
    void implausibleForwardJumpHoldsConfirmedProgress() {
        LiveRaceClassificationEngine engine = new LiveRaceClassificationEngine();
        engine.advance(200.0, 1, 50, List.of(observation(ALPHA, "Alpha", 20, 1, 50, RaceProgressConfidence.CONFIRMED)));

        LiveRaceTimingSnapshot snapshot = engine.advance(200.0, 2, 100, List.of(
            observation(ALPHA, "Alpha", 150, 2, 100, RaceProgressConfidence.CONFIRMED)
        ));

        assertEquals(20.0, snapshot.rows().getFirst().routeDistanceMeters(), 1.0E-6);
        assertEquals(RaceProgressConfidence.DEGRADED, snapshot.rows().getFirst().confidence());
    }

    @Test
    void missingParticipantRebindsWithinGraceWindow() {
        LiveRaceClassificationEngine engine = new LiveRaceClassificationEngine();
        engine.advance(ROUTE_LENGTH, 1, 50, List.of(observation(ALPHA, "Alpha", 40, 1, 50, RaceProgressConfidence.CONFIRMED)));

        LiveRaceTimingSnapshot missing = engine.advance(ROUTE_LENGTH, 2, 100, List.of());
        assertEquals(1, missing.rows().size());
        assertEquals(RaceProgressConfidence.STALE, missing.rows().getFirst().confidence());

        LiveRaceTimingSnapshot rebound = engine.advance(ROUTE_LENGTH, 100, 5_000, List.of(
            observation(ALPHA, "Alpha", 45, 100, 5_000, RaceProgressConfidence.CONFIRMED)
        ));
        assertEquals(45.0, rebound.rows().getFirst().routeDistanceMeters(), 1.0E-6);
        assertEquals(0, rebound.rows().getFirst().completedLaps());
    }

    @Test
    void permanentlyMissingParticipantRetiresAfterFiveMinutes() {
        LiveRaceClassificationEngine engine = new LiveRaceClassificationEngine();
        engine.advance(ROUTE_LENGTH, 1, 50, List.of(
            observation(ALPHA, "Alpha", 60, 1, 50, RaceProgressConfidence.CONFIRMED),
            observation(BRAVO, "Bravo", 40, 1, 50, RaceProgressConfidence.CONFIRMED)
        ));

        engine.advance(ROUTE_LENGTH, 2, 100, List.of(
            observation(ALPHA, "Alpha", 61, 2, 100, RaceProgressConfidence.CONFIRMED)
        ));
        LiveRaceTimingSnapshot beforeDeadline = engine.advance(ROUTE_LENGTH, 6_002, 300_100, List.of(
            observation(ALPHA, "Alpha", 65, 6_002, 300_100, RaceProgressConfidence.CONFIRMED)
        ));
        assertEquals(2, beforeDeadline.rows().size());

        LiveRaceTimingSnapshot retired = engine.advance(ROUTE_LENGTH, 6_003, 300_150, List.of(
            observation(ALPHA, "Alpha", 66, 6_003, 300_150, RaceProgressConfidence.CONFIRMED)
        ));
        assertEquals(List.of(ALPHA), retired.rows().stream().map(RaceTimingRow::participant).toList());
        assertTrue(retired.recentPositionChanges().isEmpty());
    }

    @Test
    void staleParticipantCanRecoverAcrossStartFinishSeam() {
        LiveRaceClassificationEngine engine = new LiveRaceClassificationEngine();
        engine.advance(ROUTE_LENGTH, 1, 50, List.of(observation(ALPHA, "Alpha", 98, 1, 50, RaceProgressConfidence.CONFIRMED)));
        engine.advance(ROUTE_LENGTH, 2, 100, List.of());

        LiveRaceTimingSnapshot rebound = engine.advance(ROUTE_LENGTH, 120, 6_000, List.of(
            observation(ALPHA, "Alpha", 2, 120, 6_000, RaceProgressConfidence.CONFIRMED)
        ));

        assertEquals(1, rebound.rows().getFirst().completedLaps());
        assertEquals(102.0, rebound.rows().getFirst().absoluteProgressMeters(), 1.0E-6);
    }

    @Test
    void lateJoinRebasesOrderWithoutPositionChangeEvents() {
        RaceParticipantKey charlie = key("00000000-0000-0000-0000-000000000003", RaceParticipantKind.AI);
        LiveRaceClassificationEngine engine = new LiveRaceClassificationEngine();
        engine.advance(ROUTE_LENGTH, 1, 50, List.of(
            observation(ALPHA, "Alpha", 60, 1, 50, RaceProgressConfidence.CONFIRMED),
            observation(BRAVO, "Bravo", 40, 1, 50, RaceProgressConfidence.CONFIRMED)
        ));

        LiveRaceTimingSnapshot joined = engine.advance(ROUTE_LENGTH, 2, 100, List.of(
            observation(ALPHA, "Alpha", 62, 2, 100, RaceProgressConfidence.CONFIRMED),
            new RaceTimingObservation(charlie, "Charlie", 3, 50, 10, RaceProgressConfidence.CONFIRMED, 2, 100, 3),
            observation(BRAVO, "Bravo", 42, 2, 100, RaceProgressConfidence.CONFIRMED)
        ));

        assertEquals(List.of(ALPHA, charlie, BRAVO), joined.rows().stream().map(RaceTimingRow::participant).toList());
        assertTrue(joined.recentPositionChanges().isEmpty());
    }

    @Test
    void timeGapUsesSameRouteReference() {
        LiveRaceClassificationEngine engine = new LiveRaceClassificationEngine();
        engine.advance(ROUTE_LENGTH, 1, 0, List.of(
            observation(ALPHA, "Alpha", 0, 1, 1, RaceProgressConfidence.CONFIRMED),
            observation(BRAVO, "Bravo", 0, 1, 1, RaceProgressConfidence.CONFIRMED)
        ));
        engine.advance(ROUTE_LENGTH, 2, 1_000, List.of(
            observation(ALPHA, "Alpha", 20, 2, 1_000, RaceProgressConfidence.CONFIRMED),
            observation(BRAVO, "Bravo", 12, 2, 1_000, RaceProgressConfidence.CONFIRMED)
        ));
        LiveRaceTimingSnapshot snapshot = engine.advance(ROUTE_LENGTH, 3, 2_000, List.of(
            observation(ALPHA, "Alpha", 40, 3, 2_000, RaceProgressConfidence.CONFIRMED),
            observation(BRAVO, "Bravo", 20, 3, 2_000, RaceProgressConfidence.CONFIRMED)
        ));

        RaceTimingRow bravo = snapshot.rows().stream().filter(row -> row.participant().equals(BRAVO)).findFirst().orElseThrow();
        assertEquals(RaceGap.Type.TIME_MILLIS, bravo.gapToLeader().type());
        assertTrue(bravo.gapToLeader().millis() >= 900 && bravo.gapToLeader().millis() <= 1_100);
    }

    @Test
    void lappedParticipantReportsLapGap() {
        LiveRaceClassificationEngine engine = new LiveRaceClassificationEngine();
        engine.advance(ROUTE_LENGTH, 1, 50, List.of(
            observation(ALPHA, "Alpha", 90, 1, 50, RaceProgressConfidence.CONFIRMED),
            observation(BRAVO, "Bravo", 5, 1, 50, RaceProgressConfidence.CONFIRMED)
        ));
        engine.advance(ROUTE_LENGTH, 2, 100, List.of(
            observation(ALPHA, "Alpha", 5, 2, 100, RaceProgressConfidence.CONFIRMED),
            observation(BRAVO, "Bravo", 8, 2, 100, RaceProgressConfidence.CONFIRMED)
        ));

        RaceTimingRow bravo = engine.advance(ROUTE_LENGTH, 3, 150, List.of(
            observation(ALPHA, "Alpha", 10, 3, 150, RaceProgressConfidence.CONFIRMED),
            observation(BRAVO, "Bravo", 9, 3, 150, RaceProgressConfidence.CONFIRMED)
        )).rows().stream().filter(row -> row.participant().equals(BRAVO)).findFirst().orElseThrow();

        assertEquals(RaceGap.Type.LAPS, bravo.gapToLeader().type());
        assertEquals(1, bravo.gapToLeader().laps());
    }

    private static RaceTimingObservation observation(RaceParticipantKey key, String name, double routeDistance, long tick, long millis,
                                                     RaceProgressConfidence confidence) {
        return new RaceTimingObservation(key, name, key.equals(ALPHA) ? 1 : 2, routeDistance, 10.0, confidence, tick, millis,
            key.equals(ALPHA) ? 1 : 2);
    }

    private static RaceParticipantKey key(String id, RaceParticipantKind kind) {
        return new RaceParticipantKey(UUID.fromString(id), kind);
    }
}
