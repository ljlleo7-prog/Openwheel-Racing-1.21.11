package com.openwheelracing.content.race;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OWRLapProfilesTest {
    @Test
    void interpolatesReferenceMillisBetweenSamples() {
        OWRLapProfiles.BestLapProfile profile = profile(UUID.randomUUID(), UUID.randomUUID(), new int[]{0, 1000, 2000, 3000}, new int[]{1000, 2000, 3000, 4000});
        assertEquals(1500, profile.referenceMillis(6.0));
        assertEquals(3500, profile.referenceMillis(14.0));
    }

    @Test
    void interpolatesSpeedInKmh() {
        OWRLapProfiles.BestLapProfile profile = profile(UUID.randomUUID(), UUID.randomUUID(), new int[]{0, 1000, 2000, 3000}, new int[]{1000, 2000, 3000, 4000});
        assertEquals(90.0, profile.speedKmh(6.0), 0.001);
    }

    @Test
    void routeIdentityRemainsDistinct() {
        UUID trackId = UUID.randomUUID();
        UUID firstRoute = UUID.randomUUID();
        UUID secondRoute = UUID.randomUUID();
        UUID driver = UUID.randomUUID();
        OWRLapProfiles.BestLapProfile first = profile(trackId, firstRoute, driver, 4000);
        OWRLapProfiles.BestLapProfile second = profile(trackId, secondRoute, driver, 3900);
        assertEquals(firstRoute, first.routeId());
        assertEquals(secondRoute, second.routeId());
    }

    @Test
    void rejectsOversizedProfile() {
        int[] values = new int[OWRLapProfiles.MAX_PROFILE_SAMPLES + 1];
        assertThrows(IllegalArgumentException.class, () -> profile(UUID.randomUUID(), UUID.randomUUID(), values, values));
    }

    private static OWRLapProfiles.BestLapProfile profile(UUID trackId, UUID routeId, int[] times, int[] speeds) {
        return new OWRLapProfiles.BestLapProfile("minecraft:overworld", trackId, routeId, UUID.randomUUID(), "Driver", 1L, 4000, 16.0, 4.0, times, speeds, 1L);
    }

    private static OWRLapProfiles.BestLapProfile profile(UUID trackId, UUID routeId, UUID driverId, int lapMillis) {
        return new OWRLapProfiles.BestLapProfile("minecraft:overworld", trackId, routeId, driverId, "Driver", 1L, lapMillis, 16.0, 4.0,
            new int[]{0, 1000, 2000, 3000}, new int[]{1000, 2000, 3000, 4000}, 1L);
    }
}
