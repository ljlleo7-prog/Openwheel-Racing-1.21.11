package com.openwheelracing.content.race;

/** Physical start-light pattern, shared by preparation and race start sequences. */
public final class RaceStartLights {
    public static final int FORMATION_GREEN = 7;

    public enum Lamp { OFF, RED, GREEN }

    private RaceStartLights() {
    }

    public static Lamp signal(int phase, int lightOrder) {
        if (lightOrder < 1 || lightOrder > 5) return Lamp.OFF;
        if (phase == FORMATION_GREEN) {
            return lightOrder == 2 || lightOrder == 4 ? Lamp.GREEN : Lamp.OFF;
        }
        return phase >= lightOrder && phase <= 5 ? Lamp.RED : Lamp.OFF;
    }
}
