package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhysicsTelemetryLoggerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void flushExportsBufferedCsvWithRawAndAssistedRequests() throws Exception {
        Path output = temporaryDirectory.resolve("physics.csv");
        PhysicsTelemetryLogger.WheelSample wheel = new PhysicsTelemetryLogger.WheelSample(
            2_000.0, 2.1, 2.0, 4_200.0, 4_000.0, -5_000.0, -3_500.0,
            2_200.0, -3_200.0, 1_800.0, 0.91, 4.2, 120.0, 91.0, 88.0);
        try (PhysicsTelemetryLogger logger = PhysicsTelemetryLogger.create(output)) {
            logger.append(new PhysicsTelemetryLogger.CarSample(
                42L, 166.0, 46.0, 1.0, 0.5, 1.0, 2.8, 0.0, 1.0,
                1.0, -2_500.0, 12_000.0, -8_000.0, 9_100.0,
                true, false, 0.98, 0.98), wheel, wheel, wheel, wheel);
            logger.flush();
        }

        List<String> lines = Files.readAllLines(output);
        assertEquals(2, lines.size());
        assertTrue(lines.getFirst().contains("fl_raw_long_request_n,fl_assisted_long_request_n"));
        assertEquals(lines.getFirst().split(",").length, lines.get(1).split(",").length);
    }
}
