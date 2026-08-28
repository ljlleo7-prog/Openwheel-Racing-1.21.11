package com.openwheelracing.content.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class RaceLightConfigurationTest {
    @Test void invalidPitModeOrdinalFailsSafeToEntry() {
        assertEquals(PitLightMode.ENTRY, PitLightMode.fromOrdinal(-1));
        assertEquals(PitLightMode.ENTRY, PitLightMode.fromOrdinal(999));
    }

    @Test void threePhysicalLightTypesRemainDistinct() {
        assertEquals(3, RaceLightType.values().length);
    }
}
