package com.openwheelracing.content.race;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StoredLapReplayTest {
    @TempDir Path world;

    @Test
    void completeCsvIsPublishedThenReadWithoutAMinecraftServer() throws Exception {
        UUID driver = new UUID(0, 1);
        var snapshot = new LapTelemetryRecorder.Snapshot(7, driver, "Driver", "race:a", new UUID(0, 2), new UUID(0, 3),
            1000, 100, new int[] {0, 1000}, new int[] {0, 1000}, new int[] {0, 1000}, new int[] {1000, 2000},
            new int[] {0, 1000}, new int[] {6400, 6400}, new int[] {0, 0}, new byte[] {1, 1});
        Path directory = world.resolve("openwheelracing/lap-telemetry");
        var write = LapTelemetryStore.class.getDeclaredMethod("write", Path.class, LapTelemetryRecorder.Snapshot.class);
        write.setAccessible(true);
        write.invoke(null, directory, snapshot);
        var data = LapTelemetryStore.readStored(world, 7, driver, "race:a", "Driver", 1000).orElseThrow();
        assertArrayEquals(new int[] {0, 1000}, data.elapsedMillis());
        assertArrayEquals(new int[] {1000, 2000}, data.speedCmps());
        try (var files = Files.list(directory)) { assertTrue(files.noneMatch(file -> file.toString().endsWith(".tmp"))); }
        assertTrue(LapTelemetryStore.readStored(world, 7, driver, "race:b", "Driver", 1000).isEmpty(),
            "A missing lap in another dimension must not fall back to this dimension's same lap ID");
        assertTrue(LapTelemetryStore.readStored(world, 7, new UUID(0, 9), "race:a", "Other driver", 1000).isEmpty());
    }
}
