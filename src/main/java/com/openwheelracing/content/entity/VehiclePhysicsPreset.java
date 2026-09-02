package com.openwheelracing.content.entity;

import java.util.Locale;

public enum VehiclePhysicsPreset {
    CLASSIC,
    DYNAMIC;

    public boolean isDynamic() {
        return this == DYNAMIC;
    }

    public static VehiclePhysicsPreset fromName(String name) {
        if (name == null) return DYNAMIC;
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return DYNAMIC;
        }
    }
}
