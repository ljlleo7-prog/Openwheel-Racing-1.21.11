package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PitTyreServiceTimingTest {
    @Test
    void operationDurationRangesFromSixToTenTicks() {
        assertEquals(6, PitTyreServiceTiming.durationFromRoll(0));
        assertEquals(8, PitTyreServiceTiming.durationFromRoll(2));
        assertEquals(10, PitTyreServiceTiming.durationFromRoll(4));
    }

    @Test
    void earlyInputLosesWorkWithoutExceedingOriginalDuration() {
        assertEquals(7, PitTyreServiceTiming.applyEarlySetback(5, 10));
        assertEquals(10, PitTyreServiceTiming.applyEarlySetback(9, 10));
    }

    @Test
    void completeServiceSpansTwentyFourToFortyTicks() {
        assertEquals(24, PitTyreServiceTiming.MIN_DURATION_TICKS * 4);
        assertEquals(40, PitTyreServiceTiming.MAX_DURATION_TICKS * 4);
    }

    @Test
    void cancellationReturnsExactlyTheTyreSetNotFittedToTheCar() {
        assertEquals(PitTyreServiceTiming.RETURN_NONE,
            PitTyreServiceTiming.cancellationReturn(false, false));
        assertEquals(PitTyreServiceTiming.RETURN_RESERVED_NEW,
            PitTyreServiceTiming.cancellationReturn(false, true));
        assertEquals(PitTyreServiceTiming.RETURN_REMOVED_OLD,
            PitTyreServiceTiming.cancellationReturn(true, true));
    }
}
