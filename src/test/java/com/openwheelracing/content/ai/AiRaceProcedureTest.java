package com.openwheelracing.content.ai;

import com.openwheelracing.content.race.RaceFlagMode;
import com.openwheelracing.content.race.session.RaceSessionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiRaceProcedureTest {
    private static AiRaceProcedure.Input input(RaceSessionState phase, int lights, boolean ready) {
        return new AiRaceProcedure.Input(1, phase, true, lights, RaceFlagMode.GREEN, BasicAiTrafficMode.RACE, true, true, ready);
    }

    @Test
    void formationGreenEnablesHeatingButYellowStillRestrictsPace() {
        var green = new AiRaceProcedure().advance(input(RaceSessionState.FORMATION_LAP, 7, false));
        assertEquals(BasicAiTrafficMode.FORMATION_HEATING, green.mode());
        var yellow = new AiRaceProcedure().advance(new AiRaceProcedure.Input(1, RaceSessionState.FORMATION_LAP, true, 7,
            RaceFlagMode.YELLOW, BasicAiTrafficMode.RACE, true, true, false));
        assertEquals(BasicAiTrafficMode.FORMATION, yellow.mode());
    }

    @Test
    void gridSearchCanProceedOffSurveyWithoutGrantingRacingPermission() {
        var result = new AiRaceProcedure().advance(new AiRaceProcedure.Input(1, RaceSessionState.AWAITING_START, true, 0,
            RaceFlagMode.GREEN, BasicAiTrafficMode.RACE, false, true, false));
        assertEquals(AiRaceProcedure.State.RETURNING_GRID, result.state());
        assertFalse(result.holds());
        assertFalse(result.trains());
    }

    @Test
    void standaloneArmWaitsForFreshReleaseRatherThanGreenFlagOrStaleLightsOut() {
        AiRaceProcedure procedure = new AiRaceProcedure();
        assertTrue(procedure.advance(input(null, 0, true)).holds());
        assertTrue(procedure.advance(input(null, 6, true)).holds());
        assertTrue(procedure.advance(input(null, 6, true)).holds());
        assertEquals(AiRaceProcedure.State.FORMATION_COUNTDOWN, procedure.advance(input(null, 2, true)).state());
        assertEquals(AiRaceProcedure.State.RACING, procedure.advance(input(null, 6, true)).state());
        assertEquals(AiRaceProcedure.State.RACING, procedure.advance(input(null, 6, false)).state());
    }

    @Test
    void formationReturnsToGridAndRequiresAnotherRedSequenceBeforeRacing() {
        AiRaceProcedure procedure = new AiRaceProcedure();
        assertEquals(AiRaceProcedure.State.FORMATION, procedure.advance(input(null, 7, true)).state());
        assertEquals(AiRaceProcedure.State.RETURNING_GRID, procedure.advance(input(null, 0, false)).state());
        assertEquals(AiRaceProcedure.State.RETURNING_GRID, procedure.advance(input(null, 6, false)).state());
        assertEquals(AiRaceProcedure.State.GRID_READY, procedure.advance(input(null, 0, true)).state());
        assertEquals(AiRaceProcedure.State.RACE_COUNTDOWN, procedure.advance(input(null, 1, true)).state());
        assertEquals(AiRaceProcedure.State.RACING, procedure.advance(input(null, 6, true)).state());
    }

    @Test
    void gpLifecycleRequiresFormationGreenAndAuthoritativeRaceRelease() {
        AiRaceProcedure procedure = new AiRaceProcedure();
        assertTrue(procedure.advance(input(RaceSessionState.OPEN, 6, true)).holds());
        assertTrue(procedure.advance(input(RaceSessionState.FORMATION_COUNTDOWN, 7, true)).holds());
        assertTrue(procedure.advance(input(RaceSessionState.FORMATION_LAP, 6, false)).holds());
        assertEquals(AiRaceProcedure.State.FORMATION, procedure.advance(input(RaceSessionState.FORMATION_LAP, 7, false)).state());
        assertEquals(AiRaceProcedure.State.RETURNING_GRID, procedure.advance(input(RaceSessionState.AWAITING_START, 0, false)).state());
        assertEquals(AiRaceProcedure.State.GRID_READY, procedure.advance(input(RaceSessionState.AWAITING_START, 0, true)).state());
        assertTrue(procedure.advance(input(RaceSessionState.COUNTDOWN, 6, true)).holds());
        assertTrue(procedure.advance(input(RaceSessionState.RUNNING, 5, true)).holds());
        assertEquals(AiRaceProcedure.State.RACING, procedure.advance(input(RaceSessionState.RUNNING, 6, true)).state());
        assertTrue(procedure.advance(input(RaceSessionState.FINISHING, 6, false)).holds());
    }

    @Test
    void redFlagCannotBeBypassedInAnyPhaseOrPaceMode() {
        for (RaceSessionState phase : RaceSessionState.values()) {
            for (BasicAiTrafficMode mode : BasicAiTrafficMode.values()) {
                for (int lights = 0; lights <= 7; lights++) {
                    var decision = new AiRaceProcedure().advance(new AiRaceProcedure.Input(1, phase, true, lights,
                        RaceFlagMode.RED, mode, true, true, false));
                    assertTrue(decision.holds(), phase + " " + mode + " " + lights);
                    assertFalse(decision.trains());
                    assertFalse(decision.mayRecover());
                }
            }
        }
    }

    @Test
    void onlyRunningGpCanGrantRacingRegardlessOfLightsOrOverride() {
        for (RaceSessionState phase : RaceSessionState.values()) {
            for (int lights = 0; lights <= 7; lights++) {
                var decision = new AiRaceProcedure().advance(input(phase, lights, false));
                if (phase != RaceSessionState.RUNNING || lights != 6) assertNotEquals(AiRaceProcedure.State.RACING, decision.state());
            }
        }
    }

    @Test
    void missingLocalizationEligibilityAndSuspensionAllStopWithoutRecoveryOrTraining() {
        for (var in : new AiRaceProcedure.Input[] {
            new AiRaceProcedure.Input(1, RaceSessionState.RUNNING, false, 6, RaceFlagMode.GREEN, BasicAiTrafficMode.RACE, true, true, false),
            new AiRaceProcedure.Input(1, RaceSessionState.RUNNING, true, 6, RaceFlagMode.GREEN, BasicAiTrafficMode.RACE, false, true, false),
            input(RaceSessionState.SUSPENDED, 6, false)
        }) {
            var decision = new AiRaceProcedure().advance(in);
            assertTrue(decision.holds());
            assertFalse(decision.trains());
            assertFalse(decision.mayRecover());
        }
    }

    @Test
    void cautionBoundsPaceAndDisablesTrainingEvenWithRaceOverride() {
        var decision = new AiRaceProcedure().advance(new AiRaceProcedure.Input(1, RaceSessionState.RUNNING, true, 6,
            RaceFlagMode.VIRTUAL_SAFETY_CAR, BasicAiTrafficMode.RACE, true, true, false));
        assertEquals(AiRaceProcedure.State.CAUTION, decision.state());
        assertEquals(BasicAiTrafficMode.VSC, decision.mode());
        assertFalse(decision.trains());
    }

    @Test
    void newSessionInvalidatesStandaloneReleasePermission() {
        AiRaceProcedure procedure = new AiRaceProcedure();
        procedure.advance(input(null, 1, true));
        assertFalse(procedure.advance(input(null, 6, true)).holds());
        assertTrue(procedure.advance(new AiRaceProcedure.Input(2, null, true, 6, RaceFlagMode.GREEN,
            BasicAiTrafficMode.RACE, true, true, true)).holds());
    }
}
