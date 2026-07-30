package com.openwheelracing.client.livery;

public final class LiveryUvMapping {
    private LiveryUvMapping() {}

    public static boolean isPaintableMaterial(int materialRgb) {
        int rgb = materialRgb & 0x00FFFFFF;
        return rgb == 0x00030303 || rgb == 0x00FFFFFF;
    }
}
