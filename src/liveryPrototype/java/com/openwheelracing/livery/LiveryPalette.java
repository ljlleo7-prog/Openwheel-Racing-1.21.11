package com.openwheelracing.livery;

public record LiveryPalette(int body, int accent1, int accent2) {
    public LiveryPalette {
        body = normalize(body);
        accent1 = normalize(accent1);
        accent2 = normalize(accent2);
    }

    public int colorFor(LiveryColorChannel channel) {
        return switch (channel) {
            case BODY -> body;
            case ACCENT_1 -> accent1;
            case ACCENT_2 -> accent2;
        };
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int normalize(int color) {
        return 0xFF000000 | (color & 0xFFFFFF);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
