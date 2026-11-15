package com.openwheelracing.content.race;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContinuousLapCoverageTest {
    @Test void mergesFractionalIntervalsAndDoesNotInflateCoverageWhenReversing() {
        var replay = new Replay(1000, 0);
        replay.walk(10.125, 100.875, true);
        replay.walk(100.875, 50.25, true);
        replay.walk(50.25, 80.75, true);
        assertEquals(List.of(new ContinuousLapCoverage.Interval(10.125, 100.875)), replay.coverage.intervals());
        assertEquals(90.75 / 1000, replay.coverage.coverage(), 1e-9);
    }

    @Test void aFullCircuitRecoversOnceAndKeepsOnlyTheDeparturePrefix() {
        var replay = new Replay(1000, 0);
        replay.walk(0, 1005, true);
        assertEquals(1, replay.crossings.size());
        assertEquals(1, replay.crossings.getFirst().coverage(), 1e-9);
        assertEquals(.005, replay.coverage.coverage(), 1e-9);
        replay.walk(1005, 995, true);
        replay.walk(995, 1005, true);
        assertEquals(1, replay.crossings.size(), "Finish-line oscillation must not add another lap");
    }

    @Test void wallReturnAndSpinPreserveDistinctCoverage() {
        var replay = new Replay(1000, 0);
        replay.walk(0, 600, true);
        replay.walk(600, 570, true);
        replay.walk(570, 570, true);
        replay.walk(570, 650, true);
        replay.walk(650, 1005, true);
        assertEquals(1, replay.crossings.size());
        assertEquals(1, replay.crossings.getFirst().coverage(), 1e-9);
    }

    @Test void aBriefUntrackedExcursionLeavesAHoleRatherThanErasingTheLap() {
        var replay = new Replay(1000, 0);
        replay.walk(0, 450, true);
        replay.walk(450, 520, false);
        replay.walk(520, 1005, true);
        assertEquals(1, replay.crossings.size());
        assertTrue(replay.crossings.getFirst().coverage() >= .90);
        assertTrue(replay.crossings.getFirst().coverage() < 1, "Untracked intervals must not be credited");
    }

    @Test void coverageOfOnlyAShortRepeatedSectionCannotCompensateALap() {
        var replay = new Replay(1000, 0);
        for (int i = 0; i < 100; i++) { replay.walk(0, 100, true); replay.walk(100, 0, true); }
        replay.walk(0, -10, true);
        replay.walk(-10, 10, true);
        assertTrue(replay.crossings.isEmpty());
        assertTrue(replay.coverage.coverage() < .12);
    }

    @Test void usesConfiguredFinishOffsetRatherThanTheSurveyZero() {
        var replay = new Replay(1000, 250);
        replay.walk(250, 1255, true);
        assertEquals(1, replay.crossings.size());
        assertEquals(.005, replay.coverage.coverage(), 1e-9);
    }

    @Test void teleportClearsExistingCoverageAndCannotFinishTheOldLap() {
        var replay = new Replay(1000, 0);
        replay.walk(0, 950, true);
        replay.coverage.sample(new ContinuousLapCoverage.Sample(990, ++replay.tick, 5000, 0, 5000, true));
        replay.walk(990, 1005, true);
        assertTrue(replay.crossings.isEmpty());
        assertTrue(replay.coverage.coverage() < .03);
    }

    @Test void localizationJumpDoesNotFillTheArcBetweenDistantRoutePositions() {
        var coverage = new ContinuousLapCoverage(1000, 0);
        coverage.sample(new ContinuousLapCoverage.Sample(10, 0, 0, 0, 0, true));
        coverage.sample(new ContinuousLapCoverage.Sample(400, 1, 1, 0, 0, true));
        assertEquals(0, coverage.coverage());
    }

    @Test void longSamplingGapsDoNotCreateCoverageAndSameTickMovesRemainUsable() {
        var coverage = new ContinuousLapCoverage(1000, 0);
        coverage.sample(new ContinuousLapCoverage.Sample(10, 0, 0, 0, 0, true));
        coverage.sample(new ContinuousLapCoverage.Sample(15, 20, 5, 0, 0, true));
        assertEquals(0, coverage.coverage());
        coverage.sample(new ContinuousLapCoverage.Sample(20, 20, 10, 0, 0, true));
        assertEquals(.005, coverage.coverage(), 1e-9);
    }

    private static final class Replay {
        final ContinuousLapCoverage coverage;
        final List<ContinuousLapCoverage.Crossing> crossings = new ArrayList<>();
        final double length;
        long tick;
        Replay(double length, double finish) { this.length = length; coverage = new ContinuousLapCoverage(length, finish); }
        void walk(double from, double to, boolean reliable) {
            sample(from, reliable);
            double direction = Math.signum(to - from);
            for (double at = from; Math.abs(to - at) > 1e-9;) {
                at += direction * Math.min(5, Math.abs(to - at));
                sample(at, reliable);
            }
        }
        void sample(double s, boolean reliable) {
            double angle = s / length * Math.PI * 2;
            double radius = length / (Math.PI * 2);
            coverage.sample(new ContinuousLapCoverage.Sample(s, ++tick, Math.cos(angle) * radius, 0,
                Math.sin(angle) * radius, reliable)).ifPresent(crossings::add);
        }
    }
}
