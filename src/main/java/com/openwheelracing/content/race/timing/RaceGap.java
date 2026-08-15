package com.openwheelracing.content.race.timing;

public record RaceGap(Type type, long millis, int laps) {
    public enum Type {
        LEADER,
        TIME_MILLIS,
        LAPS,
        UNAVAILABLE
    }

    public RaceGap {
        if (type == null) {
            throw new IllegalArgumentException("Gap type is required");
        }
        millis = Math.max(0L, millis);
        laps = Math.max(0, laps);
    }

    public static RaceGap leader() {
        return new RaceGap(Type.LEADER, 0L, 0);
    }

    public static RaceGap time(long millis) {
        return new RaceGap(Type.TIME_MILLIS, millis, 0);
    }

    public static RaceGap laps(int laps) {
        return new RaceGap(Type.LAPS, 0L, Math.max(1, laps));
    }

    public static RaceGap unavailable() {
        return new RaceGap(Type.UNAVAILABLE, 0L, 0);
    }
}
