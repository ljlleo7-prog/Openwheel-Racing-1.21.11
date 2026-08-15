package com.openwheelracing.content.race.timing;

public record RacePositionChange(
    RaceParticipantKey participant,
    String displayName,
    int oldPosition,
    int newPosition,
    int completedLaps,
    double routeDistanceMeters,
    long serverTick
) {
    public RacePositionChange {
        if (participant == null) {
            throw new IllegalArgumentException("Position change participant is required");
        }
        displayName = displayName == null ? "" : displayName;
        oldPosition = Math.max(1, oldPosition);
        newPosition = Math.max(1, newPosition);
        completedLaps = Math.max(0, completedLaps);
    }

    public int positionsGained() {
        return oldPosition - newPosition;
    }
}
