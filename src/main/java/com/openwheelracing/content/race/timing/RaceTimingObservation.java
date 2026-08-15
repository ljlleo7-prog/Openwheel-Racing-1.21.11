package com.openwheelracing.content.race.timing;

public record RaceTimingObservation(
    RaceParticipantKey participant,
    String displayName,
    int entityId,
    double routeDistanceMeters,
    double speedMetersPerSecond,
    RaceProgressConfidence confidence,
    long serverTick,
    long preciseMillis,
    int initialOrderHint
) {
    public RaceTimingObservation {
        if (participant == null || confidence == null) {
            throw new IllegalArgumentException("Timing observation identity and confidence are required");
        }
        displayName = displayName == null || displayName.isBlank() ? participant.id().toString().substring(0, 8) : displayName;
        if (!Double.isFinite(routeDistanceMeters) || !Double.isFinite(speedMetersPerSecond)) {
            throw new IllegalArgumentException("Timing observation values must be finite");
        }
    }
}
