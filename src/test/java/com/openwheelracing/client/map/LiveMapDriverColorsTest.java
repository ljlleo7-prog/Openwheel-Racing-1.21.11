package com.openwheelracing.client.map;

import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveMapDriverColorsTest {
    @Test void driversKeepDistinctColorsAndNumbersAsTheRosterChanges() {
        var colors = new LiveMapDriverColors();
        UUID first = new UUID(0, 1);
        var original = colors.identity(first);
        var usedColors = new HashSet<Integer>();
        var usedNumbers = new HashSet<Integer>();
        for (int i = 1; i <= 1000; i++) {
            var identity = colors.identity(new UUID(0, i));
            assertTrue(usedColors.add(identity.color()));
            assertTrue(usedNumbers.add(identity.number()));
            assertEquals(0xFF, identity.color() >>> 24);
        }
        assertEquals(original, colors.identity(first));
    }
}
