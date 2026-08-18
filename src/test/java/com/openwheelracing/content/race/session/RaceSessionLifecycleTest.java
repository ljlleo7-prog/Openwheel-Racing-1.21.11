package com.openwheelracing.content.race.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceSessionLifecycleTest {
    @Test
    void runningClockUsesOnlyServerTicks() {
        RaceSessionLifecycle session = runningSession(1_200L, 100L);

        assertEquals(50L, session.elapsedTicks(150L));
        assertEquals(1_150L, session.remainingTicks(150L));
        assertFalse(session.hasExpired(1_299L));
        assertTrue(session.hasExpired(1_300L));
    }

    @Test
    void emptyServerSuspendsOnceAndReturningPlayerDoesNotResume() {
        RaceSessionLifecycle session = runningSession(1_200L, 100L);

        assertTrue(session.onConnectedPlayerCountChanged(0, 160L));
        assertFalse(session.onConnectedPlayerCountChanged(0, 300L));
        assertFalse(session.onConnectedPlayerCountChanged(1, 500L));
        assertEquals(RaceSessionState.SUSPENDED, session.state());
        assertEquals(RaceSessionSuspensionReason.EMPTY_SERVER, session.suspensionReason());
        assertEquals(60L, session.elapsedTicks(10_000L));
    }

    @Test
    void explicitResumeContinuesFromFrozenClock() {
        RaceSessionLifecycle session = runningSession(1_200L, 100L);
        session.suspend(RaceSessionSuspensionReason.DIRECTOR, 160L);

        session.resume(1_000L);

        assertEquals(RaceSessionState.RUNNING, session.state());
        assertEquals(85L, session.elapsedTicks(1_025L));
    }

    @Test
    void recoverySuspendsRunningSessionWithoutOfflineElapsedTime() {
        RaceSessionLifecycle original = runningSession(1_200L, 100L);
        RaceSessionLifecycle.Checkpoint saved = original.checkpoint(180L);

        RaceSessionLifecycle recovered = RaceSessionLifecycle.recover(saved);

        assertEquals(RaceSessionState.SUSPENDED, recovered.state());
        assertEquals(RaceSessionState.RUNNING, recovered.suspendedFrom());
        assertEquals(RaceSessionSuspensionReason.SERVER_RECOVERY, recovered.suspensionReason());
        assertEquals(80L, recovered.elapsedTicks(50_000L));
        recovered.resume(50_000L);
        assertEquals(100L, recovered.elapsedTicks(50_020L));
    }

    @Test
    void recoveryAlsoMakesCountdownSafe() {
        RaceSessionLifecycle original = new RaceSessionLifecycle(1_200L);
        original.transitionTo(RaceSessionState.OPEN, 10L);
        original.transitionTo(RaceSessionState.STAGING, 20L);
        original.transitionTo(RaceSessionState.COUNTDOWN, 30L);

        RaceSessionLifecycle recovered = RaceSessionLifecycle.recover(original.checkpoint(40L));

        assertEquals(RaceSessionState.SUSPENDED, recovered.state());
        assertEquals(RaceSessionState.COUNTDOWN, recovered.suspendedFrom());
        assertEquals(0L, recovered.elapsedTicks(1_000L));
    }

    @Test
    void officialResultIsTerminal() {
        RaceSessionLifecycle session = runningSession(0L, 100L);
        session.transitionTo(RaceSessionState.FINISHING, 200L);
        session.transitionTo(RaceSessionState.PROVISIONAL, 200L);
        session.transitionTo(RaceSessionState.OFFICIAL, 200L);

        assertTrue(session.state().isTerminal());
        assertThrows(RaceSessionTransitionException.class,
            () -> session.transitionTo(RaceSessionState.FINISHING, 201L));
    }

    @Test
    void invalidStateJumpIsRejected() {
        RaceSessionLifecycle session = new RaceSessionLifecycle(1_200L);

        assertThrows(RaceSessionTransitionException.class,
            () -> session.transitionTo(RaceSessionState.RUNNING, 10L));
    }

    private static RaceSessionLifecycle runningSession(long durationTicks, long startTick) {
        RaceSessionLifecycle session = new RaceSessionLifecycle(durationTicks);
        session.transitionTo(RaceSessionState.OPEN, startTick - 1L);
        session.transitionTo(RaceSessionState.RUNNING, startTick);
        return session;
    }
}
