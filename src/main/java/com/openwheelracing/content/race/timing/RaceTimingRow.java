package com.openwheelracing.content.race.timing;

public record RaceTimingRow(
    int position,
    RaceParticipantKey participant,
    String displayName,
    int entityId,
    int completedLaps,
    double routeDistanceMeters,
    double absoluteProgressMeters,
    RaceProgressConfidence confidence,
    RaceGap gapToLeader,
    RaceGap intervalAhead,
    int positionChange
) {
    public RaceTimingRow {
        if (participant == null || confidence == null || gapToLeader == null || intervalAhead == null) {
            throw new IllegalArgumentException("Complete timing row data is required");
        }
        displayName = displayName == null ? "" : displayName;
        position = Math.max(1, position);
        completedLaps = Math.max(0, completedLaps);
    }
}
