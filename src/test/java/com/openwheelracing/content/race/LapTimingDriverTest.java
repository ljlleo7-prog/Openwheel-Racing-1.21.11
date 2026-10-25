package com.openwheelracing.content.race;

import com.openwheelracing.content.ai.BasicAiDriverIdentity;
import com.openwheelracing.content.race.timing.RaceParticipantKind;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LapTimingDriverTest {
    private final BasicAiDriverIdentity ai = BasicAiDriverIdentity.create(UUID.randomUUID(), UUID.randomUUID(), 1, 7);

    @Test
    void emptyAutonomousCarRecordsUnderItsStableAiDriverIdentity() {
        var driver = LapTimingDriver.resolve(null, "", false, ai, true);
        assertEquals(ai.driverId(), driver.id());
        assertEquals("AI-07", driver.name());
        assertEquals(RaceParticipantKind.AI, driver.kind());
    }

    @Test
    void playerTakingOverAiCarOwnsLapInsteadOfAi() {
        UUID player = UUID.randomUUID();
        var driver = LapTimingDriver.resolve(player, "Driver", true, ai, true);
        assertEquals(player, driver.id());
        assertEquals("Driver", driver.name());
        assertEquals(RaceParticipantKind.PLAYER, driver.kind());
    }

    @Test
    void deadPlayerCannotFallBackToAiIdentity() {
        assertNull(LapTimingDriver.resolve(UUID.randomUUID(), "Driver", false, ai, true));
    }

    @Test
    void parkedAiAndUnoccupiedPlayerCarsDoNotOwnTimedLaps() {
        assertNull(LapTimingDriver.resolve(null, "", false, ai, false));
        assertNull(LapTimingDriver.resolve(null, "", false, null, true));
    }
}
