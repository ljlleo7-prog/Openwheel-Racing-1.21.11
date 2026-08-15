package com.openwheelracing.client.hud;

import com.openwheelracing.content.race.timing.LiveRaceTimingSnapshot;
import com.openwheelracing.content.race.timing.RaceParticipantKey;
import com.openwheelracing.content.race.timing.RaceTimingRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class LiveRaceTimingClient {
    private static LiveRaceTimingSnapshot snapshot = LiveRaceTimingSnapshot.inactive(0L, 0L, "INACTIVE");

    private LiveRaceTimingClient() {
    }

    public static void apply(LiveRaceTimingSnapshot next) {
        if (next == null) {
            return;
        }
        boolean sameSession = snapshot.sessionId() == next.sessionId() && snapshot.trackId().equals(next.trackId()) && snapshot.routeId().equals(next.routeId());
        if (sameSession && next.revision() < snapshot.revision()) {
            return;
        }
        snapshot = next;
    }

    public static LiveRaceTimingSnapshot snapshot() {
        return snapshot;
    }

    public static boolean active() {
        return snapshot.active();
    }

    public static List<RaceTimingRow> rows() {
        return snapshot.rows();
    }

    public static Optional<RaceTimingRow> rowFor(UUID participantId) {
        if (participantId == null) {
            return Optional.empty();
        }
        return snapshot.rows().stream().filter(row -> row.participant().id().equals(participantId)).findFirst();
    }

    public static void clear() {
        snapshot = LiveRaceTimingSnapshot.inactive(snapshot.revision() + 1L, snapshot.serverTick(), "INACTIVE");
    }
}
