package com.openwheelracing.content.race.timing;

public enum RaceProgressConfidence {
    CONFIRMED,
    DEGRADED,
    AMBIGUOUS,
    UNTRACKED,
    STALE;

    public boolean canInitiatePositionChange() {
        return this == CONFIRMED || this == DEGRADED;
    }
}
