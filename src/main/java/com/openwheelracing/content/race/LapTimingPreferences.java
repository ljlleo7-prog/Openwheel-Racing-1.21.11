package com.openwheelracing.content.race;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LapTimingPreferences {
    private static final Map<UUID, LapTimingScope> SCOPES = new ConcurrentHashMap<>();

    private LapTimingPreferences() {
    }

    public static LapTimingScope get(UUID playerId) {
        return SCOPES.getOrDefault(playerId, LapTimingScope.SESSION);
    }

    public static void set(UUID playerId, LapTimingScope scope) {
        if (scope == LapTimingScope.SESSION) {
            SCOPES.remove(playerId);
        } else {
            SCOPES.put(playerId, scope);
        }
    }
}
