package com.openwheelracing.content.ai;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record BasicAiDriverIdentity(UUID driverId, UUID fleetId, UUID trackId, int gridIndex, String displayName, long seed) {
    public BasicAiDriverIdentity {
        if (driverId == null || fleetId == null || trackId == null) {
            throw new IllegalArgumentException("AI identity UUIDs are required");
        }
        gridIndex = Math.max(1, gridIndex);
        displayName = displayName == null || displayName.isBlank() ? "AI-%02d".formatted(gridIndex) : displayName;
    }

    public static BasicAiDriverIdentity create(UUID fleetId, UUID trackId, int gridIndex, int driverNumber) {
        String name = "AI-%02d".formatted(driverNumber);
        UUID driverId = UUID.nameUUIDFromBytes((fleetId + ":" + trackId + ":" + gridIndex).getBytes(StandardCharsets.UTF_8));
        long seed = driverId.getMostSignificantBits() ^ driverId.getLeastSignificantBits();
        return new BasicAiDriverIdentity(driverId, fleetId, trackId, gridIndex, name, seed);
    }
}
