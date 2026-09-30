package com.openwheelracing.client.screen;

import com.openwheelracing.content.race.LapTelemetryData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class LapTelemetryScreen extends Screen {
    private final RaceDirectorScreen parent;
    private final LapTelemetryData data;
    private int selectedSample;

    public LapTelemetryScreen(RaceDirectorScreen parent, LapTelemetryData data) {
        super(Component.literal("Lap telemetry"));
        this.parent = parent;
        this.data = data;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Paint an opaque background below so opening from the Race Director does not blur twice.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101820);
        graphics.drawCenteredString(font, "LAP TELEMETRY  |  " + data.driverName(), width / 2, 16, 0xFFE8EDF2);
        graphics.drawCenteredString(font, "Lap " + formatTime(data.lapMillis()) + "   Survey route " + Math.round(data.routeLength()) + " m", width / 2, 32, 0xFFC9D1D9);

        int margin = Math.max(24, width / 18);
        int gap = 22;
        int panelWidth = (width - margin * 2 - gap) / 2;
        int panelTop = 62;
        int panelBottom = height - 78;
        int mapLeft = margin;
        int graphLeft = margin + panelWidth + gap;
        graphics.fill(mapLeft - 6, panelTop - 16, mapLeft + panelWidth + 6, panelBottom + 8, 0xFF182630);
        graphics.fill(graphLeft - 6, panelTop - 16, graphLeft + panelWidth + 6, panelBottom + 8, 0xFF182630);
        graphics.drawString(font, "ACTUAL ROUTE  X/Z", mapLeft, panelTop - 13, 0xFF7EE787, false);
        graphics.drawString(font, "SPEED  /  SURVEYED DISTANCE", graphLeft, panelTop - 13, 0xFF7EE787, false);
        drawRoute(graphics, mapLeft, panelTop, panelWidth, panelBottom - panelTop);
        drawSpeed(graphics, graphLeft, panelTop, panelWidth, panelBottom - panelTop);
        drawReadout(graphics, panelBottom);
        graphics.drawCenteredString(font, "Click either plot to scrub both views   |   ESC returns to Race Director", width / 2, height - 20, 0xFF9AA7B2);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRoute(GuiGraphics graphics, int left, int top, int plotWidth, int plotHeight) {
        int count = data.worldXCm().length;
        if (count < 2) return;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            minX = Math.min(minX, data.worldXCm()[i]);
            maxX = Math.max(maxX, data.worldXCm()[i]);
            minZ = Math.min(minZ, data.worldZCm()[i]);
            maxZ = Math.max(maxZ, data.worldZCm()[i]);
        }
        double spanX = Math.max(100.0, maxX - minX);
        double spanZ = Math.max(100.0, maxZ - minZ);
        double scale = Math.min((plotWidth - 20.0) / spanX, (plotHeight - 20.0) / spanZ);
        int usedWidth = (int) Math.round(spanX * scale);
        int usedHeight = (int) Math.round(spanZ * scale);
        int offsetX = left + (plotWidth - usedWidth) / 2;
        int offsetY = top + (plotHeight - usedHeight) / 2;
        for (int i = 1; i < count; i++) {
            int x1 = offsetX + (int) Math.round((data.worldXCm()[i - 1] - minX) * scale);
            int y1 = offsetY + usedHeight - (int) Math.round((data.worldZCm()[i - 1] - minZ) * scale);
            int x2 = offsetX + (int) Math.round((data.worldXCm()[i] - minX) * scale);
            int y2 = offsetY + usedHeight - (int) Math.round((data.worldZCm()[i] - minZ) * scale);
            drawLine(graphics, x1, y1, x2, y2, statusColor(data.trackingStatus()[i]));
        }
        int selected = Math.max(0, Math.min(count - 1, selectedSample));
        int sx = offsetX + (int) Math.round((data.worldXCm()[selected] - minX) * scale);
        int sy = offsetY + usedHeight - (int) Math.round((data.worldZCm()[selected] - minZ) * scale);
        graphics.fill(sx - 3, sy - 3, sx + 4, sy + 4, 0xFFFFFFFF);
    }

    private void drawSpeed(GuiGraphics graphics, int left, int top, int plotWidth, int plotHeight) {
        int count = data.speedCmps().length;
        if (count < 2) return;
        int bottom = top + plotHeight;
        graphics.fill(left, top, left + plotWidth, bottom, 0xFF111A20);
        for (int step = 1; step < 4; step++) {
            int y = top + plotHeight * step / 4;
            graphics.fill(left, y, left + plotWidth, y + 1, 0xFF33434F);
        }
        int maxSpeed = 1;
        int maxDistance = 1;
        for (int value : data.speedCmps()) maxSpeed = Math.max(maxSpeed, value);
        for (int value : data.coveredDistanceCm()) maxDistance = Math.max(maxDistance, value);
        for (int i = 1; i < count; i++) {
            int x1 = left + (int) Math.round((plotWidth - 1.0) * data.coveredDistanceCm()[i - 1] / maxDistance);
            int y1 = bottom - 1 - (int) Math.round((plotHeight - 2.0) * data.speedCmps()[i - 1] / maxSpeed);
            int x2 = left + (int) Math.round((plotWidth - 1.0) * data.coveredDistanceCm()[i] / maxDistance);
            int y2 = bottom - 1 - (int) Math.round((plotHeight - 2.0) * data.speedCmps()[i] / maxSpeed);
            drawLine(graphics, x1, y1, x2, y2, statusColor(data.trackingStatus()[i]));
        }
        int selected = Math.max(0, Math.min(count - 1, selectedSample));
        int cursorX = left + (int) Math.round((plotWidth - 1.0) * data.coveredDistanceCm()[selected] / maxDistance);
        graphics.fill(cursorX, top, cursorX + 1, bottom, 0xAAFFFFFF);
    }

    private void drawReadout(GuiGraphics graphics, int y) {
        int selected = Math.max(0, Math.min(data.elapsedMillis().length - 1, selectedSample));
        String readout = formatTime(data.elapsedMillis()[selected]) + "  "
            + String.format(java.util.Locale.ROOT, "covered %.1f m  route s %.1f m  Y %.2f m  %.1f km/h",
                data.coveredDistanceCm()[selected] / 100.0, data.routeDistanceCm()[selected] / 100.0,
                data.worldYCm()[selected] / 100.0, data.speedCmps()[selected] * 0.036);
        graphics.drawCenteredString(font, readout, width / 2, y + 14, 0xFFE8EDF2);
        graphics.drawCenteredString(font, "Recorded samples: " + data.elapsedMillis().length, width / 2, y + 26, 0xFFC9D1D9);
    }

    private static int statusColor(byte status) {
        return switch (status) {
            case 0 -> 0xFF7EE787;
            case 1 -> 0xFFFFD166;
            case 2 -> 0xFFFFB454;
            default -> 0xFFFF6B6B;
        };
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 2, y0 + 2, color);
            if (x0 == x1 && y0 == y1) break;
            int twice = 2 * error;
            if (twice >= dy) { error += dy; x0 += sx; }
            if (twice <= dx) { error += dx; y0 += sy; }
        }
    }

    private static String formatTime(int millis) {
        return String.format(java.util.Locale.ROOT, "%d:%02d.%03d", millis / 60_000,
            millis / 1_000 % 60, millis % 1_000);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && data.elapsedMillis().length > 0
            && event.x() >= width / 2 && event.y() >= 50 && event.y() <= height - 35) {
            double fraction = Math.max(0.0, Math.min(1.0, (event.x() - width / 2.0) / Math.max(1.0, width / 2.0)));
            selectedSample = (int) Math.round((data.elapsedMillis().length - 1) * fraction);
            return true;
        }
        if (event.button() == 0 && event.x() < width / 2 && event.y() >= 50 && event.y() <= height - 35) {
            double fraction = Math.max(0.0, Math.min(1.0, (event.y() - 50.0) / Math.max(1.0, height - 85.0)));
            selectedSample = (int) Math.round((data.elapsedMillis().length - 1) * fraction);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
