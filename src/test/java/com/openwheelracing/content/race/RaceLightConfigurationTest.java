package com.openwheelracing.content.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class RaceLightConfigurationTest {
    @Test void invalidPitModeOrdinalFailsSafeToEntry() {
        assertEquals(PitLightMode.ENTRY, PitLightMode.fromOrdinal(-1));
        assertEquals(PitLightMode.ENTRY, PitLightMode.fromOrdinal(999));
    }

    @Test void formationUsesOnlySecondAndFourthGreenLights() {
        for (int order = 1; order <= 5; order++) {
            assertEquals(order == 2 || order == 4 ? RaceStartLights.Lamp.GREEN : RaceStartLights.Lamp.OFF,
                RaceStartLights.signal(RaceStartLights.FORMATION_GREEN, order));
            assertEquals(RaceStartLights.Lamp.OFF, RaceStartLights.signal(0, order));
        }
    }

    @Test void remainingTimeDeterminesPhysicalRedLights() {
        int phase = com.openwheelracing.content.race.weekend.FormationLapProcedure.redLightCount(11L * 20L);
        assertEquals(RaceStartLights.Lamp.RED, RaceStartLights.signal(phase, 1));
        assertEquals(RaceStartLights.Lamp.RED, RaceStartLights.signal(phase, 2));
        assertEquals(RaceStartLights.Lamp.OFF, RaceStartLights.signal(phase, 3));
        phase = com.openwheelracing.content.race.weekend.FormationLapProcedure.redLightCount(10L * 20L);
        assertEquals(RaceStartLights.Lamp.RED, RaceStartLights.signal(phase, 1));
        assertEquals(RaceStartLights.Lamp.OFF, RaceStartLights.signal(phase, 2));
    }

    @Test void threePhysicalLightTypesRemainDistinct() {
        assertEquals(3, RaceLightType.values().length);
    }
}
