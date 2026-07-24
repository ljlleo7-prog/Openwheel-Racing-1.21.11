package com.openwheelracing.livery;

import java.awt.image.BufferedImage;
import java.util.List;

public final class LiveryTemplateRenderer {
    private LiveryTemplateRenderer() {}

    public static BufferedImage renderMask(LiveryTemplate template, LiveryPalette palette, LiveryObjModel model) {
        BufferedImage image = new BufferedImage(template.width(), template.height(), BufferedImage.TYPE_INT_ARGB);
        for (LiveryTemplateRegion region : template.regions()) {
            for (LiveryFace face : model.faces()) {
                if (region.includes(face)) {
                    int color = palette.colorFor(LiveryFaceColor.channelFor(face));
                    fillTriangle(image, color,
                        region.pixelX(face.x0(), face.y0(), face.z0()), region.pixelY(face.x0(), face.y0(), face.z0()),
                        region.pixelX(face.x1(), face.y1(), face.z1()), region.pixelY(face.x1(), face.y1(), face.z1()),
                        region.pixelX(face.x2(), face.y2(), face.z2()), region.pixelY(face.x2(), face.y2(), face.z2())
                    );
                    if (face.vertexCount() == 4) {
                        fillTriangle(image, color,
                            region.pixelX(face.x0(), face.y0(), face.z0()), region.pixelY(face.x0(), face.y0(), face.z0()),
                            region.pixelX(face.x2(), face.y2(), face.z2()), region.pixelY(face.x2(), face.y2(), face.z2()),
                            region.pixelX(face.x3(), face.y3(), face.z3()), region.pixelY(face.x3(), face.y3(), face.z3())
                        );
                    }
                }
            }
        }
        return image;
    }

    public static BufferedImage renderMask(LiveryTemplate template, LiveryPalette palette) {
        return renderMask(template, palette, new LiveryObjModel(List.of(
            new LiveryFace(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 3, palette.body(), "test_body")
        )));
    }

    private static void fillTriangle(BufferedImage image, int color, double x0, double y0, double x1, double y1, double x2, double y2) {
        int minX = clampFloor(Math.min(x0, Math.min(x1, x2)), 0, image.getWidth() - 1);
        int maxX = clampCeil(Math.max(x0, Math.max(x1, x2)), 0, image.getWidth() - 1);
        int minY = clampFloor(Math.min(y0, Math.min(y1, y2)), 0, image.getHeight() - 1);
        int maxY = clampCeil(Math.max(y0, Math.max(y1, y2)), 0, image.getHeight() - 1);
        double area = edge(x0, y0, x1, y1, x2, y2);
        if (Math.abs(area) < 0.000001) {
            return;
        }

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                double w0 = edge(x1, y1, x2, y2, px, py);
                double w1 = edge(x2, y2, x0, y0, px, py);
                double w2 = edge(x0, y0, x1, y1, px, py);
                if ((w0 >= 0.0 && w1 >= 0.0 && w2 >= 0.0) || (w0 <= 0.0 && w1 <= 0.0 && w2 <= 0.0)) {
                    image.setRGB(x, y, color);
                }
            }
        }
    }

    private static double edge(double x0, double y0, double x1, double y1, double px, double py) {
        return (px - x0) * (y1 - y0) - (py - y0) * (x1 - x0);
    }

    private static int clampFloor(double value, int min, int max) {
        return Math.max(min, Math.min(max, (int) Math.floor(value)));
    }

    private static int clampCeil(double value, int min, int max) {
        return Math.max(min, Math.min(max, (int) Math.ceil(value)));
    }
}
