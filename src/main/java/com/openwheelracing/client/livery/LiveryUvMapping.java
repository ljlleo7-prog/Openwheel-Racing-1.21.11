package com.openwheelracing.client.livery;

public final class LiveryUvMapping {
    private LiveryUvMapping() {}

    public record Uv(float u, float v) {}

    public static Uv uvForVertex(String group, float x, float y, float z) {
        return switch (group) {
            case "Upper-Body" -> z < -2.95f
                ? uv(24, 24, 144, 92, -0.30f, 0.30f, -4.90f, -2.95f, x, z)
                : uv(184, 24, 160, 92, -0.45f, 0.45f, -3.30f, -1.45f, x, z);
            case "LeftSection" -> uv(24, 148, 216, 108, -4.15f, -1.95f, -0.25f, 0.25f, z, -y);
            case "RightSection" -> uv(272, 148, 216, 108, -4.15f, -1.95f, -0.25f, 0.25f, z, -y);
            case "Chassis" -> z < -2.95f
                ? uv(24, 24, 216, 128, -0.45f, 0.45f, -5.05f, -2.95f, x, z)
                : uv(272, 24, 216, 128, -0.50f, 0.50f, -2.95f, -1.45f, x, z);
            case "Left-FW-Endplate", "FW-Tip" -> uv(24, 184, 216, 56, -1.20f, -0.47f, -0.28f, 0.05f, z, -y);
            case "Right-FW-Endplate" -> uv(272, 184, 216, 56, -1.20f, -0.47f, -0.28f, 0.05f, z, -y);
            case "Wheel_Front_Left" -> uv(24, 288, 96, 64, -1.78f, -1.32f, -0.24f, 0.22f, z, -y);
            case "Wheel_Front_Right" -> uv(144, 288, 96, 64, -1.78f, -1.32f, -0.24f, 0.22f, z, -y);
            case "Wheel_Rear_Left" -> uv(272, 288, 96, 72, -4.78f, -4.26f, -0.24f, 0.24f, z, -y);
            case "Wheel_Rear_Right" -> uv(392, 288, 96, 72, -4.78f, -4.26f, -0.24f, 0.24f, z, -y);
            default -> null;
        };
    }

    public static boolean isPaintable(String group) {
        return switch (group) {
            case "Upper-Body", "LeftSection", "RightSection", "MidSection", "Chassis",
                 "Left-FW-Endplate", "FW-Tip", "Right-FW-Endplate",
                 "RW-Left-Endplate", "RW-Right-Endplate",
                 "Wheel_Front_Left", "Wheel_Front_Right", "Wheel_Rear_Left", "Wheel_Rear_Right" -> true;
            default -> false;
        };
    }

    public static boolean isPaintableMaterial(int materialRgb) {
        int rgb = materialRgb & 0x00FFFFFF;
        return rgb == 0x00030303 || rgb == 0x00FFFFFF;
    }

    private static Uv uv(int x, int y, int w, int h, float minU, float maxU, float minV, float maxV, float u, float v) {
        return new Uv(x + (u - minU) * w / (maxU - minU), y + (v - minV) * h / (maxV - minV));
    }
}
