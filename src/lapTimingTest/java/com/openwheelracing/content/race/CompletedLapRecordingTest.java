package com.openwheelracing.content.race;

import com.mojang.serialization.JsonOps;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackGeometry;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CompletedLapRecordingTest {
    private static final UUID DRIVER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void lineTriggeredLapRemainsValidWhenFirstTelemetrySampleIsFarFromTheLine() {
        double startedAt = LapTimingClock.crossingTime(100, 0.25);
        double finishedAt = LapTimingClock.crossingTime(1300, 0.4);
        int lapMillis = LapTimingClock.elapsedMillis(startedAt, finishedAt);
        var telemetry = new LapTelemetryRecorder();
        telemetry.startUntracked(DRIVER, "Driver", "test", startedAt);
        // Capturing can begin late and far beyond the start line without moving the timing origin.
        telemetry.sampleUntracked(120, 100, 64, 0, 100);
        telemetry.sampleUntracked(125, 107, 64, 0, 100);
        var snapshot = telemetry.finish(0, lapMillis).orElseThrow();
        assertEquals(10000, snapshot.worldXCm()[0]);
        assertEquals(1038, snapshot.elapsedMillis()[0]);
        assertTrue(LapAttemptRules.validStart(true, 100, 0, true));
        assertTrue(LapAttemptRules.validStart(true, false));
        assertFalse(LapAttemptRules.validStart(false, 100, 0, true));
        assertEquals(60008, snapshot.lapMillis());
        var records = new OWRLapRecords();
        var lap = records.recordLap(DRIVER, "Driver", snapshot.lapMillis(), 1300, "test", 0, 0,
            new OWRLapRecords.CarSnapshot(1, 2, 3, 4, 0, 0, true));
        assertFalse(lap.invalidated());
        assertEquals(60008, records.getBestLap(DRIVER));
    }

    @Test void partialAttemptIsVisibleInDirectorButExcludedFromValidLapsAndBestTimes() {
        var records = new OWRLapRecords();
        int millis = LapTimingClock.elapsedMillis(100.0, 1000.14);
        assertFalse(LapAttemptRules.shouldDiscard(millis, 500));
        assertFalse(LapAttemptRules.validStart(100, 0, true));
        var partial = records.recordLap(DRIVER, "Driver", millis, 1001, "test", 0, 0,
            new OWRLapRecords.CarSnapshot(1, 2, 3, 4, 0, 0, true));
        assertTrue(records.invalidateLap(partial.id(), DRIVER, "did not start at start line"));
        var rows = records.getVisibleLaps(false, 0, 12).stream().map(RaceDirectorLapRow::fromRecord).toList();
        assertEquals(1, rows.size());
        assertTrue(rows.getFirst().invalidated());
        assertEquals("did not start at start line", rows.getFirst().invalidationReason());
        assertEquals(millis, rows.getFirst().lapMillis());
        assertEquals(0, records.getBestLap(DRIVER));
        assertTrue(records.getValidSessionLaps(records.getActiveSessionId()).isEmpty());

        // A subsequent complete start-line-to-start-line lap is independently valid.
        var complete = records.recordLap(DRIVER, "Driver", 60_008, 2201, "test", 0, 0, partial.car());
        assertFalse(complete.invalidated());
        assertEquals(60_008, records.getBestLap(DRIVER));
        assertEquals(1, records.getValidSessionLaps(records.getActiveSessionId()).size());
    }

    @Test void completeForwardLapIsImmediatelyValidAndRankedAtInterpolatedMillisecondTime() {
        var left = new TrackDefinition.Point3(0, 64, -10);
        var right = new TrackDefinition.Point3(0, 64, 10);
        double startFraction = TrackGeometry.crossing(new Vec3(-1, 64, 0), new Vec3(3, 64, 0), left, right).orElseThrow().movementT();
        double finishFraction = TrackGeometry.crossing(new Vec3(-2, 64, 0), new Vec3(3, 64, 0), left, right).orElseThrow().movementT();
        double start = LapTimingClock.crossingTime(100, startFraction);
        double finish = LapTimingClock.crossingTime(1300, finishFraction);
        int lapMillis = LapTimingClock.elapsedMillis(start, finish);
        assertEquals(60_008, lapMillis);
        assertFalse(LapAttemptRules.shouldDiscard(lapMillis, 1500));

        var records = new OWRLapRecords();
        var row = records.recordLap(DRIVER, "Driver", lapMillis, 1300, 1_000_000_000_000L,
            "minecraft:overworld", 0, 3, new OWRLapRecords.CarSnapshot(1, 2, 3, 4, 0, 0, true));
        // No server tick, telemetry rotation, or I/O completion occurs before these assertions.
        assertEquals(row, records.getLap(row.id()).orElseThrow());
        assertFalse(row.invalidated());
        assertEquals(60_008, row.lapMillis());
        var telemetry = new LapTelemetryRecorder();
        telemetry.startUntracked(DRIVER, "Driver", "test", 100);
        telemetry.sampleUntracked(100, 0, 64, 0, 100);
        telemetry.sampleUntracked(105, 5, 64, 0, 100);
        assertEquals(60_008, telemetry.finish(row.id(), lapMillis).orElseThrow().lapMillis());
        assertEquals(60_008, records.getBestLap(DRIVER));
        assertEquals(1, records.getValidSessionLaps(records.getActiveSessionId()).size());
        var json = OWRLapRecords.LapRecord.CODEC.encodeStart(JsonOps.INSTANCE, row).getOrThrow();
        assertEquals(row, OWRLapRecords.LapRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test void consecutiveCrossingsPreserveOneMillisecondDifferencesAtLargeWorldTimes() {
        long tick = 2_800_000;
        double start = LapTimingClock.crossingTime(tick, 0.25);
        assertEquals(60_001, LapTimingClock.elapsedMillis(start, LapTimingClock.crossingTime(tick + 1200, 0.27)));
        assertEquals(60_002, LapTimingClock.elapsedMillis(start, LapTimingClock.crossingTime(tick + 1200, 0.29)));
        assertEquals(60_049, LapTimingClock.elapsedMillis(start, LapTimingClock.crossingTime(tick + 1201, 0.23)));
    }

    @Test void completedLapDoesNotWaitForIndependentDrivingCaptureToFlush() {
        var driving = new DrivingTelemetryRecorder();
        var savedSegments = new ArrayList<DrivingTelemetryRecorder.Segment>();
        driving.tick(DRIVER, "Driver", "test", 100, 5000, 0, 64, 0, 100, savedSegments::add);
        driving.tick(DRIVER, "Driver", "test", 105, 5250, 5, 64, 0, 100, savedSegments::add);
        var records = new OWRLapRecords();
        var row = records.recordLap(DRIVER, "Driver", 60_007, 105, "test", 0, 0,
            new OWRLapRecords.CarSnapshot(1, 2, 3, 4, 0, 0, true));
        assertTrue(savedSegments.isEmpty());
        assertFalse(records.getLap(row.id()).orElseThrow().invalidated());
        assertEquals(60_007, records.getBestLap(DRIVER));
        driving.flush(106, savedSegments::add);
        assertEquals(1, savedSegments.size());
        assertEquals(60_007, records.getLap(row.id()).orElseThrow().lapMillis());
    }
}
