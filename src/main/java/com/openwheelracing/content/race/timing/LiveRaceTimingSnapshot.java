package com.openwheelracing.content.race.timing;

import java.util.List;
import java.util.UUID;

public record LiveRaceTimingSnapshot(
    boolean active,
    String suspensionReason,
    long sessionId,
    String sessionName,
    UUID trackId,
    UUID routeId,
    long revision,
    long serverTick,
    double routeLengthMeters,
    List<RaceTimingRow> rows,
    List<RacePositionChange> recentPositionChanges,
    int lapLimit,
    long remainingRaceTicks
) {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public LiveRaceTimingSnapshot {
        suspensionReason = suspensionReason == null ? "" : suspensionReason;
        sessionName = sessionName == null ? "" : sessionName;
        trackId = trackId == null ? ZERO_UUID : trackId;
        routeId = routeId == null ? ZERO_UUID : routeId;
        rows = List.copyOf(rows);
        recentPositionChanges = List.copyOf(recentPositionChanges);
        if (lapLimit < 0) {
            throw new IllegalArgumentException("lapLimit must not be negative");
        }
        if (remainingRaceTicks < -1L) {
            throw new IllegalArgumentException("remainingRaceTicks must be -1 or greater");
        }
    }

    public LiveRaceTimingSnapshot(boolean active, String suspensionReason, long sessionId, String sessionName, UUID trackId,
                                  UUID routeId, long revision, long serverTick, double routeLengthMeters,
                                  List<RaceTimingRow> rows, List<RacePositionChange> recentPositionChanges) {
        this(active, suspensionReason, sessionId, sessionName, trackId, routeId, revision, serverTick, routeLengthMeters,
            rows, recentPositionChanges, 0, -1L);
    }

    public static LiveRaceTimingSnapshot inactive(long revision, long serverTick, String reason) {
        return new LiveRaceTimingSnapshot(false, reason, 0L, "", ZERO_UUID, ZERO_UUID, revision, serverTick, 0.0,
            List.of(), List.of(), 0, -1L);
    }
}
