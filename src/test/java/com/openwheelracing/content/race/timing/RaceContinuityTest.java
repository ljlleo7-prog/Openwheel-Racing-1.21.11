package com.openwheelracing.content.race.timing;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RaceContinuityTest {
    private static final RaceParticipantKey A = new RaceParticipantKey(new UUID(0, 1), RaceParticipantKind.PLAYER);
    private static final RaceParticipantKey B = new RaceParticipantKey(new UUID(0, 2), RaceParticipantKind.PLAYER);

    @Test void uncertainOrderIsHeldButReliableOvertakeIsAppliedOnReacquisition() {
        var engine = new LiveRaceClassificationEngine();
        engine.setRaceContinuity(true);
        step(engine, 1, 200, 190, RaceProgressConfidence.CONFIRMED);
        for (int tick = 2; tick <= 40; tick++) {
            var snapshot = step(engine, tick, 200, 190 + tick, RaceProgressConfidence.UNTRACKED);
            assertEquals(A, snapshot.rows().getFirst().participant());
            assertEquals(RaceGap.Type.UNAVAILABLE, snapshot.rows().get(1).intervalAhead().type());
        }
        // A really stopped and B really passed. Do not keep the stale order once observed.
        var recovered = step(engine, 41, 200, 231, RaceProgressConfidence.CONFIRMED);
        assertEquals(B, recovered.rows().getFirst().participant());
    }

    @Test void missingRaceParticipantDoesNotSilentlyRetireAfterFiveMinutes() {
        var engine = new LiveRaceClassificationEngine();
        engine.setRaceContinuity(true);
        step(engine, 1, 200, 190, RaceProgressConfidence.CONFIRMED);
        engine.advance(1000, 2, 100, List.of(obs(B, 190, 2, RaceProgressConfidence.CONFIRMED)));
        var snapshot = engine.advance(1000, 7000, 350000, List.of(obs(B, 190, 7000, RaceProgressConfidence.CONFIRMED)));
        assertEquals(2, snapshot.rows().size());
        assertEquals(A, snapshot.rows().getFirst().participant());
        assertEquals(RaceProgressConfidence.STALE, snapshot.rows().getFirst().confidence());
    }

    @Test void confirmedFinishCreditOverridesHeldSameLapOrder() {
        var engine = new LiveRaceClassificationEngine();
        engine.setRaceContinuity(true);
        step(engine, 1, 200, 190, RaceProgressConfidence.CONFIRMED);
        engine.reconcileCompletedLaps(B, 1);
        var snapshot = step(engine, 2, 200, 0, RaceProgressConfidence.UNTRACKED);
        assertEquals(B, snapshot.rows().getFirst().participant());
    }

    @Test void sharedRouteIntervalsStayContinuousAcrossFinishCrossings() {
        var engine = new LiveRaceClassificationEngine();
        engine.setRaceContinuity(true);
        for (int tick = 1; tick <= 700; tick++) {
            var snapshot = step(engine, tick, (200 + tick * 4) % 1000, (190 + tick * 4) % 1000,
                RaceProgressConfidence.CONFIRMED);
            if (tick > 10) {
                assertEquals(A, snapshot.rows().getFirst().participant());
                RaceGap interval = snapshot.rows().get(1).intervalAhead();
                assertEquals(RaceGap.Type.TIME_MILLIS, interval.type());
                assertEquals(125, interval.millis());
            }
        }
    }

    private static LiveRaceTimingSnapshot step(LiveRaceClassificationEngine engine, long tick, double a, double b, RaceProgressConfidence confidence) {
        return engine.advance(1000, tick, tick * 50, List.of(obs(A, a, tick, confidence), obs(B, b, tick, RaceProgressConfidence.CONFIRMED)));
    }
    private static RaceTimingObservation obs(RaceParticipantKey key, double distance, long tick, RaceProgressConfidence confidence) {
        return new RaceTimingObservation(key, key.equals(A) ? "A" : "B", key.equals(A) ? 1 : 2, distance, 20, confidence, tick, tick * 50, 0);
    }
}
