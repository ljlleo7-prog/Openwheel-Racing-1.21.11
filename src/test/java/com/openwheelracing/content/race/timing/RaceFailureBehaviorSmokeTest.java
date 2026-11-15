package com.openwheelracing.content.race.timing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic fault injection against the production classifier, not a replacement model. */
class RaceFailureBehaviorSmokeTest {
    private static final int CARS = 12;
    private static final int TICKS = 2400;
    private static final double LENGTH = 1000;
    private static RaceParticipantKey key(int car) {
        return new RaceParticipantKey(new UUID(7, car + 1), RaceParticipantKind.PLAYER);
    }

    @Test void reproduceContinuityAndCountingFailuresIndependently() throws IOException {
        List<String> summary = new ArrayList<>();
        summary.add("fault,policy,final_undercredits,final_overcredits,wrong_leader_ticks,largest_rank_jump,longest_wrong_leader_run,missing_row_ticks,uncertain_row_ticks,undercredited_car_ticks,overcredited_car_ticks");
        List<String> trace = new ArrayList<>();
        trace.add("fault,policy,tick,true_laps,record_laps,automatic_laps,leader_position,confidence");
        for (String fault : List.of("control", "timer_10_percent", "untracked_short", "untracked_whole_lap",
            "entity_missing", "ambiguous", "route_jump", "timer_delayed", "timer_duplicate", "mixed_misses")) {
            for (String policy : List.of("position", "hybrid", "continuity")) {
                boolean reconcile = !policy.equals("position");
                Result result = simulate(fault, policy, trace);
                summary.add(fault + "," + policy + "," + result.csv());
                assertEquals(0, result.over, fault + " must not manufacture lap credits");
                if (fault.equals("control") || fault.equals("timer_10_percent") || fault.equals("timer_duplicate")) {
                    assertEquals(0, result.under, fault);
                    assertEquals(0, result.wrongLeader, fault);
                }
                if (fault.equals("untracked_whole_lap") && reconcile) assertEquals(0, result.under);
                if (fault.equals("untracked_short")) {
                    if (policy.equals("continuity")) {
                        assertEquals(0, result.wrongLeader);
                        assertEquals(0, result.jump);
                    } else assertTrue(result.wrongLeader > 0, "reproduce false overtakes during dropout");
                }
                if (policy.equals("continuity") && !fault.equals("mixed_misses")) {
                    assertEquals(0, result.wrongLeader, fault);
                    assertEquals(0, result.jump, fault);
                    assertEquals(0, result.under, fault);
                }
                if (fault.equals("mixed_misses") && reconcile) assertTrue(result.under > 0, "equal source totals hide distinct missed laps");
            }
        }
        Path reports = Path.of("build/reports");
        Files.createDirectories(reports);
        Files.write(reports.resolve("race-failure-behavior.csv"), summary);
        Files.write(reports.resolve("race-failure-trace.csv"), trace);
        summary.forEach(System.out::println);
    }

    private static Result simulate(String fault, String policy, List<String> trace) {
        var engine = new LiveRaceClassificationEngine();
        engine.setRaceContinuity(policy.equals("continuity"));
        boolean reconcile = !policy.equals("position");
        int[] records = new int[CARS];
        int[] previousTrue = new int[CARS];
        int[] previousRank = new int[CARS];
        Result result = new Result();
        int wrongRun = 0;
        for (int tick = 1; tick <= TICKS; tick++) {
            List<RaceTimingObservation> observations = new ArrayList<>();
            for (int car = 0; car < CARS; car++) {
                double progress = 200 - car * 10 + tick * 4.0;
                int lap = (int) (progress / LENGTH);
                boolean broken = car == 0 && tick >= 150 && tick < 450;
                boolean gap = car == 0 && tick >= 150 && tick < 190;
                boolean mixedGap = car == 0 && tick >= 650 && tick < 950;
                if (lap > previousTrue[car]) {
                    boolean miss = fault.equals("timer_10_percent") && (lap + car) % 10 == 0
                        || fault.equals("mixed_misses") && car == 0 && lap == 1;
                    if (!miss && !fault.equals("timer_delayed")) records[car]++;
                }
                if (fault.equals("timer_delayed")) {
                    records[car] = Math.max(0, (int) ((progress - 80) / LENGTH));
                }
                previousTrue[car] = lap;
                if (fault.equals("entity_missing") && broken) continue;
                RaceProgressConfidence confidence = RaceProgressConfidence.CONFIRMED;
                if (fault.equals("untracked_short") && gap || fault.equals("untracked_whole_lap") && broken
                    || fault.equals("mixed_misses") && mixedGap) confidence = RaceProgressConfidence.UNTRACKED;
                if (fault.equals("ambiguous") && broken) confidence = RaceProgressConfidence.AMBIGUOUS;
                double distance = progress % LENGTH;
                if (fault.equals("route_jump") && gap) distance = (distance + 450) % LENGTH;
                observations.add(new RaceTimingObservation(key(car), "Driver " + car, car + 1, distance, 80,
                    confidence, tick, tick * 50L, car));
            }
            var snapshot = engine.advance(LENGTH, tick, tick * 50L, observations);
            if (reconcile) {
                for (int car = 0; car < CARS; car++) {
                    engine.reconcileCompletedLaps(key(car), records[car]);
                    if (fault.equals("timer_duplicate")) engine.reconcileCompletedLaps(key(car), records[car]);
                }
                snapshot = engine.currentSnapshot(tick);
            }
            boolean wrong = !snapshot.rows().getFirst().participant().equals(key(0));
            if (wrong) { result.wrongLeader++; wrongRun++; } else wrongRun = 0;
            result.longestWrong = Math.max(result.longestWrong, wrongRun);
            for (int car = 0; car < CARS; car++) {
                final int id = car;
                var row = snapshot.rows().stream().filter(r -> r.participant().equals(key(id))).findFirst();
                if (row.isEmpty()) { result.missing++; continue; }
                var r = row.get();
                if (previousRank[car] > 0) result.jump = Math.max(result.jump, Math.abs(previousRank[car] - r.position()));
                previousRank[car] = r.position();
                if (r.confidence() != RaceProgressConfidence.CONFIRMED) result.uncertain++;
                if (car == 0 && tick % 10 == 0) trace.add(fault + "," + policy
                    + "," + tick + "," + previousTrue[car] + "," + records[car] + "," + r.completedLaps()
                    + "," + r.position() + "," + r.confidence());
                result.underTicks += Math.max(0, previousTrue[car] - r.completedLaps());
                result.overTicks += Math.max(0, r.completedLaps() - previousTrue[car]);
                if (tick == TICKS) {
                    result.under += Math.max(0, previousTrue[car] - r.completedLaps());
                    result.over += Math.max(0, r.completedLaps() - previousTrue[car]);
                }
            }
        }
        return result;
    }

    private static final class Result {
        int under, over, wrongLeader, jump, longestWrong, missing, uncertain, underTicks, overTicks;
        String csv() { return under + "," + over + "," + wrongLeader + "," + jump + "," + longestWrong + "," + missing + "," + uncertain + "," + underTicks + "," + overTicks; }
    }
}
