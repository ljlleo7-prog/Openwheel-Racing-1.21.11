package com.openwheelracing.content.race;

public enum PitLightMode {
    ENTRY,
    EXIT,
    WEATHER;

    public static PitLightMode fromOrdinal(int ordinal) {
        PitLightMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ENTRY;
    }
}
