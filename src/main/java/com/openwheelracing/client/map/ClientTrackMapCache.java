package com.openwheelracing.client.map;

import com.openwheelracing.content.track.TrackMapSnapshot;

public final class ClientTrackMapCache {
    private static TrackMapSnapshot current = TrackMapSnapshot.EMPTY;

    private ClientTrackMapCache() {
    }

    public static void set(TrackMapSnapshot snapshot) {
        TrackMapSnapshot next = snapshot == null ? TrackMapSnapshot.EMPTY : snapshot;
        if (next.revision() != current.revision() || !next.dimensionId().equals(current.dimensionId())) {
            CircuitMapRenderer.clearCache();
        }
        current = next;
    }

    public static TrackMapSnapshot current() {
        return current;
    }
}
