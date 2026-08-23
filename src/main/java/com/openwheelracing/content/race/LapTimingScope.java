package com.openwheelracing.content.race;

public enum LapTimingScope {
    SESSION,
    ALL_TIME;

    public static LapTimingScope fromOrdinal(int ordinal) {
        return ordinal == ALL_TIME.ordinal() ? ALL_TIME : SESSION;
    }
}
