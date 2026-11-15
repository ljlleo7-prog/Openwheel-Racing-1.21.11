package com.openwheelracing.client.debug;

import java.util.HashSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntermittentVehicleLagTest {
    @Test
    void mostTrafficIsImmediateWithIrregularBoundedBursts() {
        var lag = new IntermittentVehicleLag(new Random(42));
        lag.configure(6, 0);
        int immediate = 0, stalls = 0;
        long lastRelease = -1, previousStart = -1;
        var intervals = new HashSet<Long>();
        for (long tick = 0; tick < 20000; tick++) {
            long release = lag.releaseAt(tick);
            assertTrue(release >= tick && release - tick <= 6);
            assertEquals(release, lag.releaseAt(tick), "All packets in one tick share the same stall");
            if (release == tick) immediate++;
            else if (release != lastRelease) {
                stalls++;
                if (previousStart >= 0) {
                    assertTrue(tick - lastRelease >= 60 && tick - lastRelease <= 140);
                    intervals.add(tick - previousStart);
                }
                previousStart = tick;
                lastRelease = release;
            }
        }
        assertTrue(immediate > 18000, "At least 90% of ticks should deliver normally");
        assertTrue(stalls > 100);
        assertTrue(intervals.size() > 10, "Stalls must not recur on a fixed cadence");
    }

    @Test
    void changingSettingsClearsStallAndStartsWithQuietDriving() {
        var lag = new IntermittentVehicleLag(new Random(1));
        lag.configure(6, 0);
        for (long tick = 0; tick < 60; tick++) assertEquals(tick, lag.releaseAt(tick));
        long active = 60;
        while (lag.releaseAt(active) == active) active++;
        lag.configure(0, active);
        assertEquals(active, lag.releaseAt(active));
        lag.configure(6, active);
        assertEquals(active + 59, lag.releaseAt(active + 59));
    }
}
