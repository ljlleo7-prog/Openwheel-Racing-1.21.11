package com.openwheelracing.livery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiveryTemplateRendererTest {
    private static final int BODY = LiveryPalette.rgb(10, 20, 30);
    private static final int ACCENT_1 = LiveryPalette.rgb(40, 50, 60);
    private static final int ACCENT_2 = LiveryPalette.rgb(70, 80, 90);
    private static final LiveryPalette PALETTE = new LiveryPalette(BODY, ACCENT_1, ACCENT_2);

    @Test
    void renderMaskColorsOnlyDeclaredRegions() {
        LiveryTemplate template = LiveryTemplate.prototypeCar512();
        BufferedImage image = LiveryTemplateRenderer.renderMask(template, PALETTE);

        assertEquals(512, image.getWidth());
        assertEquals(512, image.getHeight());
        assertEquals(BODY, image.getRGB(32, 32));
        assertEquals(BODY, image.getRGB(32, 156));
        assertEquals(ACCENT_1, image.getRGB(368, 32));
        assertEquals(ACCENT_1, image.getRGB(32, 296));
        assertEquals(ACCENT_2, image.getRGB(32, 376));
        assertEquals(ACCENT_2, image.getRGB(192, 456));
        assertEquals(0x00000000, image.getRGB(0, 0));
        assertEquals(0x00000000, image.getRGB(256, 256));
        assertEquals(0x00000000, image.getRGB(511, 511));
    }

    @Test
    void exportMaskWritesReadablePng(@TempDir Path tempDir) throws IOException {
        Path outputFile = tempDir.resolve("prototype_car_livery_mask.png");

        Path written = LiveryTemplateExporter.exportMask(outputFile, LiveryTemplate.prototypeCar512(), PALETTE);
        BufferedImage image = ImageIO.read(written.toFile());

        assertTrue(Files.isRegularFile(written));
        assertEquals(512, image.getWidth());
        assertEquals(512, image.getHeight());
        assertEquals(BODY, image.getRGB(32, 32));
        assertEquals(0x00000000, image.getRGB(0, 0));
    }
}
