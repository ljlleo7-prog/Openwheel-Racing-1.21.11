package com.openwheelracing.livery;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class LiveryTemplateExporter {
    private LiveryTemplateExporter() {}

    public static Path exportMask(Path outputFile, LiveryTemplate template, LiveryPalette palette, LiveryObjModel model) throws IOException {
        BufferedImage image = LiveryTemplateRenderer.renderMask(template, palette, model);
        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        ImageIO.write(image, "png", outputFile.toFile());
        return outputFile;
    }

    public static void main(String[] args) throws IOException {
        Path outputFile = args.length > 0 ? Path.of(args[0]) : Path.of("build/livery/prototype_car_livery_mask.png");
        Path modelFile = args.length > 1 ? Path.of(args[1]) : Path.of("src/main/resources/assets/openwheelracing/objmodels/f1_car_2026.obj");
        LiveryPalette palette = new LiveryPalette(
            LiveryPalette.rgb(235, 18, 32),
            LiveryPalette.rgb(0, 92, 255),
            LiveryPalette.rgb(255, 215, 0)
        );
        exportMask(outputFile, LiveryTemplate.prototypeCar512(), palette, LiveryObjModel.load(modelFile));
        System.out.println(outputFile.toAbsolutePath());
    }
}
