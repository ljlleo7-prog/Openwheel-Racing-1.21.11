package com.openwheelracing.content.block;

import net.minecraft.util.StringRepresentable;

public enum TrackMoisture implements StringRepresentable {
    DRY("dry", 0, 0.0),
    DAMP("damp", 1, 0.15),
    WET("wet", 2, 0.55),
    SOAKING("soaking", 3, 1.0);

    private final String name;
    private final int level;
    private final double depth;

    TrackMoisture(String name, int level, double depth) {
        this.name = name;
        this.level = level;
        this.depth = depth;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int level() {
        return level;
    }

    public double depth() {
        return depth;
    }

    public TrackMoisture wetter() {
        return values()[Math.min(values().length - 1, ordinal() + 1)];
    }

    public TrackMoisture drier() {
        return values()[Math.max(0, ordinal() - 1)];
    }
}
