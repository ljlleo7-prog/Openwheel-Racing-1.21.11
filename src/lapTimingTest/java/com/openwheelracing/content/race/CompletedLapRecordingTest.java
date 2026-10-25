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
