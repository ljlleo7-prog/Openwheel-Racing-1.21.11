package com.openwheelracing.content.race.timing;

import java.util.UUID;

public record RaceParticipantKey(UUID id, RaceParticipantKind kind) {
    public RaceParticipantKey {
        if (id == null || kind == null) {
            throw new IllegalArgumentException("Race participant identity is required");
        }
    }
}
