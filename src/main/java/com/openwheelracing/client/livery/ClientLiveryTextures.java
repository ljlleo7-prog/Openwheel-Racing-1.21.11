package com.openwheelracing.client.livery;

import com.mojang.blaze3d.platform.NativeImage;
import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.client.render.ColoredObjModel;
import com.openwheelracing.content.car.CarLiveryTexture;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class ClientLiveryTextures {
    public static final int SIZE = 512;

    public record TemplateRegion(String group, int x, int y, int width, int height) {}
    private static final Map<String, Identifier> LOCATIONS = new HashMap<>();
    private static final Map<String, NativeImage> IMAGES = new HashMap<>();
    private static final java.util.Set<String> MISSING = new java.util.HashSet<>();

    private ClientLiveryTextures() {}

    public static Path directory(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve("openwheelracing").resolve("liveries");
    }

    public static Optional<Identifier> textureLocation(CarLiveryTexture texture) {
        if (texture == null || !texture.isPresent()) {
            return Optional.empty();
        }
        String id = texture.id();
        if (MISSING.contains(id)) {
            return Optional.empty();
        }
        Identifier cached = LOCATIONS.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        Minecraft minecraft = Minecraft.getInstance();
        Path file = file(minecraft, id);
        if (!Files.isRegularFile(file)) {
            MISSING.add(id);
            return Optional.empty();
        }
        try (InputStream input = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(input);
            if (image.getWidth() != SIZE || image.getHeight() != SIZE) {
                image.close();
                return Optional.empty();
            }
            return Optional.of(register(minecraft, id, image));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }


    public static List<TemplateRegion> templateRegions(Minecraft minecraft) {
        Identifier objLoc = Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "objmodels/f1_car_2026.obj");
        ColoredObjModel model = ColoredObjModel.load(minecraft.getResourceManager(), objLoc);
        Map<String, TemplateRegion> regions = new LinkedHashMap<>();
        for (ColoredObjModel.Face face : model.faces) {
            if (!face.hasLiveryRegion() || !LiveryUvMapping.isPaintableMaterial(face.materialRgb())) {
                continue;
            }
            String key = face.group() + ':' + face.liveryRegionX() + ':' + face.liveryRegionY() + ':' + face.liveryRegionWidth() + ':' + face.liveryRegionHeight();
            regions.putIfAbsent(key, new TemplateRegion(face.group(), face.liveryRegionX(), face.liveryRegionY(), face.liveryRegionWidth(), face.liveryRegionHeight()));
        }
        List<TemplateRegion> sorted = new ArrayList<>(regions.values());
        sorted.sort(Comparator.comparingInt(TemplateRegion::y).thenComparingInt(TemplateRegion::x));
        return sorted;
    }

    public static NativeImage loadExisting(Minecraft minecraft, CarLiveryTexture texture) {
        if (texture == null || !texture.isPresent()) {
            return null;
        }
        String id = texture.id();
        NativeImage cached = IMAGES.get(id);
        if (cached != null) {
            return cached;
        }
        if (MISSING.contains(id)) {
            return null;
        }
        NativeImage loaded = loadImage(minecraft, id);
        if (loaded != null) {
            register(minecraft, id, loaded);
            return loaded;
        }
        MISSING.add(id);
        return null;
    }

    public static NativeImage loadOrCreate(Minecraft minecraft, CarLiveryTexture texture, int fallbackColor) {
        if (texture != null && texture.isPresent()) {
            String id = texture.id();
            if (MISSING.contains(id)) {
                NativeImage image = new NativeImage(SIZE, SIZE, true);
                fillTemplate(image, fallbackColor);
                return image;
            }
            NativeImage cached = IMAGES.get(id);
            if (cached != null) {
                return cached;
            }
            NativeImage loaded = loadImage(minecraft, id);
            if (loaded != null) {
                register(minecraft, id, loaded);
                return loaded;
            }
            MISSING.add(id);
        }
        NativeImage image = new NativeImage(SIZE, SIZE, true);
        fillTemplate(image, fallbackColor);
        return image;
    }

    public static CarLiveryTexture saveNew(Minecraft minecraft, NativeImage image) throws IOException {
        String id = "livery_" + System.currentTimeMillis();
        save(minecraft, id, image);
        return new CarLiveryTexture(id);
    }

    public static void save(Minecraft minecraft, String id, NativeImage image) throws IOException {
        String safe = CarLiveryTexture.sanitize(id);
        Files.createDirectories(directory(minecraft));
        image.writeToFile(file(minecraft, safe));
        register(minecraft, safe, copy(image));
    }

    public static void saveSynced(Minecraft minecraft, String id, byte[] pngBytes) throws IOException {
        String safe = CarLiveryTexture.sanitize(id);
        if (safe.isEmpty() || pngBytes.length == 0) {
            return;
        }
        NativeImage image = NativeImage.read(pngBytes);
        if (image.getWidth() != SIZE || image.getHeight() != SIZE) {
            image.close();
            return;
        }
        Files.createDirectories(directory(minecraft));
        Files.write(file(minecraft, safe), pngBytes);
        register(minecraft, safe, image);
    }

    public static byte[] readPngBytes(Minecraft minecraft, String id) throws IOException {
        return Files.readAllBytes(file(minecraft, id));
    }

    public static Path file(Minecraft minecraft, String id) {
        return directory(minecraft).resolve(CarLiveryTexture.sanitize(id) + ".png");
    }

    public static Identifier register(Minecraft minecraft, String id, NativeImage image) {
        String safe = CarLiveryTexture.sanitize(id);
        MISSING.remove(safe);
        Identifier location = Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "dynamic/livery/" + safe);
        Identifier old = LOCATIONS.put(safe, location);
        if (old != null) {
            minecraft.getTextureManager().release(old);
        }
        NativeImage oldImage = IMAGES.put(safe, image);
        if (oldImage != null) {
            oldImage.close();
        }
        DynamicTexture texture = new DynamicTexture(() -> "openwheelracing-livery-" + safe, image);
        minecraft.getTextureManager().register(location, texture);
        texture.upload();
        return location;
    }

    private static NativeImage loadImage(Minecraft minecraft, String id) {
        Path file = file(minecraft, id);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream input = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(input);
            if (image.getWidth() == SIZE && image.getHeight() == SIZE) {
                return image;
            }
            image.close();
        } catch (IOException ignored) {
        }
        return null;
    }

    public static void fillTemplate(NativeImage image, int baseColor) {
        Minecraft minecraft = Minecraft.getInstance();
        Identifier objLoc = Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "objmodels/f1_car_2026.obj");
        ColoredObjModel model = ColoredObjModel.load(minecraft.getResourceManager(), objLoc);
        image.fillRect(0, 0, SIZE, SIZE, 0x00000000);
        for (ColoredObjModel.Face face : model.faces) {
            if (!LiveryUvMapping.isPaintableMaterial(face.materialRgb()) || !face.hasLiveryRegion()) {
                continue;
            }
            int color = regionColor(face.group(), baseColor);
            rasterizeTriangle(image, color,
                face.liveryPixelX(face.x0(), face.y0(), face.z0()), face.liveryPixelY(face.x0(), face.y0(), face.z0()),
                face.liveryPixelX(face.x1(), face.y1(), face.z1()), face.liveryPixelY(face.x1(), face.y1(), face.z1()),
                face.liveryPixelX(face.x2(), face.y2(), face.z2()), face.liveryPixelY(face.x2(), face.y2(), face.z2())
            );
            if (face.vertexCount() == 4) {
                rasterizeTriangle(image, color,
                    face.liveryPixelX(face.x0(), face.y0(), face.z0()), face.liveryPixelY(face.x0(), face.y0(), face.z0()),
                    face.liveryPixelX(face.x2(), face.y2(), face.z2()), face.liveryPixelY(face.x2(), face.y2(), face.z2()),
                    face.liveryPixelX(face.x3(), face.y3(), face.z3()), face.liveryPixelY(face.x3(), face.y3(), face.z3())
                );
            }
        }
    }

    private static int regionColor(String group, int baseColor) {
        return switch (group) {
            case "Upper-Body" -> baseColor;
            case "LeftSection", "RightSection" -> shade(baseColor, 0.86);
            case "MidSection" -> shade(baseColor, 0.65);
            case "Left-FW-Endplate", "Right-FW-Endplate", "FW-Tip" -> shade(baseColor, 0.78);
            case "RW-Left-Endplate", "RW-Right-Endplate" -> shade(baseColor, 0.70);
            default -> baseColor;
        };
    }

    private static void rasterizeTriangle(NativeImage image, int color, float x0, float y0, float x1, float y1, float x2, float y2) {
        int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
        int maxX = Math.min(SIZE - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
        int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
        int maxY = Math.min(SIZE - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
        float area = triangleArea(x0, y0, x1, y1, x2, y2);
        if (Math.abs(area) < 0.00001f) {
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                float py = y + 0.5f;
                float w0 = edge(x1, y1, x2, y2, px, py);
                float w1 = edge(x2, y2, x0, y0, px, py);
                float w2 = edge(x0, y0, x1, y1, px, py);
                if ((w0 >= 0 && w1 >= 0 && w2 >= 0) || (w0 <= 0 && w1 <= 0 && w2 <= 0)) {
                    image.setPixel(x, y, color);
                }
            }
        }
    }

    private static float edge(float x0, float y0, float x1, float y1, float px, float py) {
        return (px - x0) * (y1 - y0) - (py - y0) * (x1 - x0);
    }

    private static float triangleArea(float x0, float y0, float x1, float y1, float x2, float y2) {
        return (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
    }

    private static int shade(int color, double multiplier) {
        int a = (color >>> 24) & 255;
        int r = (int) (((color >> 16) & 255) * multiplier);
        int g = (int) (((color >> 8) & 255) * multiplier);
        int b = (int) ((color & 255) * multiplier);
        return (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    private static NativeImage copy(NativeImage source) {
        NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), true);
        image.copyFrom(source);
        return image;
    }
}
