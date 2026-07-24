package com.openwheelracing.livery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiveryTemplateRendererTest {
    private static final int BODY = LiveryPalette.rgb(10, 20, 30);
    private static final int ACCENT_1 = LiveryPalette.rgb(40, 50, 60);
    private static final int ACCENT_2 = LiveryPalette.rgb(70, 80, 90);
    private static final LiveryPalette PALETTE = new LiveryPalette(BODY, ACCENT_1, ACCENT_2);
    private static final LiveryObjModel SIMPLE_MODEL = new LiveryObjModel(List.of(
        new LiveryFace(-1.0f, 0.0f, -1.0f, 1.0f, 0.0f, -1.0f, -1.0f, 0.0f, 1.0f, 0xFF030303, "body"),
        new LiveryFace(-1.0f, 0.0f, -1.0f, 1.0f, 0.0f, -1.0f, -1.0f, 0.0f, 1.0f, 0xFFFFFFFF, "body"),
        new LiveryFace(-1.0f, 0.0f, -1.0f, 1.0f, 0.0f, -1.0f, -1.0f, 0.0f, 1.0f, 0xFF000000, "body")
    ));

    @Test
    void renderMaskColorsOnlyProjectedFacePixels() {
        LiveryTemplate template = new LiveryTemplate(16, 16, List.of(
            new LiveryTemplateRegion("body", 0, 0, 16, 16, LiveryProjection.TOP, -1.0, 1.0, -1.0, 1.0, face -> face.group().equals("body"))
        ));

        BufferedImage image = LiveryTemplateRenderer.renderMask(template, PALETTE, SIMPLE_MODEL);

        assertEquals(ACCENT_1, image.getRGB(1, 1));
        assertEquals(ACCENT_1, image.getRGB(6, 6));
        assertEquals(0x00000000, image.getRGB(14, 14));
        assertEquals(0x00000000, image.getRGB(15, 15));
    }

    @Test
    void renderMaskUsesMaterialClassificationForChannels() {
        LiveryTemplate template = new LiveryTemplate(16, 16, List.of(
            new LiveryTemplateRegion("all", 0, 0, 16, 16, LiveryProjection.TOP, -1.0, 1.0, -1.0, 1.0, face -> face.group().equals("body"))
        ));

        BufferedImage image = LiveryTemplateRenderer.renderMask(template, PALETTE, SIMPLE_MODEL);

        assertEquals(ACCENT_1, image.getRGB(1, 1));
        assertEquals(0x00000000, image.getRGB(14, 14));
    }

    @Test
    void prototypeCarMaskDoesNotFillEveryTemplateRectangle() throws IOException {
        LiveryTemplate template = LiveryTemplate.prototypeCar512();
        LiveryObjModel model = LiveryObjModel.load(Path.of("src/main/resources/assets/openwheelracing/objmodels/f1_car_2026.obj"));

        BufferedImage image = LiveryTemplateRenderer.renderMask(template, PALETTE, model);

        int opaquePixels = countOpaquePixels(image);
        int regionArea = template.regions().stream().mapToInt(region -> region.width() * region.height()).sum();
        assertTrue(opaquePixels > 0);
        assertTrue(opaquePixels < regionArea);
        assertEquals(0x00000000, image.getRGB(8, 8));
    }

    @Test
    void exportMaskWritesReadablePng(@TempDir Path tempDir) throws IOException {
        Path outputFile = tempDir.resolve("prototype_car_livery_mask.png");
        LiveryTemplate template = new LiveryTemplate(16, 16, List.of(
            new LiveryTemplateRegion("body", 0, 0, 16, 16, LiveryProjection.TOP, -1.0, 1.0, -1.0, 1.0, face -> face.group().equals("body"))
        ));

        Path written = LiveryTemplateExporter.exportMask(outputFile, template, PALETTE, SIMPLE_MODEL);
        BufferedImage image = ImageIO.read(written.toFile());

        assertTrue(Files.isRegularFile(written));
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        assertEquals(ACCENT_1, image.getRGB(1, 1));
        assertEquals(0x00000000, image.getRGB(14, 14));
    }

    private static int countOpaquePixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 255) != 0) {
                    count++;
                }
            }
        }
        return count;
    }
}
