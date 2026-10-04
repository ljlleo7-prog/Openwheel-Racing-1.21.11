package com.openwheelracing.content.race;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LapTelemetryLapSplitterTest {
    @Test
    void splitsTwoSurveyLapsAtTheNearestStartFinishSampleWithConservativeTime() {
        int count = 41;
        int[] elapsed = new int[count];
        int[] route = new int[count];
        int[] covered = new int[count];
        int[] speed = new int[count];
        int[] zeros = new int[count];
        byte[] status = new byte[count];
        for (int index = 0; index < count; index++) {
            elapsed[index] = index * 500;
            route[index] = (index * 5_000) % 100_000;
            covered[index] = index * 5_000;
            speed[index] = 10_000;
        }
        status[5] = 3; // one brief localizer dropout; route progress resumes within the step bound
        LapTelemetryRecorder.Snapshot snapshot = new LapTelemetryRecorder.Snapshot(0L, UUID.randomUUID(), "driver", "test",
            UUID.randomUUID(), UUID.randomUUID(), 20_000, 1_000.0, elapsed, route, covered, speed,
            zeros, zeros, zeros, status);

        var splits = LapTelemetryLapSplitter.split(snapshot, 0.0, 1_000);

        assertEquals(2, splits.size());
        assertEquals(20, splits.get(0).endSample());
        assertEquals(10_250, splits.get(0).lapMillis());
        assertEquals(10_250, splits.get(1).lapMillis());
    }

    @Test
    void refusesToSplitWhenDistanceIsNotAnIntegerNumberOfSurveyLaps() {
        int count = 20;
        int[] elapsed = new int[count];
        int[] route = new int[count];
        int[] covered = new int[count];
        int[] zeros = new int[count];
        byte[] status = new byte[count];
        for (int index = 0; index < count; index++) {
            elapsed[index] = index * 1_000;
            route[index] = (index * 10_000) % 100_000;
            covered[index] = index * 10_000;
        }
        LapTelemetryRecorder.Snapshot snapshot = new LapTelemetryRecorder.Snapshot(0L, UUID.randomUUID(), "driver", "test",
            UUID.randomUUID(), UUID.randomUUID(), 19_000, 1_000.0, elapsed, route, covered, zeros,
            zeros, zeros, zeros, status);

        assertTrue(LapTelemetryLapSplitter.split(snapshot, 0.0, 1_000).isEmpty());
    }
}
