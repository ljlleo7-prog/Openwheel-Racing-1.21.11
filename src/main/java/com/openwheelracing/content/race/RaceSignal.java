package com.openwheelracing.content.race;

public enum RaceSignal implements net.minecraft.util.StringRepresentable {
    OFF("off", 0),
    GREEN("green", 12),
    YELLOW("yellow", 15),
    RED("red", 15),
    BLUE("blue", 12),
    WHITE("white", 12),
    ORANGE("orange", 13),
    PURPLE("purple", 10);

    private final String key;
    private final int lightLevel;

    RaceSignal(String key, int lightLevel) {
        this.key = key;
        this.lightLevel = lightLevel;
    }

    public String key() {
        return key;
    }

    @Override public String getSerializedName() { return key; }

    public int lightLevel() {
        return lightLevel;
    }

    public static RaceSignal fromOrdinal(int ordinal) {
        RaceSignal[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : OFF;
    }
}
