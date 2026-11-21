package com.openwheelracing.client.livery;

/** Image operations performed only while editing, never while drawing cars. */
public final class PictureLivery {
    private PictureLivery() {}

    public record Placement(int x, int y, int width, int height) {}

    public static Placement fit(int sourceWidth, int sourceHeight, int x, int y, int width, int height) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image and panel dimensions must be positive");
        }
        double scale = Math.min(width / (double) sourceWidth, height / (double) sourceHeight);
        int fittedWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int fittedHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        return new Placement(x + (width - fittedWidth) / 2, y + (height - fittedHeight) / 2, fittedWidth, fittedHeight);
    }

    public static int composite(int source, int background) {
        int alpha = source >>> 24;
        int inverse = 255 - alpha;
        int r = (((source >> 16) & 255) * alpha + ((background >> 16) & 255) * inverse + 127) / 255;
        int g = (((source >> 8) & 255) * alpha + ((background >> 8) & 255) * inverse + 127) / 255;
        int b = ((source & 255) * alpha + (background & 255) * inverse + 127) / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
