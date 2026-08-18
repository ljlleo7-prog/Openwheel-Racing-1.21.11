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
    ABANDONED;

    public boolean isActive() {
        return this == STAGING || this == COUNTDOWN || this == RUNNING;
    }

    public boolean isTerminal() {
        return this == OFFICIAL || this == ABANDONED;
    }
}
