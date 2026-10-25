package com.openwheelracing.content.race;

import com.openwheelracing.content.ai.BasicAiDriverIdentity;
import com.openwheelracing.content.race.timing.RaceParticipantKind;

import java.util.UUID;

/** Resolves timing ownership without treating an empty autonomous car as a departed player. */
public record LapTimingDriver(UUID id, String name, RaceParticipantKind kind) {
    public static LapTimingDriver resolve(UUID playerId, String playerName, boolean playerAlive,
                                          BasicAiDriverIdentity ai, boolean autonomous) {
        if (playerId != null) {
            return playerAlive ? new LapTimingDriver(playerId, playerName, RaceParticipantKind.PLAYER) : null;
        }
        return ai != null && autonomous ? new LapTimingDriver(ai.driverId(), ai.displayName(), RaceParticipantKind.AI) : null;
    }
}
