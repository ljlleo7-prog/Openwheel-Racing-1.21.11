package com.openwheelracing.client.hud;

import com.openwheelracing.client.input.WheelInputSettings;
import com.openwheelracing.client.map.CircuitMapRenderer;
import com.openwheelracing.client.map.ClientTrackMapCache;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.race.OWRLapRecords;
import com.openwheelracing.content.race.RaceFlagMode;
import com.openwheelracing.content.track.TrackMapSnapshot;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class CarHudOverlay {
    private static final int PRIMARY_WIDTH = 236;
    private static final int PRIMARY_HEIGHT = 76;
    private static final int SHIFT_LIGHT_COUNT = 15;
    private static final int SHIFT_LIGHT_GROUP_SIZE = 5;
    private static int lastTemperatureCarId = -1;
    private static float displayedTyreTemperatureC = Float.NaN;

    private CarHudOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof OpenwheelCarEntity car)) {
            return;
        }

        Font font = minecraft.font;
        WheelInputSettings settings = WheelInputSettings.get();

        if (settings.showDrivingHud) {
            renderGlobalFlagMarker(graphics);
            renderLapDeltaBar(graphics, font);
            renderLiveTiming(graphics, font, car);
            renderPrimaryHud(graphics, font, car);
            renderCarStatus(graphics, font, car);
        }

        if (settings.showPhysicsDebugHud) {
            renderPhysicsDebug(graphics, font, car);
        }

        if (settings.showRankingHud) {
            renderRankingBoard(graphics, font);
        }
    }

    private static void renderLiveTiming(GuiGraphics graphics, Font font, OpenwheelCarEntity car) {
        int panelWidth = 146;
        int panelHeight = 106;
        int x = graphics.guiWidth() - panelWidth - 8;
        int y = 8;

        int displayedLapTicks = car.getCompletedLapLingerTicks() > 0 ? car.getCompletedLapTicks() : car.getCurrentLapTicks();
        int lapColor = car.getCompletedLapLingerTicks() > 0 ? completedLapColor(car.getCompletedLapResult()) : 0xFFE8E8E8;
        String lap = formatLapTime(displayedLapTicks);
        String best = formatLapTime(car.getBestLapTicks());
        String checkpointProgress = LapDeltaClient.segmentCount() > 0 ? LapDeltaClient.hitCount() + "/" + LapDeltaClient.segmentCount() : (car.hasCheckpoint() ? "1/1" : "0/1");
        graphics.drawString(font, "LAP", x + 7, y + 5, 0xFF88909A, false);
        graphics.drawString(font, lap, x + panelWidth - 7 - font.width(lap), y + 5, lapColor, false);
        graphics.drawString(font, "BEST", x + 7, y + 16, 0xFF88909A, false);
        graphics.drawString(font, best, x + panelWidth - 7 - font.width(best), y + 16, 0xFFFFFF99, false);
        graphics.drawString(font, "CP", x + 7, y + 27, 0xFF88909A, false);
        graphics.drawString(font, checkpointProgress, x + panelWidth - 7 - font.width(checkpointProgress), y + 27, LapDeltaClient.hitCount() > 0 || car.hasCheckpoint() ? 0xFF7EE787 : 0xFFFFD044, false);

        TrackMapSnapshot map = ClientTrackMapCache.current();
        if (map.present()) {
            CircuitMapRenderer.renderLocal(graphics, map, car.getX(), car.getZ(), car.getYRot(), car.getLiveryColors().bodySide(), x + 7, y + 40, panelWidth - 14, 58);
        }
    }

    private static void renderGlobalFlagMarker(GuiGraphics graphics) {
        RaceFlagMode flag = RaceFlagClient.getGlobalFlag();
        if (flag == RaceFlagMode.GREEN) {
            return;
        }
        int x = (graphics.guiWidth() - 30) / 2;
        int y = 51;
        int color = flagColor(flag);
        graphics.fill(x + 5, y + 4, x + 22, y + 11, color);
        graphics.fill(x + 22, y + 4, x + 24, y + 15, 0xFFE8E8E8);
        if (flag == RaceFlagMode.SAFETY_CAR || flag == RaceFlagMode.VIRTUAL_SAFETY_CAR) {
            graphics.fill(x + 9, y + 6, x + 18, y + 9, 0xFF1F2328);
        }
    }

    private static void renderLapDeltaBar(GuiGraphics graphics, Font font) {
        int count = LapDeltaClient.segmentCount();
        if (count <= 0) {
            return;
        }
        int width = Math.min(270, graphics.guiWidth() - 340);
        if (width < 90) {
            width = Math.min(190, graphics.guiWidth() - 48);
        }
        int x = (graphics.guiWidth() - width) / 2;
        int y = 9;
        int gap = 2;
        int segmentWidth = Math.max(4, (width - gap * (count - 1)) / count);
        int actualWidth = segmentWidth * count + gap * (count - 1);
        List<Integer> statuses = LapDeltaClient.statuses();
        for (int index = 0; index < count; index++) {
            int sx = x + index * (segmentWidth + gap);
            int color = splitStatusColor(index < statuses.size() ? statuses.get(index) : LapDeltaClient.STATUS_UNREACHED);
            graphics.fill(sx, y, sx + segmentWidth, y + 9, color);
            graphics.fill(sx, y + 9, sx + segmentWidth, y + 10, 0x66000000);
        }
        int last = LapDeltaClient.lastSegmentIndex();
        if (last >= 0) {
            String total = formatSignedTicks(LapDeltaClient.cumulativeDeltaMillis());
            String mini = formatSignedTicks(LapDeltaClient.miniDeltaMillis());
            int totalColor = deltaColor(LapDeltaClient.cumulativeDeltaMillis());
            int miniColor = deltaColor(LapDeltaClient.miniDeltaMillis());
            graphics.drawString(font, total, x + 3, y + 15, totalColor, true);
            graphics.drawString(font, mini, x + actualWidth - 3 - font.width(mini), y + 15, miniColor, false);
        }
        String flash = LapDeltaClient.flashLabel();
        if (!flash.isBlank()) {
            graphics.drawString(font, flash, (graphics.guiWidth() - font.width(flash)) / 2, y + 30, 0xFFFFFFFF, true);
        }
    }

    private static void renderPrimaryHud(GuiGraphics graphics, Font font, OpenwheelCarEntity car) {
        int x = (graphics.guiWidth() - PRIMARY_WIDTH) / 2;
        int y = Math.min(graphics.guiHeight() - PRIMARY_HEIGHT - 34, graphics.guiHeight() / 2 + 34);
        renderShiftLights(graphics, car, x + 48, x + PRIMARY_WIDTH - 48, y + 5, 5);

        int centerX = x + PRIMARY_WIDTH / 2;
        drawScaledText(graphics, font, car.getGearLabel(), centerX, y + 12, 5.0f, 0xFFFFFFFF, true);

        float energyPercent = clamp(car.getErsEnergyPercent(), 0.0f, 100.0f);
        int leftCx = x + 50;
        int leftCy = y + 43;
        drawRadialProgress(graphics, leftCx, leftCy, 28, energyPercent / 100.0f, ersEnergyColor(energyPercent), 0xFF2B2118);
        String ersPercent = String.format("%.0f%%", energyPercent);
        drawScaledText(graphics, font, ersPercent, leftCx, leftCy - 7, 1.55f, 0xFFFFFFFF, true);
        String power = signedPowerLabel(car.getErsPowerKw());
        int powerColor = ersActivityColor(car.getErsActivity());
        graphics.drawString(font, power, leftCx - font.width(power) / 2, y + 63, powerColor, false);
        drawTinyModePip(graphics, x + 8, y + 21, car.getErsMode(), powerColor);

        int rightCx = x + PRIMARY_WIDTH - 50;
        String speed = String.format("%.0f", car.getSpeedKmh());
        int speedColor = car.isDrsActive() ? 0xFF00DD44 : 0xFFFFFFFF;
        drawScaledText(graphics, font, speed, rightCx, y + 24, speed.length() >= 3 ? 2.35f : 2.8f, speedColor, true);
        String kmh = "km/h";
        graphics.drawString(font, kmh, rightCx - font.width(kmh) / 2, y + 51, 0xFF88909A, false);
        String rpm = String.format("%d RPM", car.getRpm());
        graphics.drawString(font, rpm, rightCx - font.width(rpm) / 2, y + 63, rpmColor(car.getRpm()), false);

        if (car.isInPitStop()) {
            renderPitStop(graphics, font, car, x + 36, y - 17, PRIMARY_WIDTH - 72);
        }
    }

    private static void renderPitStop(GuiGraphics graphics, Font font, OpenwheelCarEntity car, int x, int y, int width) {
        int remaining = car.getPitStopTicks();
        int pct = Math.max(0, Math.min(100, 100 - (remaining * 100 / 60)));
        graphics.fill(x + 1, y + 1, x + 1 + (width - 2) * pct / 100, y + 4, 0xFFDA1A20);
        String label = "PIT " + (remaining / 20 + 1) + "s";
        graphics.drawString(font, label, x + (width - font.width(label)) / 2, y + 2, 0xFFFFFFFF, false);
    }

    private static void drawTinyModePip(GuiGraphics graphics, int x, int y, int mode, int color) {
        int filled = switch (mode) {
            case OpenwheelCarEntity.ERS_MODE_HARVEST -> 1;
            case OpenwheelCarEntity.ERS_MODE_ATTACK -> 3;
            default -> 2;
        };
        for (int i = 0; i < 3; i++) {
            int pipColor = i < filled ? color : 0xFF30343A;
            graphics.fill(x + i * 8, y, x + i * 8 + 6, y + 6, pipColor);
        }
    }

    private static void renderShiftLights(GuiGraphics graphics, OpenwheelCarEntity car, int minX, int maxX, int y, int lightSize) {
        int gap = Math.max(1, (maxX - minX - SHIFT_LIGHT_COUNT * lightSize) / (SHIFT_LIGHT_COUNT - 1));
        int totalWidth = SHIFT_LIGHT_COUNT * lightSize + (SHIFT_LIGHT_COUNT - 1) * gap;
        int x = (minX + maxX - totalWidth) / 2;
        int lit = shiftLightCount(car.getRpm(), WheelInputSettings.get());
        for (int i = 0; i < SHIFT_LIGHT_COUNT; i++) {
            int color = i < lit ? shiftLightColor(i) : 0xFF242424;
            int lightX = x + i * (lightSize + gap);
            drawShiftDot(graphics, lightX, y, lightSize, color, i < lit);
        }
    }

    private static void drawShiftDot(GuiGraphics graphics, int x, int y, int size, int color, boolean lit) {
        graphics.fill(x + 1, y, x + size - 1, y + 1, 0xFF050505);
        graphics.fill(x, y + 1, x + size, y + size - 1, 0xFF050505);
        graphics.fill(x + 1, y + size - 1, x + size - 1, y + size, 0xFF050505);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, color);
        if (lit) {
            graphics.fill(x + 1, y + 1, x + size - 1, y + 2, 0x88FFFFFF);
        }
    }

    private static void drawRadialProgress(GuiGraphics graphics, int cx, int cy, int radius, float progress, int fillColor, int emptyColor) {
        int inner = radius - 4;
        int segments = 40;
        for (int i = 0; i < segments; i++) {
            double start = -Math.PI / 2.0 + Math.PI * 2.0 * i / segments;
            double end = -Math.PI / 2.0 + Math.PI * 2.0 * (i + 0.72) / segments;
            int color = i < Math.round(progress * segments) ? fillColor : emptyColor;
            drawRadialSegment(graphics, cx, cy, inner, radius, start, end, color);
        }
    }

    private static void drawRadialSegment(GuiGraphics graphics, int cx, int cy, int inner, int outer, double start, double end, int color) {
        int steps = 3;
        for (int i = 0; i <= steps; i++) {
            double angle = start + (end - start) * i / steps;
            int x0 = cx + (int) Math.round(Math.cos(angle) * inner);
            int y0 = cy + (int) Math.round(Math.sin(angle) * inner);
            int x1 = cx + (int) Math.round(Math.cos(angle) * outer);
            int y1 = cy + (int) Math.round(Math.sin(angle) * outer);
            drawLine(graphics, x0, y0, x1, y1, color);
        }
    }

    private static void drawScaledText(GuiGraphics graphics, Font font, String text, int centerX, int y, float scale, int color, boolean shadow) {
        float scaledWidth = font.width(text) * scale;
        float drawX = (centerX - scaledWidth / 2.0f) / scale;
        float drawY = y / scale;
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        graphics.drawString(font, text, Math.round(drawX), Math.round(drawY), color, shadow);
        graphics.pose().popMatrix();
    }

    private static void renderCarStatus(GuiGraphics graphics, Font font, OpenwheelCarEntity car) {
        int width = 146;
        int height = 96;
        int x = graphics.guiWidth() - width - 8;
        int y = graphics.guiHeight() - height - 8;

        float min = car.getTyreWorkingTemperatureMinCelsius();
        float max = car.getTyreWorkingTemperatureMaxCelsius();
        drawTyreStatus(graphics, x + 22, y + 18, car.getTyreTemperatureFlCelsius(), min, max, car.getTyreWearPercent());
        drawTyreStatus(graphics, x + 76, y + 18, car.getTyreTemperatureFrCelsius(), min, max, car.getTyreWearPercent());
        drawTyreStatus(graphics, x + 22, y + 51, car.getTyreTemperatureRlCelsius(), min, max, car.getTyreWearPercent());
        drawTyreStatus(graphics, x + 76, y + 51, car.getTyreTemperatureRrCelsius(), min, max, car.getTyreWearPercent());

        int damageColor = damageColor(car.getDamagePercent());
        drawVerticalMeter(graphics, x + 121, y + 18, 10, 50, car.getDamagePercent() / 100.0f, damageColor, 0xFF251B1B);
        graphics.drawString(font, "DMG", x + 110, y + 73, damageColor, false);

        drawStatusChip(graphics, font, "ABS", x + 9, y + 80, car.isAbsEnabled() ? 0xFF7EE787 : 0xFFFFD044);
        drawStatusChip(graphics, font, "TC", x + 45, y + 80, car.isTractionControlEnabled() ? 0xFF7EE787 : 0xFFFFD044);
        drawStatusChip(graphics, font, "DRS", x + 80, y + 80, car.isDrsActive() ? 0xFF00DD44 : 0xFF555B63);
    }

    private static void drawTyreStatus(GuiGraphics graphics, int x, int y, float temperature, float min, float max, float wearPercent) {
        int tempColor = tyreTemperatureColor(temperature, min, max);
        int wearFill = Math.max(2, Math.min(20, Math.round(20.0f * Math.max(0.0f, 100.0f - wearPercent) / 100.0f)));
        graphics.fill(x, y, x + 24, y + 10, 0xFF111418);
        graphics.fill(x + 2, y + 2, x + 2 + wearFill, y + 8, 0xFF3A414A);
        graphics.renderOutline(x, y, 24, 10, tempColor);
        graphics.fill(x + 4, y - 4, x + 20, y - 2, tempColor);
        graphics.fill(x + 4, y + 12, x + 20, y + 14, tempColor);
    }

    private static void drawStatusChip(GuiGraphics graphics, Font font, String label, int x, int y, int color) {
        graphics.drawString(font, label, x + (28 - font.width(label)) / 2, y + 2, color, false);
    }

    private static void drawVerticalMeter(GuiGraphics graphics, int x, int y, int width, int height, float progress, int fillColor, int emptyColor) {
        progress = clamp(progress, 0.0f, 1.0f);
        int filled = Math.round((height - 2) * progress);
        graphics.fill(x, y, x + width, y + height, emptyColor);
        graphics.fill(x + 1, y + height - 1 - filled, x + width - 1, y + height - 1, fillColor);
        graphics.renderOutline(x, y, width, height, fillColor);
    }

    private static int splitStatusColor(int status) {
        return switch (status) {
            case LapDeltaClient.STATUS_SESSION_BEST -> 0xFFD65CFF;
            case LapDeltaClient.STATUS_PERSONAL_BEST -> 0xFF34D058;
            case LapDeltaClient.STATUS_SLOWER -> 0xFFFFD044;
            default -> 0xFF333333;
        };
    }

    private static int deltaColor(int ticks) {
        if (ticks < 0) {
            return 0xFF34D058;
        }
        if (ticks > 0) {
            return 0xFFFFD044;
        }
        return 0xFFE8E8E8;
    }

    private static String formatSignedTicks(int ticks) {
        if (ticks == 0) {
            return "+0.00";
        }
        String sign = ticks > 0 ? "+" : "-";
        return sign + formatGap(Math.abs(ticks));
    }

    private static int flagColor(RaceFlagMode flag) {
        return switch (flag) {
            case GREEN -> 0xFF34D058;
            case YELLOW, VIRTUAL_SAFETY_CAR -> 0xFFFFD044;
            case RED -> 0xFFDA1A20;
            case SAFETY_CAR -> 0xFF79C0FF;
        };
    }

    private static int shiftLightCount(int rpm, WheelInputSettings settings) {
        int startRpm = settings.shiftLightStartRpm;
        int fullRpm = Math.max(startRpm + 500, settings.shiftLightFullRpm);
        if (rpm <= startRpm) {
            return 0;
        }
        return Math.max(0, Math.min(SHIFT_LIGHT_COUNT, 1 + (rpm - startRpm) * SHIFT_LIGHT_COUNT / (fullRpm - startRpm)));
    }

    private static int shiftLightColor(int index) {
        if (index < SHIFT_LIGHT_GROUP_SIZE) {
            return 0xFF34D058;
        }
        if (index < SHIFT_LIGHT_GROUP_SIZE * 2) {
            return 0xFFFF2D2D;
        }
        return 0xFFD65CFF;
    }

    private static int ersEnergyColor(float energyPercent) {
        if (energyPercent <= 25.0f) {
            return 0xFFDA1A20;
        }
        if (energyPercent <= 65.0f) {
            return 0xFFFFD044;
        }
        return 0xFF34D058;
    }

    private static String signedPowerLabel(float powerKw) {
        if (Math.abs(powerKw) < 0.5f) {
            return "0 kW";
        }
        return (powerKw > 0.0f ? "+" : "-") + String.format("%.0f kW", Math.abs(powerKw));
    }

    private static int ersActivityColor(int activity) {
        return switch (activity) {
            case OpenwheelCarEntity.ERS_ACTIVITY_HARVESTING -> 0xFF34D058;
            case OpenwheelCarEntity.ERS_ACTIVITY_DEPLOYING -> 0xFF79C0FF;
            case OpenwheelCarEntity.ERS_ACTIVITY_NEGATIVE -> 0xFFFFD044;
            default -> 0xFFE8E8E8;
        };
    }

    private static int rpmColor(int rpm) {
        int lit = shiftLightCount(rpm, WheelInputSettings.get());
        if (lit >= SHIFT_LIGHT_COUNT - 2) {
            return 0xFFD65CFF;
        }
        if (lit >= SHIFT_LIGHT_GROUP_SIZE * 2) {
            return 0xFFFF7777;
        }
        if (lit >= SHIFT_LIGHT_GROUP_SIZE) {
            return 0xFFFFD044;
        }
        return 0xFF7EE787;
    }

    private static int damageColor(float damagePercent) {
        if (damagePercent > 70.0f) {
            return 0xFFFF7777;
        }
        if (damagePercent > 35.0f) {
            return 0xFFFFD044;
        }
        return 0xFF7EE787;
    }

    private static void renderRankingBoard(GuiGraphics graphics, Font font) {
        List<OWRLapRecords.DriverBest> ranking = LapRankingClient.getRanking();
        String sessionName = LapRankingClient.getSessionName();
        int rowCount = Math.min(8, ranking.size());
        int headerHeight = 19;
        int rowHeight = 10;
        int panelWidth = 154;
        int panelHeight = headerHeight + rowHeight * Math.max(1, rowCount) + 4;
        int px = 8;
        int py = 8;

        graphics.drawString(font, fit(font, sessionName, 88), px + 6, py + 3, 0xFFE8E8E8, false);
        graphics.drawString(font, "LIVE", px + panelWidth - 6 - font.width("LIVE"), py + 3, 0xFF7EE787, false);

        if (ranking.isEmpty()) {
            graphics.drawString(font, "No laps yet", px + 6, py + headerHeight + 1, 0xFF666666, false);
            return;
        }
        int firstMillis = ranking.get(0).millis();
        for (int i = 0; i < rowCount; i++) {
            OWRLapRecords.DriverBest entry = ranking.get(i);
            int ry = py + headerHeight + i * rowHeight;
            int nameColor = i == 0 ? 0xFFFFDD44 : 0xFFCCCCCC;
            String pos = (i + 1) + ".";
            String name = entry.name().length() > 9 ? entry.name().substring(0, 9) : entry.name();
            String gap = i == 0 ? formatLapTime(entry.millis()) : "+" + formatGap(entry.millis() - firstMillis);
            graphics.drawString(font, pos, px + 5, ry, 0xFF88909A, false);
            graphics.drawString(font, name, px + 19, ry, nameColor, false);
            graphics.drawString(font, gap, px + panelWidth - 6 - font.width(gap), ry, nameColor, false);
        }
    }

    private static String formatGap(int millis) {
        int s = millis / 1000;
        int frac = millis % 1000;
        return s + "." + String.format("%03d", frac);
    }

    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }

    private static void renderPhysicsDebug(GuiGraphics graphics, Font font, OpenwheelCarEntity car) {
        int x = 8;
        int y = graphics.guiHeight() - 94;
        int width = 170;
        drawHorizontalMeter(graphics, x + 8, y + 13, 66, normalizedAbs(car.getDebugVelocityLat(), 1.4), signedDemandColor(car.getDebugVelocityLat()));
        drawHorizontalMeter(graphics, x + 96, y + 13, 66, normalizedAbs(car.getDebugYawRate(), 0.55), signedDemandColor(car.getDebugYawRate()));
        graphics.drawString(font, "LAT", x + 8, y + 3, 0xFF99DDFF, false);
        graphics.drawString(font, "YAW", x + 96, y + 3, 0xFF99DDFF, false);

        drawCornerDemand(graphics, x + 28, y + 38, car.getDebugFlDemand(), car.getDebugFlSlipAngleDegrees());
        drawCornerDemand(graphics, x + 116, y + 38, car.getDebugFrDemand(), car.getDebugFrSlipAngleDegrees());
        drawCornerDemand(graphics, x + 28, y + 67, car.getDebugRlDemand(), car.getDebugRlSlipAngleDegrees());
        drawCornerDemand(graphics, x + 116, y + 67, car.getDebugRrDemand(), car.getDebugRrSlipAngleDegrees());
        graphics.drawString(font, String.format("%.0fkm/h", car.getSpeedKmh()), x + 63, y + 45, 0xFFE8E8E8, false);
        graphics.drawString(font, String.format("%.1f°", car.getFrontWheelSteerDegrees()), x + 68, y + 56, 0xFFFFDD88, false);
    }

    private static void drawHorizontalMeter(GuiGraphics graphics, int x, int y, int width, float progress, int color) {
        int fill = Math.round(width * clamp(progress, 0.0f, 1.0f));
        graphics.fill(x, y, x + width, y + 5, 0xFF1B2026);
        graphics.fill(x, y, x + fill, y + 5, color);
    }

    private static void drawCornerDemand(GuiGraphics graphics, int cx, int cy, double demand, double slipDegrees) {
        int radius = 8;
        int color = demandColor(demand);
        graphics.fill(cx - radius, cy - radius, cx + radius, cy + radius, 0xFF101418);
        int fill = Math.max(1, Math.min(radius, (int) Math.round(radius * Math.min(1.4, Math.abs(demand)) / 1.4)));
        graphics.fill(cx - fill, cy - fill, cx + fill, cy + fill, color);
        graphics.renderOutline(cx - radius, cy - radius, radius * 2, radius * 2, slipDegrees > 12.0 ? 0xFFFF7777 : 0xFF3A414A);
    }

    private static float normalizedAbs(double value, double max) {
        return (float) Math.min(1.0, Math.abs(value) / max);
    }

    private static int signedDemandColor(double value) {
        double abs = Math.abs(value);
        if (abs > 1.0) {
            return 0xFFFF7777;
        }
        if (abs > 0.55) {
            return 0xFFFFD044;
        }
        return 0xFF7EE787;
    }

    private static int completedLapColor(int result) {
        return switch (result) {
            case OpenwheelCarEntity.LAP_RESULT_OVERALL_BEST -> 0xFFFF55FF;
            case OpenwheelCarEntity.LAP_RESULT_PERSONAL_BEST -> 0xFF55FF55;
            case OpenwheelCarEntity.LAP_RESULT_SLOWER -> 0xFFFFDD66;
            default -> 0xFFFFFFFF;
        };
    }

    private static int demandColor(double demand) {
        if (demand > 1.25) {
            return 0xFFFF7777;
        }
        if (demand > 1.0) {
            return 0xFFFFD044;
        }
        return 0xFFB7FFB7;
    }

    private static int tyreTemperatureColor(OpenwheelCarEntity car, float temperature) {
        return tyreTemperatureColor(temperature, car.getTyreWorkingTemperatureMinCelsius(), car.getTyreWorkingTemperatureMaxCelsius());
    }

    private static int tyreTemperatureColor(float temperature, float min, float max) {
        if (temperature < min - 10.0f) {
            return 0xFF66CCFF;
        }
        if (temperature < min) {
            return 0xFF99DDFF;
        }
        if (temperature <= max) {
            return 0xFFB7FFB7;
        }
        if (temperature <= max + 10.0f) {
            return 0xFFFFDD66;
        }
        return 0xFFFF7777;
    }

    private static double maxDemand(OpenwheelCarEntity car) {
        return Math.max(
            Math.max(car.getDebugFlDemand(), car.getDebugFrDemand()),
            Math.max(car.getDebugRlDemand(), car.getDebugRrDemand())
        );
    }

    private static float displayedTyreTemperature(OpenwheelCarEntity car) {
        float target = car.getTyreTemperatureCelsius();
        if (lastTemperatureCarId != car.getId() || Float.isNaN(displayedTyreTemperatureC)) {
            lastTemperatureCarId = car.getId();
            displayedTyreTemperatureC = target;
        } else {
            displayedTyreTemperatureC += (target - displayedTyreTemperatureC) * 0.35f;
        }
        return displayedTyreTemperatureC;
    }

    private static String formatLapTime(int millis) {
        if (millis <= 0) {
            return "--:--.---";
        }
        int minutes = millis / 60000;
        int seconds = millis / 1000 % 60;
        int milliseconds = millis % 1000;
        return String.format("%d:%02d.%03d", minutes, seconds, milliseconds);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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
}
