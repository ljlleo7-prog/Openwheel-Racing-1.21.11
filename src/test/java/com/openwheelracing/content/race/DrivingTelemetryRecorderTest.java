package com.openwheelracing.content.race;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DrivingTelemetryRecorderTest {
    @Test void capturesDrivingWithoutRouteOrLapAndSavesOnDismount() {
        var recorder = new DrivingTelemetryRecorder();
        var saved = new ArrayList<DrivingTelemetryRecorder.Segment>();
        UUID driver = UUID.randomUUID();
        recorder.tick(driver, "Driver", "test", 100, 1000, 10, 64, 20, 72, saved::add);
        recorder.tick(driver, "Driver", "test", 105, 1250, 15, 64, 20, 80, saved::add);
        recorder.tick(null, "", "test", 106, 1300, 15, 64, 20, 0, saved::add);
        assertEquals(1, saved.size());
        var data = saved.getFirst().snapshot();
        assertEquals(driver, data.driverId());
        assertArrayEquals(new int[]{0, 250}, data.elapsedMillis());
        assertArrayEquals(new int[]{1000, 1500}, data.worldXCm());
        assertArrayEquals(new int[]{2000, 2222}, data.speedCmps());
        assertArrayEquals(new int[]{0, 500}, data.coveredDistanceCm());
        assertEquals(0, data.routeLength());
        assertArrayEquals(new byte[]{3, 3}, data.trackingStatus());
    }

    @Test void rotatesBoundedSegmentsAndNeverSharesDataBetweenDrivers() {
        var recorder = new DrivingTelemetryRecorder();
        var saved = new ArrayList<DrivingTelemetryRecorder.Segment>();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        for (long tick = 0; tick <= 205; tick += 5) recorder.tick(a, "A", "test", tick, tick * 50, tick, 64, 0, 100, saved::add);
        assertEquals(1, saved.size());
        recorder.tick(b, "B", "test", 210, 10500, 0, 64, 0, 0, saved::add);
        recorder.tick(b, "B", "test", 215, 10750, 0, 64, 0, 0, saved::add);
        recorder.flush(216, saved::add);
        assertEquals(3, saved.size());
        assertEquals(a, saved.get(0).snapshot().driverId());
        assertEquals(a, saved.get(1).snapshot().driverId());
        assertEquals(b, saved.get(2).snapshot().driverId());
        assertNotEquals(saved.get(0).captureId(), saved.get(1).captureId());
    }
}
