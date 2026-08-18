package com.openwheelracing.content.race.session;

public final class RaceSessionTransitionException extends IllegalStateException {
    public RaceSessionTransitionException(RaceSessionState from, RaceSessionState to) {
        super("Cannot transition race session from " + from + " to " + to);
    }
}
