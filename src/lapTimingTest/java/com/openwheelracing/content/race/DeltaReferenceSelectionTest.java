package com.openwheelracing.content.race;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeltaReferenceSelectionTest {
    @Test void threeValidLapsChooseFastestTimingWithoutReplacingCleanAiLine() {
        UUID track = UUID.randomUUID(), route = UUID.randomUUID(), driver = UUID.randomUUID();
        var profiles = new OWRLapProfiles();
        // Slower spin lap happened not to hit a wall. Faster valid laps touched a wall.
        assertTrue(profiles.putIfFaster(profile(track, route, driver, 1, 6000, true)));
        assertTrue(profiles.putIfFaster(profile(track, route, driver, 2, 5000, false)));
        assertTrue(profiles.putIfFaster(profile(track, route, driver, 3, 4500, false)));
        assertEquals(4500, profiles.get(track, route, driver, LapTimingScope.SESSION, 1).orElseThrow().lapMillis());
        assertEquals(6000, profiles.recordedLine(track, route, driver).orElseThrow().lapMillis());
        assertEquals(6000, profiles.fastestValidPlayer(track, route, 16).orElseThrow().lapMillis());
        assertFalse(profiles.putIfFaster(profile(track, route, driver, 4, 6500, true)));
        assertEquals(4500, profiles.get(track, route, driver).orElseThrow().lapMillis());
    }

    private static OWRLapProfiles.BestLapProfile profile(UUID track, UUID route, UUID driver, long id, int time, boolean line) {
        return new OWRLapProfiles.BestLapProfile("minecraft:overworld", track, route, driver, "Driver", OWRLapProfiles.Origin.PLAYER,
            id, 1, time, 16, 4, new int[]{0, time / 4, time / 2, time * 3 / 4}, new int[]{1000, 1000, 1000, 1000},
            line ? new int[4] : new int[0], line ? new int[4] : new int[0], id);
    }
}
