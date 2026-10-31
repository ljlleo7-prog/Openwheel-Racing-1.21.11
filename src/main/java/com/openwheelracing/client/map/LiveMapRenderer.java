package com.openwheelracing.client.map;

import com.openwheelracing.content.race.map.LiveMapSnapshot.Car;
import com.openwheelracing.content.race.map.LiveMapSnapshot.Point;
import com.openwheelracing.content.track.TrackMapSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class LiveMapRenderer {
    private static final int FINISH_COLOR = 0xFFFFD54F;
    private static final int DIRECTION_COLOR = 0xFF42E6B5;

    private LiveMapRenderer() {}

    public static void renderHud(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!LiveMapClient.enabled() || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        int width = Math.min(240, Math.max(130, graphics.guiWidth() / 3));
        int height = Math.min(190, (graphics.guiHeight() - 54) / 2);
        int x = graphics.guiWidth() - width - 6;
        panel(graphics, x, 8, width, height, false);
        panel(graphics, x, height + 26, width, height, true);
        graphics.drawString(mc.font, Component.translatable("map.openwheelracing.expand"), x, height * 2 + 32, 0xFFCCD4DF, true);
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height, boolean finishView) {
        Minecraft mc = Minecraft.getInstance();
        graphics.fill(x, y, x + width, y + height, 0xEE101820);
        graphics.renderOutline(x, y, width, height, 0xFF526574);
        graphics.drawString(mc.font, Component.translatable(finishView ? "map.openwheelracing.finish" : "map.openwheelracing.whole"), x + 5, y + 4, 0xFFFFFFFF, false);
        int top = y + 17;
        var snapshot = LiveMapClient.snapshot();
        var geometry = LiveMapClient.geometry();
        if (snapshot == null || geometry == null) {
            graphics.drawString(mc.font, Component.translatable("map.openwheelracing.waiting"), x + 5, top + 5, 0xFFFFD54F, false);
            return;
        }
        if (finishView && geometry.finish() == null) {
            int textY = top + 5;
            for (var text : mc.font.split(Component.translatable("map.openwheelracing.no_finish"), Math.max(30, width - 10))) {
                graphics.drawString(mc.font, text, x + 5, textY, FINISH_COLOR, false);
                textY += 10;
            }
            return;
        }
        LiveMapProjection projection = projection(x + 3, top, width - 6, height - 21, finishView);
        graphics.enableScissor(projection.x(), projection.y(), projection.x() + projection.width(), projection.y() + projection.height());
        LiveMapBackground.draw(graphics, geometry, projection, finishView);
        if (geometry.finish() != null) {
            var finish = geometry.finish();
            int ax = projection.screenX(finish.left().x()), ay = projection.screenY(finish.left().z());
            int bx = projection.screenX(finish.right().x()), by = projection.screenY(finish.right().z());
            line(graphics, projection, ax, ay, bx, by, FINISH_COLOR);
            line(graphics, projection, ax + 1, ay, bx + 1, by, FINISH_COLOR);
            double centerX = (finish.left().x() + finish.right().x()) / 2;
            double centerZ = (finish.left().z() + finish.right().z()) / 2;
            int cx = projection.screenX(centerX), cy = projection.screenY(centerZ);
            arrow(graphics, projection, cx, cy, Math.cos(finish.heading()), Math.sin(finish.heading()), 22, DIRECTION_COLOR);
        }
        List<Car> cars = snapshot.cars().stream().sorted(Comparator.comparingInt(car -> LiveMapClient.COLORS.identity(car.driverId()).number())).toList();
        List<LabelBox> labels = new ArrayList<>();
        for (Car car : cars) {
            if (!projection.contains(car.x(), car.z())) continue;
            var identity = LiveMapClient.COLORS.identity(car.driverId());
            String label = "#" + identity.number() + " " + car.label();
            label = mc.font.plainSubstrByWidth(label, Math.max(30, projection.width() - 14));
            int sx = projection.screenX(car.x()), sy = projection.screenY(car.z());
            LabelBox box = placeLabel(projection, sx, sy, mc.font.width(label) + 2, labels);
            if (box == null) {
                // The numbered roster in the expanded view remains usable in a tightly packed grid.
                graphics.drawString(mc.font, "#" + identity.number(), sx + 3, sy - 8, identity.color(), true);
                continue;
            }
            labels.add(box);
            line(graphics, projection, sx, sy, Math.clamp(sx, box.x(), box.x() + box.width()), box.y() + 4, identity.color());
            graphics.fill(box.x(), box.y(), box.x() + box.width(), box.y() + 9, 0xE6101820);
            graphics.drawString(mc.font, label, box.x() + 1, box.y(), identity.color(), false);
        }
        // Keep position markers visible even when labels are densely packed.
        for (Car car : cars) {
            if (!projection.contains(car.x(), car.z())) continue;
            int sx = projection.screenX(car.x()), sy = projection.screenY(car.z());
            int color = LiveMapClient.COLORS.identity(car.driverId()).color();
            graphics.fill(sx - 3, sy - 3, sx + 4, sy + 4, 0xFF000000);
            graphics.fill(sx - 2, sy - 2, sx + 3, sy + 3, color);
            double radians = Math.toRadians(car.heading());
            arrow(graphics, projection, sx, sy, -Math.sin(radians), Math.cos(radians), 9, color);
        }
        graphics.drawString(mc.font, "N ^  E >", projection.x() + 2, projection.y() + 2, 0xFFB0C0CD, true);
        if (LiveMapClient.stale()) graphics.drawString(mc.font, Component.translatable("map.openwheelracing.stale"), projection.x() + 2, projection.y() + projection.height() - 10, 0xFFFF6868, true);
        graphics.disableScissor();
    }

    private static LiveMapProjection projection(int x, int y, int width, int height, boolean finishView) {
        var geometry = LiveMapClient.geometry();
        if (finishView) {
            double cx = (geometry.finish().left().x() + geometry.finish().right().x()) / 2;
            double cz = (geometry.finish().left().z() + geometry.finish().right().z()) / 2;
            int size = Math.min(width, height);
            return new LiveMapProjection(cx, cz, size / 200.0, x + (width - size) / 2, y + (height - size) / 2, size, size);
        }
        double minX = Double.POSITIVE_INFINITY, minZ = minX, maxX = Double.NEGATIVE_INFINITY, maxZ = maxX;
        TrackMapSnapshot map = geometry.surface();
        if (map.present()) { minX = map.minX(); minZ = map.minZ(); maxX = map.maxX() + 1; maxZ = map.maxZ() + 1; }
        List<Point> points = new ArrayList<>(geometry.route());
        if (geometry.finish() != null) { points.add(geometry.finish().left()); points.add(geometry.finish().right()); }
        for (Car car : LiveMapClient.snapshot().cars()) points.add(new Point(car.x(), car.z()));
        for (Point point : points) {
            minX = Math.min(minX, point.x()); minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x()); maxZ = Math.max(maxZ, point.z());
        }
        if (!Double.isFinite(minX)) { minX = -100; minZ = -100; maxX = 100; maxZ = 100; }
        return LiveMapProjection.fit(Math.floor((minX - 10) / 100) * 100, Math.floor((minZ - 10) / 100) * 100,
            Math.ceil((maxX + 10) / 100) * 100, Math.ceil((maxZ + 10) / 100) * 100, x, y, width, height);
    }

    private static LabelBox placeLabel(LiveMapProjection p, int sx, int sy, int width, List<LabelBox> labels) {
        for (int distance = 0; distance < p.height(); distance += 10) {
            for (int sign : new int[] {1, -1}) {
                int y = Math.clamp(sy - 4 + sign * distance, p.y() + 12, Math.max(p.y() + 12, p.y() + p.height() - 10));
                for (int side : new int[] {1, -1}) {
                    int x = Math.clamp(side == 1 ? sx + 5 : sx - width - 5, p.x() + 1, Math.max(p.x() + 1, p.x() + p.width() - width - 1));
                    LabelBox box = new LabelBox(x, y, width);
                    if (labels.stream().noneMatch(box::intersects)) return box;
                }
            }
        }
        return null;
    }

    private record LabelBox(int x, int y, int width) {
        boolean intersects(LabelBox other) { return x < other.x + other.width + 2 && x + width + 2 > other.x && y < other.y + 10 && y + 10 > other.y; }
    }

    private static void arrow(GuiGraphics graphics, LiveMapProjection p, int x, int y, double dx, double dy, int length, int color) {
        int tx = x + (int) Math.round(dx * length), ty = y + (int) Math.round(dy * length);
        line(graphics, p, x, y, tx, ty, color);
        line(graphics, p, tx, ty, tx - (int) Math.round(dx * 4 - dy * 3), ty - (int) Math.round(dy * 4 + dx * 3), color);
        line(graphics, p, tx, ty, tx - (int) Math.round(dx * 4 + dy * 3), ty - (int) Math.round(dy * 4 - dx * 3), color);
    }

    /** Clip before rasterizing: distant cars must not create million-pixel lines in the finish view. */
    private static void line(GuiGraphics graphics, LiveMapProjection p, int x1, int y1, int x2, int y2, int color) {
        rasterLine(p, x1, y1, x2, y2, (px, py) -> graphics.fill(px, py, px + 1, py + 1, color));
    }

    @FunctionalInterface
    interface PixelPlotter { void plot(int x, int y); }

    static void rasterLine(LiveMapProjection p, int x1, int y1, int x2, int y2, PixelPlotter plotter) {
        double dx = (double) x2 - x1, dy = (double) y2 - y1;
        double start = 0, end = 1;
        double[] edges = {-dx, dx, -dy, dy};
        double[] distances = {x1 - (double) p.x(), p.x() + p.width() - (double) x1, y1 - (double) p.y(), p.y() + p.height() - (double) y1};
        for (int i = 0; i < 4; i++) {
            if (edges[i] == 0) { if (distances[i] < 0) return; }
            else if (edges[i] < 0) start = Math.max(start, distances[i] / edges[i]);
            else end = Math.min(end, distances[i] / edges[i]);
        }
        if (start > end) return;
        double ax = x1 + start * dx, ay = y1 + start * dy;
        double bx = x1 + end * dx, by = y1 + end * dy;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(bx - ax), Math.abs(by - ay))));
        for (int i = 0; i <= steps; i++) {
            int px = (int) Math.round(ax + (bx - ax) * i / steps), py = (int) Math.round(ay + (by - ay) * i / steps);
            plotter.plot(px, py);
        }
    }
}
