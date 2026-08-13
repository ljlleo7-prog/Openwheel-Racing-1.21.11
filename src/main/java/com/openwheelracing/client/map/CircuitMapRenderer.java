package com.openwheelracing.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.race.TeamCarRow;
import com.openwheelracing.content.track.TrackMapSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CircuitMapRenderer {
    private static final int ASPHALT_COLOR = 0xFFE4E8EF;
    private static final int PIT_COLOR = 0xFF4F8FB3;
    private static final int BORDER_COLOR = 0xFF56616C;
    private static final int MIN_SURFACE_PIXELS = 2;
    private static final Map<String, SurfaceTexture> SURFACE_TEXTURES = new HashMap<>();

    private CircuitMapRenderer() {
    }

    public static void render(GuiGraphics graphics, TrackMapSnapshot map, List<TeamCarRow> cars, int x, int y, int width, int height, int selectedCarId, int leftCarId, int rightCarId) {
        graphics.fill(x, y, x + width, y + height, 0xCC050608);
        graphics.renderOutline(x, y, width, height, BORDER_COLOR);
        if (map == null || !map.present()) {
            return;
        }
        Projection projection = new Projection(map, x + 4, y + 4, Math.max(1, width - 8), Math.max(1, height - 8));
        drawSurface(graphics, map, projection);
        drawMapMarkers(graphics, map, projection);
        for (TeamCarRow car : cars) {
            drawCar(graphics, projection, car, carColor(car, selectedCarId, leftCarId, rightCarId));
        }
    }

    public static void renderLocal(GuiGraphics graphics, TrackMapSnapshot map, double carX, double carZ, float headingDegrees, int color, int x, int y, int width, int height) {
        if (map == null || !map.present()) {
            return;
        }
        Projection projection = new Projection(map, x + 4, y + 4, Math.max(1, width - 8), Math.max(1, height - 8));
        drawSurface(graphics, map, projection);
        drawMarker(graphics, projection.screenX(carX), projection.screenY(carZ), headingDegrees, color, true);
    }

    public static void clearCache() {
        Minecraft minecraft = Minecraft.getInstance();
        for (SurfaceTexture texture : SURFACE_TEXTURES.values()) {
            texture.close(minecraft);
        }
        SURFACE_TEXTURES.clear();
    }

    private static void drawSurface(GuiGraphics graphics, TrackMapSnapshot map, Projection projection) {
        SurfaceTexture texture = surfaceTexture(map, projection);
        if (texture == null || texture.location == null) {
            drawRuns(graphics, projection, map.asphaltRuns(), ASPHALT_COLOR);
            drawRuns(graphics, projection, map.pitRuns(), PIT_COLOR);
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture.location, projection.x, projection.y, 0.0f, 0.0f, projection.width, projection.height, texture.width, texture.height, texture.width, texture.height);
    }

    private static SurfaceTexture surfaceTexture(TrackMapSnapshot map, Projection projection) {
        if (projection.width <= 0 || projection.height <= 0) {
            return null;
        }
        String key = map.dimensionId() + ":" + map.revision() + ":" + projection.width + "x" + projection.height;
        SurfaceTexture cached = SURFACE_TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        pruneCache(minecraft, key);
        NativeImage image = new NativeImage(projection.width, projection.height, true);
        rasterizeRuns(image, projection.withOrigin(0, 0), map.asphaltRuns(), ASPHALT_COLOR);
        rasterizeRuns(image, projection.withOrigin(0, 0), map.pitRuns(), PIT_COLOR);
        Identifier location = Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "dynamic/circuit_map/" + sanitizeKey(key));
        DynamicTexture texture = new DynamicTexture(() -> "openwheelracing-circuit-map-" + sanitizeKey(key), image);
        minecraft.getTextureManager().register(location, texture);
        texture.upload();
        SurfaceTexture surfaceTexture = new SurfaceTexture(location, texture, image, projection.width, projection.height);
        SURFACE_TEXTURES.put(key, surfaceTexture);
        return surfaceTexture;
    }

    private static void pruneCache(Minecraft minecraft, String keepKey) {
        SURFACE_TEXTURES.entrySet().removeIf(entry -> {
            boolean remove = !entry.getKey().equals(keepKey) && SURFACE_TEXTURES.size() > 4;
            if (remove) {
                entry.getValue().close(minecraft);
            }
            return remove;
        });
    }

    private static String sanitizeKey(String key) {
        StringBuilder builder = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = Character.toLowerCase(key.charAt(i));
            builder.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '/' || c == '.' ? c : '_');
        }
        return builder.toString();
    }

    private static void drawRuns(GuiGraphics graphics, Projection projection, List<TrackMapSnapshot.CellRun> runs, int color) {
        for (TrackMapSnapshot.CellRun run : runs) {
            Rect rect = runRect(projection, run);
            graphics.fill(rect.left, rect.top, rect.right, rect.bottom, color);
        }
    }

    private static void rasterizeRuns(NativeImage image, Projection projection, List<TrackMapSnapshot.CellRun> runs, int color) {
        for (TrackMapSnapshot.CellRun run : runs) {
            Rect rect = runRect(projection, run);
            int left = Math.max(0, Math.min(image.getWidth(), rect.left));
            int right = Math.max(0, Math.min(image.getWidth(), rect.right));
            int top = Math.max(0, Math.min(image.getHeight(), rect.top));
            int bottom = Math.max(0, Math.min(image.getHeight(), rect.bottom));
            for (int y = top; y < bottom; y++) {
                for (int x = left; x < right; x++) {
                    image.setPixel(x, y, color);
                }
            }
        }
    }

    private static Rect runRect(Projection projection, TrackMapSnapshot.CellRun run) {
        int left = projection.screenX(run.startX());
        int right = projection.screenX(run.endX() + 1);
        int top = projection.screenY(run.z());
        int bottom = projection.screenY(run.z() + 1);
        if (right - left < MIN_SURFACE_PIXELS) {
            int center = (left + right) / 2;
            left = center - MIN_SURFACE_PIXELS / 2;
            right = left + MIN_SURFACE_PIXELS;
        }
        if (bottom - top < MIN_SURFACE_PIXELS) {
            int center = (top + bottom) / 2;
            top = center - MIN_SURFACE_PIXELS / 2;
            bottom = top + MIN_SURFACE_PIXELS;
        }
        return new Rect(left, top, right, bottom);
    }

    private static void drawMapMarkers(GuiGraphics graphics, TrackMapSnapshot map, Projection projection) {
        for (TrackMapSnapshot.MapPoint marker : map.checkpointMarkers()) {
            drawDot(graphics, projection.screenX(marker.x() + 0.5), projection.screenY(marker.z() + 0.5), 0xFFFFD044);
        }
        for (TrackMapSnapshot.MapPoint marker : map.startFinishMarkers()) {
            drawDot(graphics, projection.screenX(marker.x() + 0.5), projection.screenY(marker.z() + 0.5), 0xFFFFFFFF);
        }
    }

    private static void drawDot(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xCC000000);
        graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
    }

    private static void drawCar(GuiGraphics graphics, Projection projection, TeamCarRow car, int color) {
        drawMarker(graphics, projection.screenX(car.x()), projection.screenY(car.z()), car.headingDegrees(), color, car.onMap());
    }

    private static void drawMarker(GuiGraphics graphics, int x, int y, float headingDegrees, int color, boolean filled) {
        int markerColor = filled ? color : 0xFFFF7777;
        graphics.fill(x - 2, y - 2, x + 3, y + 3, markerColor);
        double radians = Math.toRadians(headingDegrees);
        int tipX = x + (int) Math.round(-Math.sin(radians) * 6.0);
        int tipY = y + (int) Math.round(Math.cos(radians) * 6.0);
        drawLine(graphics, x, y, tipX, tipY, 0xFFFFFFFF);
    }

    private static void drawLine(GuiGraphics graphics, int ax, int ay, int bx, int by, int color) {
        int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        if (steps == 0) {
            graphics.fill(ax, ay, ax + 1, ay + 1, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(ax + (bx - ax) * (i / (float) steps));
            int y = Math.round(ay + (by - ay) * (i / (float) steps));
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static int carColor(TeamCarRow car, int selectedCarId, int leftCarId, int rightCarId) {
        if (car.entityId() == selectedCarId) {
            return 0xFFFFD866;
        }
        if (car.entityId() == leftCarId || car.entityId() == rightCarId) {
            return 0xFF7EE787;
        }
        if (car.inPitLane()) {
            return 0xFF79C0FF;
        }
        return car.liveryColor();
    }

    private record Rect(int left, int top, int right, int bottom) {
    }

    private static final class SurfaceTexture {
        private final Identifier location;
        private final DynamicTexture texture;
        private final NativeImage image;
        private final int width;
        private final int height;

        private SurfaceTexture(Identifier location, DynamicTexture texture, NativeImage image, int width, int height) {
            this.location = location;
            this.texture = texture;
            this.image = image;
            this.width = width;
            this.height = height;
        }

        private void close(Minecraft minecraft) {
            if (minecraft != null && location != null) {
                minecraft.getTextureManager().release(location);
            }
            image.close();
        }
    }

    private static final class Projection {
        private final double minX;
        private final double minZ;
        private final double scale;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final double xPad;
        private final double zPad;

        private Projection(TrackMapSnapshot map, int x, int y, int width, int height) {
            this.minX = map.minX();
            this.minZ = map.minZ();
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            double mapWidth = Math.max(1.0, map.maxX() - map.minX() + 1.0);
            double mapHeight = Math.max(1.0, map.maxZ() - map.minZ() + 1.0);
            this.scale = Math.min(width / mapWidth, height / mapHeight);
            this.xPad = (width - mapWidth * scale) * 0.5;
            this.zPad = (height - mapHeight * scale) * 0.5;
        }

        private Projection(double minX, double minZ, double scale, int x, int y, int width, int height, double xPad, double zPad) {
            this.minX = minX;
            this.minZ = minZ;
            this.scale = scale;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.xPad = xPad;
            this.zPad = zPad;
        }

        private Projection withOrigin(int x, int y) {
            return new Projection(minX, minZ, scale, x, y, width, height, xPad, zPad);
        }

        private int screenX(double worldX) {
            return x + (int) Math.round(xPad + (worldX - minX) * scale);
        }

        private int screenY(double worldZ) {
            return y + (int) Math.round(zPad + (worldZ - minZ) * scale);
        }
    }
}
