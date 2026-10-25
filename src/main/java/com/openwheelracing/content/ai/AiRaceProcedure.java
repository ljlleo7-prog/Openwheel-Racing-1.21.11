package com.openwheelracing.content.ai;

import com.openwheelracing.content.race.RaceFlagMode;
import com.openwheelracing.content.race.session.RaceSessionState;

/** Per-car sporting authority. Pace overrides cannot grant permission to move. */
public final class AiRaceProcedure {
    public enum State {
        WAITING, FORMATION_COUNTDOWN, FORMATION, RETURNING_GRID, GRID_READY, RACE_COUNTDOWN,
        RACING, CAUTION, SUSPENDED, FINISHED, FAULT
    }

    public record Input(long sessionId, RaceSessionState sessionState, boolean eligible, int lights,
                        RaceFlagMode flag, BasicAiTrafficMode requestedMode, boolean localized,
                        boolean gridAssigned, boolean atGrid) { }

    public record Decision(State state, BasicAiTrafficMode mode) {
        public boolean holds() { return mode == BasicAiTrafficMode.HOLD; }
        public boolean trains() { return state == State.RACING && mode == BasicAiTrafficMode.RACE; }
        public boolean mayRecover() { return state == State.RACING || state == State.CAUTION; }
    }

    private long sessionId = Long.MIN_VALUE;
    private int previousLights = -1;
    private boolean redObserved;
    private boolean formationReleased;
    private boolean raceReleased;
    private boolean epochChanged;

    public boolean epochChanged() { return epochChanged; }

    public Decision advance(Input input) {
        epochChanged = sessionId != input.sessionId();
        if (epochChanged) {
            sessionId = input.sessionId();
            previousLights = -1;
            redObserved = false;
            formationReleased = false;
            raceReleased = false;
        }
        Decision decision = resolve(input);
        previousLights = input.lights();
        return decision;
    }

    private Decision resolve(Input input) {
        if (!input.eligible()) return hold(State.WAITING);
        if (input.sessionState() == RaceSessionState.SUSPENDED) return hold(State.SUSPENDED);
        if (input.sessionState() != null && (input.sessionState().isTerminal()
            || input.sessionState() == RaceSessionState.FINISHING || input.sessionState() == RaceSessionState.PROVISIONAL)) {
            return hold(State.FINISHED);
        }
        if (input.flag() == null || input.flag() == RaceFlagMode.RED) return hold(State.SUSPENDED);
        boolean parking = input.gridAssigned() && (input.sessionState() == RaceSessionState.AWAITING_START
            || input.sessionState() == null && formationReleased && input.lights() != 7);
        if (!input.localized() && !parking) return hold(State.FAULT);
        if (input.sessionState() != null) {
            return switch (input.sessionState()) {
                case CONFIGURED, OPEN -> hold(State.WAITING);
                case STAGING -> hold(input.atGrid() ? State.GRID_READY : State.WAITING);
                case FORMATION_COUNTDOWN -> hold(State.FORMATION_COUNTDOWN);
                case FORMATION_LAP -> input.lights() == 7
                    ? movement(State.FORMATION, BasicAiTrafficMode.FORMATION_HEATING, input) : hold(State.WAITING);
                case AWAITING_START -> returnToGrid(input);
                case COUNTDOWN -> hold(State.RACE_COUNTDOWN);
                case RUNNING -> input.lights() == 6 ? race(input) : hold(State.WAITING);
                default -> hold(State.FINISHED);
            };
        }
        // A stale lights-out value at arm/restart is never a new release.
        if (input.lights() >= 1 && input.lights() <= 5) {
            redObserved = true;
            raceReleased = false;
            return hold(formationReleased ? State.RACE_COUNTDOWN : State.FORMATION_COUNTDOWN);
        }
        if (input.lights() == 7) {
            formationReleased = true;
            redObserved = false;
            raceReleased = false;
            return movement(State.FORMATION, BasicAiTrafficMode.FORMATION_HEATING, input);
        }
        if (input.lights() == 6 && previousLights != 6 && redObserved) {
            raceReleased = true;
            redObserved = false;
        }
        if (input.lights() == 0) raceReleased = false;
        if (raceReleased) return race(input);
        return formationReleased ? returnToGrid(input) : hold(State.WAITING);
    }

    private static Decision returnToGrid(Input input) {
        if (!input.gridAssigned()) return hold(State.FAULT);
        return input.atGrid() ? hold(State.GRID_READY) : movement(State.RETURNING_GRID, BasicAiTrafficMode.GRID_RETURN, input);
    }

    private static Decision race(Input input) {
        BasicAiTrafficMode requested = input.requestedMode() == null ? BasicAiTrafficMode.AUTO : input.requestedMode();
        BasicAiTrafficMode pace = requested == BasicAiTrafficMode.AUTO ? BasicAiTrafficMode.RACE : requested;
        return movement(State.RACING, pace, input);
    }

    private static Decision movement(State state, BasicAiTrafficMode pace, Input input) {
        pace = BasicAiTrafficMode.resolve(pace, input.flag());
        if (pace == BasicAiTrafficMode.HOLD) return hold(State.SUSPENDED);
        return new Decision(state == State.RACING && input.flag() != RaceFlagMode.GREEN ? State.CAUTION : state, pace);
    }

    private static Decision hold(State state) {
        return new Decision(state, BasicAiTrafficMode.HOLD);
    }
}
