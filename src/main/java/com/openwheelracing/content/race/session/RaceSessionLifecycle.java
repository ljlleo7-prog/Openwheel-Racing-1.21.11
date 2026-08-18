package com.openwheelracing.content.race.session;

import java.util.Objects;

/**
 * Pure server-side lifecycle and sporting clock for one race session.
 * Game ticks are supplied by the caller so no wall-clock time can leak into
 * session timing or restart recovery.
 */
public final class RaceSessionLifecycle {
    private final long durationTicks;
    private RaceSessionState state;
    private RaceSessionState suspendedFrom;
    private RaceSessionSuspensionReason suspensionReason;
    private long elapsedTicks;
    private long runningSinceTick;

    public RaceSessionLifecycle(long durationTicks) {
        if (durationTicks < 0L) {
            throw new IllegalArgumentException("durationTicks must not be negative");
        }
        this.durationTicks = durationTicks;
        state = RaceSessionState.CONFIGURED;
        suspendedFrom = RaceSessionState.CONFIGURED;
        suspensionReason = RaceSessionSuspensionReason.NONE;
        runningSinceTick = -1L;
    }

    private RaceSessionLifecycle(Checkpoint checkpoint) {
        durationTicks = checkpoint.durationTicks();
        elapsedTicks = checkpoint.elapsedTicks();
        state = checkpoint.state();
        suspendedFrom = checkpoint.suspendedFrom();
        suspensionReason = checkpoint.suspensionReason();
        runningSinceTick = -1L;

        if (state.isActive()) {
            suspendedFrom = state;
            state = RaceSessionState.SUSPENDED;
            suspensionReason = RaceSessionSuspensionReason.SERVER_RECOVERY;
        }
    }

    public static RaceSessionLifecycle recover(Checkpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        return new RaceSessionLifecycle(checkpoint);
    }

    public RaceSessionState state() {
        return state;
    }

    public RaceSessionState suspendedFrom() {
        return suspendedFrom;
    }

    public RaceSessionSuspensionReason suspensionReason() {
        return suspensionReason;
    }

    public long durationTicks() {
        return durationTicks;
    }

    public long elapsedTicks(long serverTick) {
        if (state != RaceSessionState.RUNNING) {
            return elapsedTicks;
        }
        requireTickNotBeforeAnchor(serverTick);
        return Math.addExact(elapsedTicks, serverTick - runningSinceTick);
    }

    public long remainingTicks(long serverTick) {
        return Math.max(0L, durationTicks - elapsedTicks(serverTick));
    }

    public boolean hasExpired(long serverTick) {
        return durationTicks > 0L && elapsedTicks(serverTick) >= durationTicks;
    }

    public void transitionTo(RaceSessionState target, long serverTick) {
        Objects.requireNonNull(target, "target");
        if (!isAllowed(state, target)) {
            throw new RaceSessionTransitionException(state, target);
        }
        if (state == RaceSessionState.RUNNING) {
            freezeClock(serverTick);
        }
        state = target;
        suspensionReason = RaceSessionSuspensionReason.NONE;
        if (target == RaceSessionState.RUNNING) {
            runningSinceTick = serverTick;
        }
    }

    public boolean suspend(RaceSessionSuspensionReason reason, long serverTick) {
        Objects.requireNonNull(reason, "reason");
        if (reason == RaceSessionSuspensionReason.NONE) {
            throw new IllegalArgumentException("A suspension requires a reason");
        }
        if (state == RaceSessionState.SUSPENDED) {
            return false;
        }
        if (!state.isActive()) {
            throw new RaceSessionTransitionException(state, RaceSessionState.SUSPENDED);
        }
        if (state == RaceSessionState.RUNNING) {
            freezeClock(serverTick);
        }
        suspendedFrom = state;
        state = RaceSessionState.SUSPENDED;
        suspensionReason = reason;
        return true;
    }

    public boolean onConnectedPlayerCountChanged(int connectedPlayers, long serverTick) {
        if (connectedPlayers < 0) {
            throw new IllegalArgumentException("connectedPlayers must not be negative");
        }
        if (connectedPlayers != 0 || !state.isActive()) {
            return false;
        }
        return suspend(RaceSessionSuspensionReason.EMPTY_SERVER, serverTick);
    }

    public void resume(long serverTick) {
        if (state != RaceSessionState.SUSPENDED || !suspendedFrom.isActive()) {
            throw new RaceSessionTransitionException(state, suspendedFrom);
        }
        state = suspendedFrom;
        suspensionReason = RaceSessionSuspensionReason.NONE;
        if (state == RaceSessionState.RUNNING) {
            runningSinceTick = serverTick;
        }
    }

    public Checkpoint checkpoint(long serverTick) {
        return new Checkpoint(durationTicks, state, suspendedFrom, suspensionReason, elapsedTicks(serverTick));
    }

    private void freezeClock(long serverTick) {
        elapsedTicks = elapsedTicks(serverTick);
        runningSinceTick = -1L;
    }

    private void requireTickNotBeforeAnchor(long serverTick) {
        if (serverTick < runningSinceTick) {
            throw new IllegalArgumentException("serverTick must not move backwards while a session is running");
        }
    }

    private static boolean isAllowed(RaceSessionState from, RaceSessionState to) {
        return switch (from) {
            case CONFIGURED -> to == RaceSessionState.OPEN || to == RaceSessionState.ABANDONED;
            case OPEN -> to == RaceSessionState.STAGING || to == RaceSessionState.RUNNING || to == RaceSessionState.ABANDONED;
            case STAGING -> to == RaceSessionState.COUNTDOWN || to == RaceSessionState.OPEN || to == RaceSessionState.ABANDONED;
            case COUNTDOWN -> to == RaceSessionState.RUNNING || to == RaceSessionState.STAGING || to == RaceSessionState.ABANDONED;
            case RUNNING -> to == RaceSessionState.FINISHING || to == RaceSessionState.ABANDONED;
            case SUSPENDED -> to == RaceSessionState.FINISHING || to == RaceSessionState.ABANDONED;
            case FINISHING -> to == RaceSessionState.PROVISIONAL || to == RaceSessionState.ABANDONED;
            case PROVISIONAL -> to == RaceSessionState.OFFICIAL || to == RaceSessionState.FINISHING || to == RaceSessionState.ABANDONED;
            case OFFICIAL, ABANDONED -> false;
        };
    }

    public record Checkpoint(long durationTicks, RaceSessionState state, RaceSessionState suspendedFrom,
                             RaceSessionSuspensionReason suspensionReason, long elapsedTicks) {
        public Checkpoint {
            if (durationTicks < 0L || elapsedTicks < 0L) {
                throw new IllegalArgumentException("Session ticks must not be negative");
            }
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(suspendedFrom, "suspendedFrom");
            Objects.requireNonNull(suspensionReason, "suspensionReason");
            if (state == RaceSessionState.SUSPENDED && !suspendedFrom.isActive()) {
                throw new IllegalArgumentException("A suspended session must remember an active state");
            }
        }
    }
}
