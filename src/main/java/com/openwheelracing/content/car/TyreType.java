package com.openwheelracing.content.car;

public enum TyreType {
    SLICK("Slick", 1.0),
    INTERMEDIATE("Intermediate", 0.78),
    WET("Wet", 0.62);

    private final String displayName;
    private final double deformationHeatMultiplier;

    TyreType(String displayName, double deformationHeatMultiplier) {
        this.displayName = displayName;
        this.deformationHeatMultiplier = deformationHeatMultiplier;
    }

    public String displayName() { return displayName; }
    public double deformationHeatMultiplier() { return deformationHeatMultiplier; }
    public int id() { return ordinal(); }

    public static TyreType fromId(int id) {
        return values()[Math.max(0, Math.min(values().length - 1, id))];
    }
}
