package com.openwheelracing.content.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcceptedMotionClockTest {
    @Test
    void batchedFramesRetainDistinctTimingInsteadOfArrivalTick() {
        var clock = new AcceptedMotionClock();
        assertEquals(100, clock.accept(1, 100, 0).end());
        clock.accept(2, 101, 50_000_000L);
        for (int sequence = 3; sequence <= 8; sequence++) {
            var interval = clock.accept(sequence, 108, 400_000_000L);
            assertEquals(98 + sequence, interval.start());
            assertEquals(99 + sequence, interval.end());
        }
    }

    @Test
    void frameBudgetPreventsUnlimitedClientTimeAdvancement() {
        var clock = new AcceptedMotionClock();
        for (int i = 1; i <= AcceptedMotionClock.BURST_FRAMES; i++) clock.accept(i, 0, 0);
        assertFalse(clock.ready(0));
        assertTrue(clock.ready(AcceptedMotionClock.STEP_NANOS));
        assertThrows(IllegalArgumentException.class, () -> clock.accept(99, 1, AcceptedMotionClock.STEP_NANOS));
    }

    @Test
    void duplicateFramesCannotAdvanceClock() {
        var clock = new AcceptedMotionClock();
        clock.accept(1, 100, 0);
        assertThrows(IllegalArgumentException.class, () -> clock.accept(1, 100, 0));
        assertEquals(100, clock.now(0, 0));
    }
    @Test
    void longClientFreezeCannotStopElapsedRaceTime() {
        var clock = new AcceptedMotionClock();
        clock.accept(1, 100, 0);
        var resumed = clock.accept(2, 300, 10_000_000_000L);
        assertEquals(288, resumed.end());
        assertEquals(289, clock.accept(3, 301, 10_050_000_000L).end());
        assertEquals(301, clock.now(10_050_000_000L, 0));
    }
}
