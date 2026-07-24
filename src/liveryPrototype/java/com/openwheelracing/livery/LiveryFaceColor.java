package com.openwheelracing.livery;

public final class LiveryFaceColor {
    private LiveryFaceColor() {}

    public static LiveryColorChannel channelFor(LiveryFace face) {
        String group = face.group();
        if (group.equals("Underfloor-mid") || group.equals("Underfloor") || group.equals("Diffuser")) {
            return null;
        }

        int rgb = face.materialRgb() & 0x00FFFFFF;
        if (rgb == 0x00030303) {
            return LiveryColorChannel.BODY;
        }
        if (rgb == 0x00FFFFFF) {
            return LiveryColorChannel.ACCENT_1;
        }
        return null;
    }
}
