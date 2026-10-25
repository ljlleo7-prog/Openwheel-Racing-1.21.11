package com.openwheelracing.content.race.weekend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RaceStartProcedureTest {
    @Test
    void gpTimelineExactlyMatchesTraditionalPerLightStepsAndFinalLightsOutDelay() {
        for (int randomDelay : new int[] {0, 17, 40}) {
            long elapsed = 0L;
            assertEquals(1, RaceStartProcedure.redLightCount(elapsed));
            for (int phase = 1; phase < 5; phase++) {
                assertEquals(20L, RaceStartProcedure.nextPhaseDelayTicks(phase, randomDelay));
                elapsed += RaceStartProcedure.nextPhaseDelayTicks(phase, randomDelay);
                assertEquals(phase + 1, RaceStartProcedure.redLightCount(elapsed));
            }
            assertEquals(80L, elapsed); // Fifth red is on; only its lights-out delay remains.
            long lightsOutDelay = RaceStartProcedure.nextPhaseDelayTicks(5, randomDelay);
            assertEquals(20L + randomDelay, lightsOutDelay);
            assertEquals(5, RaceStartProcedure.redLightCount(elapsed + lightsOutDelay - 1));
            assertEquals(elapsed + lightsOutDelay, RaceStartProcedure.durationTicks(randomDelay));
        }
    }

    @Test
    void redLightsAdvanceOncePerSecondThenStayOnDuringRandomHold() {
        assertEquals(1, RaceStartProcedure.redLightCount(0));
        assertEquals(1, RaceStartProcedure.redLightCount(19));
        assertEquals(2, RaceStartProcedure.redLightCount(20));
        assertEquals(5, RaceStartProcedure.redLightCount(80));
        assertEquals(5, RaceStartProcedure.redLightCount(100));
        assertEquals(5, RaceStartProcedure.redLightCount(139));
    }

    @Test
    void totalIsFiveSecondsPlusZeroToTwoSeconds() {
        assertEquals(100, RaceStartProcedure.durationTicks(0));
        assertEquals(127, RaceStartProcedure.durationTicks(27));
        assertEquals(140, RaceStartProcedure.durationTicks(40));
        assertEquals(100, RaceStartProcedure.durationTicks(-10));
        assertEquals(140, RaceStartProcedure.durationTicks(100));
    }
}
