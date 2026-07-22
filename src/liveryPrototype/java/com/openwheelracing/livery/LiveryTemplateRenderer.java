package com.openwheelracing.livery;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class LiveryTemplateRenderer {
    private LiveryTemplateRenderer() {}

    public static BufferedImage renderMask(LiveryTemplate template, LiveryPalette palette) {
        BufferedImage image = new BufferedImage(template.width(), template.height(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (LiveryTemplateRegion region : template.regions()) {
                graphics.setColor(new java.awt.Color(palette.colorFor(region.channel()), true));
                graphics.fillRect(region.x(), region.y(), region.width(), region.height());
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
