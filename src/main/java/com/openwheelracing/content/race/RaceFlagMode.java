package com.openwheelracing.content.race;

public enum RaceFlagMode {
    GREEN("green"),
    YELLOW("yellow"),
    RED("red"),
    SAFETY_CAR("safety_car"),
    VIRTUAL_SAFETY_CAR("virtual_safety_car");

    public static final RaceFlagMode DEFAULT = GREEN;

    private final String key;

    RaceFlagMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static RaceFlagMode fromOrdinal(int ordinal) {
        RaceFlagMode[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return DEFAULT;
        }
        return values[ordinal];
    }
}
