package com.openwheelracing.content.ai;

import com.openwheelracing.content.race.RaceFlagMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAiTrafficModeTest {
    @Test
    void autoMapsRaceFlags() {
        assertEquals(BasicAiTrafficMode.RACE, BasicAiTrafficMode.resolve(BasicAiTrafficMode.AUTO, RaceFlagMode.GREEN));
        assertEquals(BasicAiTrafficMode.FORMATION, BasicAiTrafficMode.resolve(BasicAiTrafficMode.AUTO, RaceFlagMode.YELLOW));
        assertEquals(BasicAiTrafficMode.VSC, BasicAiTrafficMode.resolve(BasicAiTrafficMode.AUTO, RaceFlagMode.VIRTUAL_SAFETY_CAR));
        assertEquals(BasicAiTrafficMode.SAFETY_CAR, BasicAiTrafficMode.resolve(BasicAiTrafficMode.AUTO, RaceFlagMode.SAFETY_CAR));
        assertEquals(BasicAiTrafficMode.HOLD, BasicAiTrafficMode.resolve(BasicAiTrafficMode.AUTO, RaceFlagMode.RED));
    }

    @Test
    void explicitModeOverridesFlag() {
        assertEquals(BasicAiTrafficMode.RACE, BasicAiTrafficMode.resolve(BasicAiTrafficMode.RACE, RaceFlagMode.RED));
        assertFalse(BasicAiTrafficMode.RACE.queueing());
        assertTrue(BasicAiTrafficMode.FORMATION.queueing());
    }
}
