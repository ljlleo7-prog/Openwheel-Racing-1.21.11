package com.openwheelracing.client.hud;

import com.openwheelracing.client.input.WheelInputSettings;
import com.openwheelracing.client.map.CircuitMapRenderer;
import com.openwheelracing.client.map.ClientTrackMapCache;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.race.OWRLapRecords;
import com.openwheelracing.content.race.RaceFlagMode;
import com.openwheelracing.content.race.timing.RaceGap;
import com.openwheelracing.content.race.timing.RaceProgressConfidence;
import com.openwheelracing.content.race.timing.RaceTimingRow;
import com.openwheelracing.content.track.TrackMapSnapshot;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

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
            if (LiveRaceTimingClient.active()) {
                renderRaceTimingTower(graphics, font, minecraft.player.getUUID());
            } else {
                renderRankingBoard(graphics, font);
            }
        }
    }

    private static void renderLiveTiming(GuiGraphics graphics, Font font, OpenwheelCarEntity car) {
        int panelWidth = 178;
        int panelHeight = 76;
        int x = graphics.guiWidth() - panelWidth - 8;
        int y = 8;

        int displayedLapTicks = LiveLapDeltaClient.active() ? LiveLapDeltaClient.elapsedMillis() : car.getCompletedLapLingerTicks() > 0 ? car.getCompletedLapTicks() : car.getCurrentLapTicks();
        int lapColor = car.getCompletedLapLingerTicks() > 0 ? completedLapColor(car.getCompletedLapResult()) : 0xFFFFFFFF;
        String lap = formatLapTime(displayedLapTicks);
        int bestMillis = LiveLapDeltaClient.bestLapMillis() > 0 ? LiveLapDeltaClient.bestLapMillis() : car.getBestLapTicks();
        String best = formatLapTime(bestMillis);
        String delta = "Delta  " + liveDeltaText();
        String checkpointProgress = LapDeltaClient.segmentCount() > 0 ? LapDeltaClient.hitCount() + "/" + LapDeltaClient.segmentCount() : (car.hasCheckpoint() ? "1/1" : "0/1");

        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xB8142638);
        graphics.fill(x, y, x + panelWidth, y + 1, 0xAA55718B);
        graphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, 0xAA09131E);
        int checkpointColor = LapDeltaClient.hitCount() > 0 || car.hasCheckpoint() ? 0xFFE8EDF2 : 0xFFFFD76A;
        drawScaledText(graphics, font, "LAP  " + lap, x + panelWidth / 2, y + 6, 1.22f, lapColor, true);
        drawScaledText(graphics, font, "BEST  " + best + "    CP  " + checkpointProgress, x + panelWidth / 2, y + 19, 0.95f, checkpointColor, true);

        int deltaTop = y + 34;
        int deltaBottom = y + 70;
        graphics.fill(x + 5, deltaTop, x + panelWidth - 5, deltaBottom, liveDeltaColor());
        float deltaScale = delta.length() > 18 ? 1.35f : 1.65f;
        drawScaledText(graphics, font, delta, x + panelWidth / 2, deltaTop + 10, deltaScale, 0xFFFFFFFF, true);

        TrackMapSnapshot map = ClientTrackMapCache.current();
        if (map.present()) {
            CircuitMapRenderer.renderLocal(graphics, map, car.getX(), car.getZ(), car.getYRot(), car.getLiveryColors().bodySide(), x + 7, y + panelHeight + 6, panelWidth - 14, 58);
        }
    }

    private static String liveDeltaText() {
        if (!LiveLapDeltaClient.active()) return "--";
        if (!LiveLapDeltaClient.hasReference()) return "NO REF";
        return switch (LiveLapDeltaClient.status()) {
            case AMBIGUOUS -> "AMBIG";
            case UNTRACKED -> "UNTRACKED";
            case LOW_CONFIDENCE, TRACKED -> formatSignedTicks(LiveLapDeltaClient.deltaMillis());
        };
    }

    private static int liveDeltaColor() {
        if (!LiveLapDeltaClient.active() || !LiveLapDeltaClient.hasReference()) return 0xE044586B;
        return switch (LiveLapDeltaClient.status()) {
            case AMBIGUOUS -> 0xE06A3D8F;
            case UNTRACKED -> 0xE08F2D2D;
            case LOW_CONFIDENCE -> 0xE08A5A00;
            case TRACKED -> LiveLapDeltaClient.deltaMillis() <= 0 ? 0xE0176B4A : 0xE08F2D2D;
        };
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
        int height = 184;
        int x = graphics.guiWidth() - width - 8;
        int y = graphics.guiHeight() - height - 8;

        renderComponentDamageDiagram(graphics, car, x + 9, y + 5);

        int tyreY = y + 88;
        float min = car.getTyreWorkingTemperatureMinCelsius();
        float max = car.getTyreWorkingTemperatureMaxCelsius();
        drawTyreStatus(graphics, font, x + 22, tyreY + 18, car.getTyreTemperatureFlCelsius(), car.getTyreCarcassTemperatureFlCelsius(), min, max, car.getTyreWearFlPercent());
        drawTyreStatus(graphics, font, x + 76, tyreY + 18, car.getTyreTemperatureFrCelsius(), car.getTyreCarcassTemperatureFrCelsius(), min, max, car.getTyreWearFrPercent());
        drawTyreStatus(graphics, font, x + 22, tyreY + 51, car.getTyreTemperatureRlCelsius(), car.getTyreCarcassTemperatureRlCelsius(), min, max, car.getTyreWearRlPercent());
        drawTyreStatus(graphics, font, x + 76, tyreY + 51, car.getTyreTemperatureRrCelsius(), car.getTyreCarcassTemperatureRrCelsius(), min, max, car.getTyreWearRrPercent());

        int damageColor = damageColor(car.getDamagePercent());
        drawVerticalMeter(graphics, x + 121, tyreY + 18, 10, 50, car.getDamagePercent() / 100.0f, damageColor, 0xFF251B1B);
        graphics.drawString(font, "DMG", x + 110, tyreY + 73, damageColor, false);

        drawStatusChip(graphics, font, "ABS", x + 9, y + 168, car.isAbsEnabled() ? 0xFF7EE787 : 0xFFFFD044);
        drawStatusChip(graphics, font, "TC", x + 45, y + 168, car.isTractionControlEnabled() ? 0xFF7EE787 : 0xFFFFD044);
        drawStatusChip(graphics, font, "DRS", x + 80, y + 168, car.isDrsActive() ? 0xFF00DD44 : 0xFF555B63);
    }

    private static void renderComponentDamageDiagram(GuiGraphics graphics, OpenwheelCarEntity car, int x, int y) {
        int cx = x + 64;
        int top = y;
        int frontColor = damageColor(car.getFrontEndDamagePercent());
        int rearColor = damageColor(car.getRearEndDamagePercent());
        int chassisColor = damageColor(car.getChassisDamagePercent());
        int engineColor = damageColor(car.getEngineDamagePercent());
        int flColor = damageColor(car.getFrontLeftSuspensionDamagePercent());
        int frColor = damageColor(car.getFrontRightSuspensionDamagePercent());
        int rlColor = damageColor(car.getRearLeftSuspensionDamagePercent());
        int rrColor = damageColor(car.getRearRightSuspensionDamagePercent());
        graphics.fill(cx - 30, top, cx + 30, top + 4, frontColor);
        graphics.fill(cx - 5, top + 4, cx + 5, top + 13, frontColor);
        graphics.fill(cx - 20, top + 22, cx - 7, top + 25, flColor);
        graphics.fill(cx + 7, top + 22, cx + 20, top + 25, frColor);
        drawSuspensionWheel(graphics, cx - 34, top + 12, flColor);
        drawSuspensionWheel(graphics, cx + 22, top + 12, frColor);
        graphics.fill(cx - 6, top + 13, cx + 6, top + 28, chassisColor);
        graphics.fill(cx - 8, top + 28, cx + 8, top + 42, chassisColor);
        graphics.fill(cx - 20, top + 42, cx + 20, top + 62, chassisColor);
        graphics.fill(cx - 6, top + 47, cx + 6, top + 59, 0xFF050608);
        graphics.fill(cx - 4, top + 49, cx + 4, top + 57, engineColor);
        graphics.fill(cx - 15, top + 62, cx + 15, top + 73, chassisColor);
        graphics.fill(cx - 10, top + 73, cx + 10, top + 79, chassisColor);
        graphics.fill(cx - 20, top + 68, cx - 7, top + 71, rlColor);
        graphics.fill(cx + 7, top + 68, cx + 20, top + 71, rrColor);
        drawSuspensionWheel(graphics, cx - 34, top + 58, rlColor);
        drawSuspensionWheel(graphics, cx + 22, top + 58, rrColor);
        graphics.fill(cx - 5, top + 79, cx + 5, top + 84, rearColor);
        graphics.fill(cx - 28, top + 84, cx + 28, top + 88, rearColor);
    }

    private static void drawSuspensionWheel(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 12, y + 19, 0xFF050608);
        graphics.fill(x + 2, y + 2, x + 10, y + 17, color);
    }

    private static void drawTyreStatus(GuiGraphics graphics, Font font, int x, int y, float surfaceTemperature, float carcassTemperature, float min, float max, float wearPercent) {
        int surfaceColor = tyreTemperatureColor(surfaceTemperature, min, max);
        int carcassColor = tyreTemperatureColor(carcassTemperature, min, max);
        int wearColor = tyreWearColor(wearPercent);
        String wear = Math.round(clamp(wearPercent, 0.0f, 100.0f)) + "%";
        graphics.fill(x, y, x + 24, y + 10, 0xFF050608);
        graphics.drawString(font, wear, x + (24 - font.width(wear)) / 2, y + 1, wearColor, false);
        graphics.fill(x + 4, y - 4, x + 20, y - 2, surfaceColor);
        graphics.fill(x + 4, y + 12, x + 20, y + 14, carcassColor);
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
        float damage = clamp(damagePercent, 0.0f, 100.0f);
        if (damage <= 50.0f) {
            return interpolateColor(0xFF7EE787, 0xFFFFD044, damage / 50.0f);
        }
        return interpolateColor(0xFFFFD044, 0xFFFF7777, (damage - 50.0f) / 50.0f);
    }

    private static void renderRaceTimingTower(GuiGraphics graphics, Font font, java.util.UUID localParticipantId) {
        List<RaceTimingRow> allRows = LiveRaceTimingClient.rows();
        int localIndex = -1;
        for (int index = 0; index < allRows.size(); index++) {
            if (allRows.get(index).participant().id().equals(localParticipantId)) {
                localIndex = index;
                break;
            }
        }
        int start = localIndex < 0 ? 0 : Math.max(0, Math.min(localIndex - 3, allRows.size() - 8));
        List<RaceTimingRow> rows = allRows.stream().skip(start).limit(8).toList();
        int panelWidth = 166;
        int headerHeight = 19;
        int rowHeight = 10;
        int panelHeight = headerHeight + Math.max(1, rows.size()) * rowHeight + 4;
        int px = 8;
        int py = 8;
        graphics.fill(px, py, px + panelWidth, py + panelHeight, 0xB8142638);
        graphics.fill(px, py, px + panelWidth, py + 1, 0xAA55718B);
        String session = fit(font, LiveRaceTimingClient.snapshot().sessionName(), 112);
        graphics.drawString(font, session, px + 6, py + 3, 0xFFE8E8E8, false);
        String raceProgress = raceProgressLabel(allRows);
        graphics.drawString(font, raceProgress, px + panelWidth - 6 - font.width(raceProgress), py + 3, 0xFF7EE787, false);
        if (rows.isEmpty()) {
            graphics.drawString(font, Component.translatable("hud.openwheelracing.race.no_cars").getString(), px + 6, py + headerHeight + 1, 0xFF777777, false);
            return;
        }
        for (int index = 0; index < rows.size(); index++) {
            RaceTimingRow row = rows.get(index);
            int y = py + headerHeight + index * rowHeight;
            boolean local = row.participant().id().equals(localParticipantId);
            int color = local ? 0xFFFFDD44 : confidenceColor(row.confidence());
            String position = Integer.toString(row.position());
            String marker = confidenceMarker(row.confidence());
            String name = fit(font, row.displayName(), 72);
            String gap = formatRaceGap(row.gapToLeader());
            graphics.drawString(font, position, px + 5, y, color, false);
            graphics.drawString(font, marker + name, px + 20, y, color, false);
            graphics.drawString(font, gap, px + panelWidth - 6 - font.width(gap), y, color, false);
        }
    }

    private static String raceProgressLabel(List<RaceTimingRow> rows) {
        var snapshot = LiveRaceTimingClient.snapshot();
        if (snapshot.lapLimit() > 0) {
            int leaderLap = rows.isEmpty() ? 1 : rows.getFirst().completedLaps() + 1;
            return "LAP " + Math.min(leaderLap, snapshot.lapLimit()) + "/" + snapshot.lapLimit();
        }
        if (snapshot.remainingRaceTicks() >= 0L) {
            long totalSeconds = (snapshot.remainingRaceTicks() + 19L) / 20L;
            long minutes = totalSeconds / 60L;
            long seconds = totalSeconds % 60L;
            return String.format("%d:%02d", minutes, seconds);
        }
        int leaderLap = rows.isEmpty() ? 1 : rows.getFirst().completedLaps() + 1;
        return "LAP " + leaderLap;
    }

    private static String formatRaceGap(RaceGap gap) {
        return switch (gap.type()) {
            case LEADER -> Component.translatable("hud.openwheelracing.race.leader").getString();
            case LAPS -> "+" + gap.laps() + " " + Component.translatable(gap.laps() == 1 ? "hud.openwheelracing.race.lap" : "hud.openwheelracing.race.laps").getString();
            case UNAVAILABLE -> "--.---";
            case TIME_MILLIS -> "+" + formatGap((int) Math.min(Integer.MAX_VALUE, gap.millis()));
        };
    }

    private static String confidenceMarker(RaceProgressConfidence confidence) {
        return switch (confidence) {
            case CONFIRMED -> "";
            case DEGRADED -> "~";
            case AMBIGUOUS -> "?";
            case UNTRACKED, STALE -> "!";
        };
    }

    private static int confidenceColor(RaceProgressConfidence confidence) {
        return switch (confidence) {
            case CONFIRMED -> 0xFFCCCCCC;
            case DEGRADED -> 0xFFFFD76A;
            case AMBIGUOUS -> 0xFFD89BFF;
            case UNTRACKED, STALE -> 0xFFFF7777;
        };
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
        String bestLabel = Component.translatable("hud.openwheelracing.best").getString();
        graphics.drawString(font, bestLabel, px + panelWidth - 6 - font.width(bestLabel), py + 3, 0xFF79C0FF, false);

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
        float coldStart = min - 18.0f;
        float hotEnd = max + 18.0f;
        int coolOptimalColor = 0xFF72DFA0;
        int centerOptimalColor = 0xFF69E27D;
        int warmOptimalColor = 0xFFA8E36E;
        if (temperature < min) {
            return interpolateColor(0xFF66CCFF, coolOptimalColor,
                clamp((temperature - coldStart) / Math.max(1.0f, min - coldStart), 0.0f, 1.0f));
        }
        if (temperature <= max) {
            float optimal = clamp((temperature - min) / Math.max(1.0f, max - min), 0.0f, 1.0f);
            if (optimal < 0.5f) {
                return interpolateColor(coolOptimalColor, centerOptimalColor, optimal * 2.0f);
            }
            return interpolateColor(centerOptimalColor, warmOptimalColor, (optimal - 0.5f) * 2.0f);
        }
        float hot = clamp((temperature - max) / Math.max(1.0f, hotEnd - max), 0.0f, 1.0f);
        if (hot < 0.5f) {
            return interpolateColor(warmOptimalColor, 0xFFFFD044, hot * 2.0f);
        }
        return interpolateColor(0xFFFFD044, 0xFFFF5555, (hot - 0.5f) * 2.0f);
    }

    private static int tyreWearColor(float wearPercent) {
        float wear = clamp(wearPercent, 0.0f, 100.0f);
        if (wear <= 55.0f) {
            return interpolateColor(0xFFB7FFB7, 0xFFFFD044, wear / 55.0f);
        }
        return interpolateColor(0xFFFFD044, 0xFFFF5555, (wear - 55.0f) / 45.0f);
    }

    private static int interpolateColor(int from, int to, float t) {
        t = clamp(t, 0.0f, 1.0f);
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return a << 24 | r << 16 | g << 8 | b;
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
