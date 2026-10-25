package com.openwheelracing.content.race.session;

public enum RaceSessionState {
    CONFIGURED,
    OPEN,
    STAGING,
    COUNTDOWN,
    RUNNING,
    SUSPENDED,
    FINISHING,
    PROVISIONAL,
    OFFICIAL,
    ABANDONED,
    FORMATION_COUNTDOWN,
    FORMATION_LAP,
    AWAITING_START;

    public boolean isActive() {
        return this == STAGING || this == COUNTDOWN || this == RUNNING
            || this == FORMATION_COUNTDOWN || this == FORMATION_LAP || this == AWAITING_START;
    }

    public boolean isTerminal() {
        return this == OFFICIAL || this == ABANDONED;
    }
}
