package com.openwheelracing.content.race.weekend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormationLapProcedureTest {
    @Test
    void arbitraryCountdownRoundsUpToTenSecondLightIntervals() {
        assertEquals(0, FormationLapProcedure.redLightCount(0));
        assertEquals(1, FormationLapProcedure.redLightCount(1));
        assertEquals(1, FormationLapProcedure.redLightCount(10 * 20));
        assertEquals(2, FormationLapProcedure.redLightCount(10 * 20 + 1));
        assertEquals(2, FormationLapProcedure.redLightCount(11 * 20));
        assertEquals(2, FormationLapProcedure.redLightCount(20 * 20));
        assertEquals(3, FormationLapProcedure.redLightCount(20 * 20 + 1));
        assertEquals(5, FormationLapProcedure.redLightCount(50 * 20));
        assertEquals(5, FormationLapProcedure.redLightCount(90 * 20));
        assertEquals(5, FormationLapProcedure.redLightCount(Long.MAX_VALUE));
    }

    @Test
    void handoverWaitsForLeaderToReachFinalApproachAfterDeparture() {
        assertFalse(FormationLapProcedure.leaderReturning(-5, 1_000));
        assertFalse(FormationLapProcedure.leaderReturning(5, 1_000));
        assertFalse(FormationLapProcedure.leaderReturning(899, 1_000));
        assertTrue(FormationLapProcedure.leaderReturning(900, 1_000));
        assertTrue(FormationLapProcedure.leaderReturning(1_005, 1_000));
        assertFalse(FormationLapProcedure.leaderReturning(44, 50));
        assertTrue(FormationLapProcedure.leaderReturning(45, 50));
    }
}
