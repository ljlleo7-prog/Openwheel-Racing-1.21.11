package com.openwheelracing.client.telemetry;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public final class SpeedTraceGraphRenderer {
    private static final int CURRENT_COLOR = 0xFF009E73;
    private static final int BEST_COLOR = 0xFF3D8BCD;

    private SpeedTraceGraphRenderer() {}

    public static void render(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        float routeLength = MonitorTelemetryClient.routeLength();
        List<MonitorTelemetryClient.Sample> current = MonitorTelemetryClient.current();
        int[] best = MonitorTelemetryClient.bestSpeeds();
        graphics.fill(x, y, x + width, y + height, 0xFF15191F);
        graphics.drawString(font, "Speed / route distance", x + 4, y + 3, 0xFFE8EDF2, false);
        graphics.fill(x + 4, y + 15, x + 12, y + 17, CURRENT_COLOR);
        graphics.drawString(font, "Current", x + 16, y + 12, 0xFFC9D1D9, false);
        for (int px = x + 70; px < x + 78; px += 3) graphics.fill(px, y + 15, px + 2, y + 17, BEST_COLOR);
        graphics.drawString(font, "Personal best", x + 82, y + 12, 0xFFC9D1D9, false);
        int plotX = x + 28, plotY = y + 29, plotW = width - 34, plotH = height - 42;
        graphics.fill(plotX, plotY, plotX + 1, plotY + plotH, 0xFF56616C);
        graphics.fill(plotX, plotY + plotH, plotX + plotW, plotY + plotH + 1, 0xFF56616C);
        if (routeLength <= 0.0f) {
            graphics.drawString(font, "NO ROUTE TELEMETRY", plotX + 8, plotY + plotH / 2, 0xFF88909A, false);
            return;
        }
        float maxSpeed = 100.0f;
        for (int speed : best) maxSpeed = Math.max(maxSpeed, speed * 0.036f);
        for (var sample : current) maxSpeed = Math.max(maxSpeed, sample.speedKmh());
        drawBest(graphics, best, MonitorTelemetryClient.bestSpacing(), routeLength, maxSpeed, plotX, plotY, plotW, plotH);
        drawCurrent(graphics, current, routeLength, maxSpeed, plotX, plotY, plotW, plotH);
        graphics.drawString(font, "0", plotX, plotY + plotH + 3, 0xFF88909A, false);
        String end = String.format(java.util.Locale.ROOT, "%.1f km", routeLength / 1000.0f);
        graphics.drawString(font, end, plotX + plotW - font.width(end), plotY + plotH + 3, 0xFF88909A, false);
        String max = Math.round(maxSpeed) + " km/h";
        graphics.drawString(font, max, plotX + 3, plotY + 2, 0xFF88909A, false);
    }

    private static void drawBest(GuiGraphics graphics, int[] speeds, float spacing, float routeLength, float maxSpeed, int x, int y, int width, int height) {
        if (speeds.length < 2 || spacing <= 0.0f) return;
        int previousX = x, previousY = y + height - Math.round(speeds[0] * 0.036f / maxSpeed * height);
        for (int i = 1; i < speeds.length; i++) {
            int nextX = x + Math.round(Math.min(routeLength, i * spacing) / routeLength * width);
            int nextY = y + height - Math.round(speeds[i] * 0.036f / maxSpeed * height);
            if ((i / 3 & 1) == 0) drawLine(graphics, previousX, previousY, nextX, nextY, BEST_COLOR);
            previousX = nextX; previousY = nextY;
        }
    }

    private static void drawCurrent(GuiGraphics graphics, List<MonitorTelemetryClient.Sample> samples, float routeLength, float maxSpeed, int x, int y, int width, int height) {
        MonitorTelemetryClient.Sample previous = null;
        for (var sample : samples) {
            if (sample.status() == com.openwheelracing.content.track.survey.SurveyRouteLocalizer.Status.AMBIGUOUS || sample.status() == com.openwheelracing.content.track.survey.SurveyRouteLocalizer.Status.UNTRACKED) {
                previous = null;
                continue;
            }
            if (previous != null) {
                drawLine(graphics, x + Math.round(previous.routeDistance() / routeLength * width), y + height - Math.round(previous.speedKmh() / maxSpeed * height),
                    x + Math.round(sample.routeDistance() / routeLength * width), y + height - Math.round(sample.speedKmh() / maxSpeed * height), CURRENT_COLOR);
            }
            previous = sample;
        }
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1, dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1, error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 2, y0 + 2, color);
            if (x0 == x1 && y0 == y1) break;
            int twice = 2 * error;
            if (twice >= dy) { error += dy; x0 += sx; }
            if (twice <= dx) { error += dx; y0 += sy; }
        }
    }
}
