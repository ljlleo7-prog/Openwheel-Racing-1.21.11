package com.openwheelracing.client.livery;

import com.mojang.blaze3d.platform.NativeImage;
import com.openwheelracing.client.render.ColoredObjModel;
import com.openwheelracing.content.car.CarLiveryColors;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class LiveryPreviewRenderer {
    private static final Identifier CAR_OBJ = Identifier.fromNamespaceAndPath("openwheelracing", "objmodels/f1_car_2026.obj");
    private static final float SOURCE_MIN_X = -0.806891f;
    private static final float SOURCE_MAX_X = 0.806891f;
    private static final float SOURCE_MIN_Y = -0.341034f;
    private static final float SOURCE_MAX_Y = 0.639583f;
    private static final float SOURCE_MIN_Z = -5.237548f;
    private static final float SOURCE_MAX_Z = -0.467113f;
    private static final float TARGET_WIDTH = 2.0f;
    private static final float TARGET_HEIGHT = 1.2f;
    private static final float TARGET_LENGTH = 5.5f;
    private static final float MODEL_X_SCALE = TARGET_WIDTH / (SOURCE_MAX_X - SOURCE_MIN_X);
    private static final float MODEL_Y_SCALE = TARGET_HEIGHT / (SOURCE_MAX_Y - SOURCE_MIN_Y);
    private static final float MODEL_Z_SCALE = TARGET_LENGTH / (SOURCE_MAX_Z - SOURCE_MIN_Z);
    private static final float MODEL_Z_CENTER = (SOURCE_MIN_Z + SOURCE_MAX_Z) * 0.5f;
    private static final int CARBON = rgb(34, 38, 48);
    private static final int TYRE = rgb(42, 46, 54);
    private static final int METAL = rgb(155, 155, 150);

    private static ColoredObjModel carModel;

    private LiveryPreviewRenderer() {}

    public static Preview render(NativeImage livery, CarLiveryColors fallbackColors, float yawDegrees, int width, int height) {
        loadModel();
        NativeImage image = new NativeImage(width, height, true);
        image.fillRect(0, 0, width, height, 0x00000000);
        float[] depth = new float[width * height];
        int[] hitU = new int[width * height];
        int[] hitV = new int[width * height];
        for (int i = 0; i < depth.length; i++) {
            depth[i] = Float.NEGATIVE_INFINITY;
            hitU[i] = -1;
            hitV[i] = -1;
        }

        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(16.0);
        float sinYaw = (float) Math.sin(yaw);
        float cosYaw = (float) Math.cos(yaw);
        float sinPitch = (float) Math.sin(pitch);
        float cosPitch = (float) Math.cos(pitch);
        float scale = Math.min(width / 2.45f, height / 6.5f);

        for (ColoredObjModel.Face face : carModel.faces) {
            // Back-face cull: skip faces whose normal points away from the camera
            if (!isFrontFacing(face.nx(), face.ny(), face.nz(), sinYaw, cosYaw, sinPitch, cosPitch)) {
                continue;
            }
            Vertex a = project(face.x0(), face.y0(), face.z0(), sinYaw, cosYaw, sinPitch, cosPitch, scale, width, height);
            Vertex b = project(face.x1(), face.y1(), face.z1(), sinYaw, cosYaw, sinPitch, cosPitch, scale, width, height);
            Vertex c = project(face.x2(), face.y2(), face.z2(), sinYaw, cosYaw, sinPitch, cosPitch, scale, width, height);
            Vertex d = project(face.x3(), face.y3(), face.z3(), sinYaw, cosYaw, sinPitch, cosPitch, scale, width, height);
            Uv auv = uvForVertex(face, face.x0(), face.y0(), face.z0());
            Uv buv = uvForVertex(face, face.x1(), face.y1(), face.z1());
            Uv cuv = uvForVertex(face, face.x2(), face.y2(), face.z2());
            Uv duv = uvForVertex(face, face.x3(), face.y3(), face.z3());
            int fallback = fallbackColor(face, fallbackColors);
            boolean paintable = LiveryUvMapping.isPaintableMaterial(face.materialRgb()) && face.hasLiveryRegion();
            int color = previewFaceColor(face, livery, fallback, paintable);
            rasterize(image, depth, hitU, hitV, a, b, c, auv, buv, cuv, color, paintable);
            rasterize(image, depth, hitU, hitV, a, c, d, auv, cuv, duv, color, paintable);
        }
        return new Preview(image, hitU, hitV, width, height);
    }

    private static int previewFaceColor(ColoredObjModel.Face face, NativeImage livery, int fallback, boolean paintable) {
        if (!paintable) {
            return fallback;
        }
        float x = (face.x0() + face.x1() + face.x2()) / 3.0f;
        float y = (face.y0() + face.y1() + face.y2()) / 3.0f;
        float z = (face.z0() + face.z1() + face.z2()) / 3.0f;
        Uv uv = uvForVertex(face, x, y, z);
        int u = clamp(Math.round(uv.u()), 0, ClientLiveryTextures.SIZE - 1);
        int v = clamp(Math.round(uv.v()), 0, ClientLiveryTextures.SIZE - 1);
        int sampled = livery.getPixel(u, v);
        return ((sampled >>> 24) & 255) > 0 ? sampled : fallback;
    }

    private static boolean isFrontFacing(float nx, float ny, float nz, float sinYaw, float cosYaw, float sinPitch, float cosPitch) {
        float yawNX = nx * cosYaw + nz * sinYaw;
        float yawNZ = -nx * sinYaw + nz * cosYaw;
        float viewZ = ny * sinPitch + yawNZ * cosPitch;
        return viewZ > 0.0f;
    }

    private static Vertex project(float sourceX, float sourceY, float sourceZ, float sinYaw, float cosYaw, float sinPitch, float cosPitch, float scale, int width, int height) {
        float x = sourceX * MODEL_X_SCALE;
        float y = (sourceY - SOURCE_MIN_Y) * MODEL_Y_SCALE - TARGET_HEIGHT * 0.48f;
        float z = (sourceZ - MODEL_Z_CENTER) * MODEL_Z_SCALE;
        float yawX = x * cosYaw + z * sinYaw;
        float yawZ = -x * sinYaw + z * cosYaw;
        float pitchY = y * cosPitch - yawZ * sinPitch;
        float pitchZ = y * sinPitch + yawZ * cosPitch;
        return new Vertex(width * 0.5f + yawX * scale, height * 0.54f - pitchY * scale, pitchZ);
    }

    private static void rasterize(NativeImage image, float[] depth, int[] hitU, int[] hitV, Vertex a, Vertex b, Vertex c, Uv auv, Uv buv, Uv cuv, int color, boolean paintable) {
        int minX = clamp((int) Math.floor(Math.min(a.x, Math.min(b.x, c.x))), 0, image.getWidth() - 1);
        int maxX = clamp((int) Math.ceil(Math.max(a.x, Math.max(b.x, c.x))), 0, image.getWidth() - 1);
        int minY = clamp((int) Math.floor(Math.min(a.y, Math.min(b.y, c.y))), 0, image.getHeight() - 1);
        int maxY = clamp((int) Math.ceil(Math.max(a.y, Math.max(b.y, c.y))), 0, image.getHeight() - 1);
        float area = edge(a, b, c.x, c.y);
        if (Math.abs(area) < 0.00001f) {
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                float py = y + 0.5f;
                float w0 = edge(b, c, px, py) / area;
                float w1 = edge(c, a, px, py) / area;
                float w2 = 1.0f - w0 - w1;
                if (w0 < 0.0f || w1 < 0.0f || w2 < 0.0f) {
                    continue;
                }
                float z = a.z * w0 + b.z * w1 + c.z * w2;
                int index = y * image.getWidth() + x;
                if (z <= depth[index]) {
                    continue;
                }
                depth[index] = z;
                if (paintable) {
                    hitU[index] = clamp(Math.round(auv.u * w0 + buv.u * w1 + cuv.u * w2), 0, ClientLiveryTextures.SIZE - 1);
                    hitV[index] = clamp(Math.round(auv.v * w0 + buv.v * w1 + cuv.v * w2), 0, ClientLiveryTextures.SIZE - 1);
                }
                image.setPixel(x, y, shadeByDepth(color, z));
            }
        }
    }

    private static float edge(Vertex a, Vertex b, float x, float y) {
        return (x - a.x) * (b.y - a.y) - (y - a.y) * (b.x - a.x);
    }

    private static Uv uvForVertex(ColoredObjModel.Face face, float x, float y, float z) {
        return new Uv(face.liveryPixelX(x, y, z), face.liveryPixelY(x, y, z));
    }

    private static int fallbackColor(ColoredObjModel.Face face, CarLiveryColors colors) {
        String group = face.group();
        if (isRubber(face)) {
            return TYRE;
        }
        if (isCarbonFibre(face)) {
            return CARBON;
        }
        if (group.equals("Underfloor-mid") || group.equals("Underfloor") || group.equals("Diffuser")) {
            return CARBON;
        }
        int r = (face.materialRgb() >> 16) & 255;
        int g = (face.materialRgb() >> 8) & 255;
        int b = face.materialRgb() & 255;
        int brightness = Math.max(r, Math.max(g, b));
        if (group.endsWith("-FW-Endplate") || group.startsWith("RW-")) {
            return brightness > 120 ? METAL : CARBON;
        }
        if (r == 0 && g == 0 && b == 0) {
            return CARBON;
        }
        if (r > 180 && g < 90 && b < 80) {
            return colors.accent2();
        }
        if (r > 220 && g > 180 && b > 120) {
            return colors.accent2();
        }
        if (brightness > 120 && Math.abs(r - g) < 18 && Math.abs(g - b) < 18) {
            return colors.accent1();
        }
        if (brightness > 120) {
            return METAL;
        }
        return colors.body();
    }

    private static boolean isCarbonFibre(ColoredObjModel.Face face) {
        return (face.materialRgb() & 0x00FFFFFF) == 0x00222630;
    }

    private static boolean isRubber(ColoredObjModel.Face face) {
        return (face.materialRgb() & 0x00FFFFFF) == 0x002A2E36;
    }

    private static int shadeByDepth(int color, float z) {
        float shade = Math.max(0.58f, Math.min(1.12f, 0.88f + z * 0.045f));
        int a = (color >>> 24) & 255;
        int r = Math.min(255, Math.round(((color >> 16) & 255) * shade));
        int g = Math.min(255, Math.round(((color >> 8) & 255) * shade));
        int b = Math.min(255, Math.round((color & 255) * shade));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void loadModel() {
        if (carModel == null) {
            carModel = ColoredObjModel.load(Minecraft.getInstance().getResourceManager(), CAR_OBJ);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public record Preview(NativeImage image, int[] hitU, int[] hitV, int width, int height) {
        public int hitU(int x, int y) {
            return hitU[y * width + x];
        }

        public int hitV(int x, int y) {
            return hitV[y * width + x];
        }
    }

    private record Vertex(float x, float y, float z) {}

    private record Uv(float u, float v) {}
}
