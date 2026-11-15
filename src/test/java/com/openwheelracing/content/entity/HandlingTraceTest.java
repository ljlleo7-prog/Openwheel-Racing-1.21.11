package com.openwheelracing.content.entity;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class HandlingTraceTest {
    @TempDir Path directory;

    @Test
    void retainsRecentIncidentEvidenceWithoutGrowingIndefinitely() throws Exception {
        var trace = new HandlingTrace();
        for (int i = 0; i < HandlingTrace.CAPACITY + 5; i++) trace.add("event-" + i);
        assertEquals(HandlingTrace.CAPACITY, trace.size());
        assertEquals(5, trace.dropped());
        Path output = directory.resolve("capture.csv");
        trace.save(output);
        var rows = Files.readAllLines(output);
        assertEquals("event-5", rows.get(2));
        assertEquals("event-20004", rows.getLast());
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> trace.save(output));
    }
}
