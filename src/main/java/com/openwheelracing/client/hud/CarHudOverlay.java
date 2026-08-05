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
    private static final int PANEL_WIDTH = 142;
    private static final int PANEL_HEIGHT = 100;
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
        int x = graphics.guiWidth() - PANEL_WIDTH - 8;
        int y = graphics.guiHeight() - PANEL_HEIGHT - 8;

        renderMinimap(graphics, car);

        if (settings.showDrivingHud) {
            renderErsMeter(graphics, font, car);
            renderGlobalFlagMarker(graphics);
            renderLapDeltaBar(graphics, font);

            int outlineColor = car.isDrsActive() ? 0xFF00DD44 : 0xFFDA1A20;
            graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0x99000000);
            graphics.renderOutline(x, y, PANEL_WIDTH, PANEL_HEIGHT, outlineColor);
            graphics.drawString(font, String.format("SPD %3.0f km/h", car.getSpeedKmh()), x + 8, y + 7, 0xFFFFFFFF, false);
            graphics.drawString(font, "GEAR " + car.getGearLabel(), x + 8, y + 18, 0xFFFFFFFF, false);
            graphics.drawString(font, "RPM  " + car.getRpm(), x + 8, y + 29, 0xFFFFFFFF, false);
            graphics.drawString(font, String.format("TYRE %3.0f%%", Math.max(0.0f, 100.0f - car.getTyreWearPercent())), x + 8, y + 40, car.getTyreWearPercent() > 70.0f ? 0xFFFFDD66 : 0xFFB7FFB7, false);
            graphics.drawString(font, String.format("DMG %3.0f%%", car.getDamagePercent()), x + 68, y + 40, car.getDamagePercent() > 70.0f ? 0xFFFF7777 : 0xFFFFFFFF, false);
            graphics.drawString(font, "ABS " + (car.isAbsEnabled() ? "ON" : "OFF"), x + 68, y + 51, car.isAbsEnabled() ? 0xFFB7FFB7 : 0xFFFFDD66, false);
            graphics.drawString(font, "TC " + (car.isTractionControlEnabled() ? "ON" : "OFF"), x + 68, y + 62, car.isTractionControlEnabled() ? 0xFFB7FFB7 : 0xFFFFDD66, false);
            graphics.drawString(font, "DRS " + (car.isDrsActive() ? "OPEN" : "----"), x + 68, y + 73, car.isDrsActive() ? 0xFF00DD44 : 0xFF777777, false);
            int displayedLapTicks = car.getCompletedLapLingerTicks() > 0 ? car.getCompletedLapTicks() : car.getCurrentLapTicks();
            int lapColor = car.getCompletedLapLingerTicks() > 0 ? completedLapColor(car.getCompletedLapResult()) : 0xFFFFFFFF;
            graphics.drawString(font, "LAP  " + formatLapTime(displayedLapTicks), x + 8, y + 51, lapColor, false);
            String checkpointProgress = LapDeltaClient.segmentCount() > 0 ? LapDeltaClient.hitCount() + " / " + LapDeltaClient.segmentCount() : (car.hasCheckpoint() ? "1 / 1" : "0 / 1");
            graphics.drawString(font, "CP   " + checkpointProgress, x + 8, y + 62, LapDeltaClient.hitCount() > 0 || car.hasCheckpoint() ? 0xFFB7FFB7 : 0xFFFFDD66, false);
            graphics.drawString(font, "BEST " + formatLapTime(car.getBestLapTicks()), x + 8, y + 73, 0xFFFFFF99, false);
            float tyreTemperature = displayedTyreTemperature(car);
            String tyreTemp = String.format("TEMP %3.0fC", tyreTemperature);
            graphics.drawString(font, tyreTemp, x + PANEL_WIDTH - 8 - font.width(tyreTemp), y + 86, tyreTemperatureColor(car, tyreTemperature), false);

            if (car.isInPitStop()) {
                int remaining = car.getPitStopTicks();
                int pct = 100 - (remaining * 100 / 60);
                int barWidth = 116;
                int barX = x - 54;
                int barY = y - 20;
                graphics.fill(barX, barY, barX + barWidth, barY + 12, 0x99000000);
                graphics.fill(barX + 1, barY + 1, barX + 1 + barWidth * pct / 100, barY + 11, 0xFFDA1A20);
                graphics.drawString(font, "PIT STOP  " + (remaining / 20 + 1) + "s", barX + 4, barY + 2, 0xFFFFFFFF, false);
            }
        }

        if (settings.showPhysicsDebugHud) {
            renderPhysicsDebug(graphics, font, car);
        }

        if (settings.showRankingHud) {
            renderRankingBoard(graphics, font);
        }
    }

    private static void renderMinimap(GuiGraphics graphics, OpenwheelCarEntity car) {
        TrackMapSnapshot map = ClientTrackMapCache.current();
        if (!map.present()) {
            return;
        }
        int width = 172;
        int height = 112;
        int x = 8;
        int y = graphics.guiHeight() - height - 8;
        CircuitMapRenderer.renderLocal(graphics, map, car.getX(), car.getZ(), car.getYRot(), car.getLiveryColors().bodySide(), x, y, width, height);
    }

    private static void renderGlobalFlagMarker(GuiGraphics graphics) {
        RaceFlagMode flag = RaceFlagClient.getGlobalFlag();
        if (flag == RaceFlagMode.GREEN) {
            return;
        }
        int x = (graphics.guiWidth() - 22) / 2;
        int y = 14;
        int color = flagColor(flag);
        graphics.fill(x, y, x + 22, y + 14, 0xCC000000);
        graphics.fill(x + 3, y + 3, x + 17, y + 11, color);
        graphics.fill(x + 17, y + 3, x + 19, y + 14, 0xFFE8E8E8);
        if (flag == RaceFlagMode.SAFETY_CAR || flag == RaceFlagMode.VIRTUAL_SAFETY_CAR) {
            graphics.fill(x + 6, y + 5, x + 14, y + 9, 0xFF1F2328);
        }
    }

    private static void renderLapDeltaBar(GuiGraphics graphics, Font font) {
        int count = LapDeltaClient.segmentCount();
        if (count <= 0) {
            return;
        }
        int width = Math.min(260, graphics.guiWidth() - 48);
        int x = (graphics.guiWidth() - width) / 2;
        int y = 32;
        int gap = 2;
        int segmentWidth = Math.max(4, (width - gap * (count - 1)) / count);
        List<Integer> statuses = LapDeltaClient.statuses();
        graphics.fill(x - 3, y - 3, x + width + 3, y + 24, 0xAA000000);
        for (int index = 0; index < count; index++) {
            int sx = x + index * (segmentWidth + gap);
            int color = splitStatusColor(index < statuses.size() ? statuses.get(index) : LapDeltaClient.STATUS_UNREACHED);
            graphics.fill(sx, y, sx + segmentWidth, y + 7, color);
            graphics.fill(sx, y + 7, sx + segmentWidth, y + 8, 0x66000000);
        }
        int last = LapDeltaClient.lastSegmentIndex();
        if (last >= 0) {
            String total = formatSignedTicks(LapDeltaClient.cumulativeDeltaMillis());
            String mini = formatSignedTicks(LapDeltaClient.miniDeltaMillis());
            int totalColor = deltaColor(LapDeltaClient.cumulativeDeltaMillis());
            int miniColor = deltaColor(LapDeltaClient.miniDeltaMillis());
            graphics.drawString(font, total, x + 5, y + 12, totalColor, true);
            graphics.drawString(font, mini, x + width - 5 - font.width(mini), y + 13, miniColor, false);
        }
        String flash = LapDeltaClient.flashLabel();
        if (!flash.isBlank()) {
            graphics.drawString(font, flash, (graphics.guiWidth() - font.width(flash)) / 2, y - 13, 0xFFFFFFFF, true);
        }
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

    private static void renderErsMeter(GuiGraphics graphics, Font font, OpenwheelCarEntity car) {
        int width = 182;
        int height = 12;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 43;
        float energyPercent = Math.max(0.0f, Math.min(100.0f, car.getErsEnergyPercent()));
        int fillWidth = Math.round((width - 2) * energyPercent / 100.0f);
        int fillColor = ersEnergyColor(energyPercent);
        renderShiftLights(graphics, car, x, x + width, y - 8);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xCC000000);
        graphics.fill(x, y, x + width, y + height, 0xFF2B2118);
        if (fillWidth > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + height - 1, fillColor);
            graphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + 2, 0x66FFFFFF);
        }
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0x99000000);
        graphics.renderOutline(x - 1, y - 1, width + 2, height + 2, 0xFF3F3F3F);

        String left = "ERS " + car.getErsModeLabel();
        String right = ersActivityLabel(car);
        int labelY = y + 2;
        graphics.drawString(font, left, x + 5, labelY, 0xFFFFFFFF, true);
        graphics.drawString(font, right, x + width - 5 - font.width(right), labelY, 0xFFFFFFFF, true);
    }

    private static void renderShiftLights(GuiGraphics graphics, OpenwheelCarEntity car, int minX, int maxX, int y) {
        int lightSize = 8;
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
        graphics.fill(x + 2, y, x + size - 2, y + 1, 0xFF050505);
        graphics.fill(x + 1, y + 1, x + size - 1, y + 2, 0xFF050505);
        graphics.fill(x, y + 2, x + size, y + size - 2, 0xFF050505);
        graphics.fill(x + 1, y + size - 2, x + size - 1, y + size - 1, 0xFF050505);
        graphics.fill(x + 2, y + size - 1, x + size - 2, y + size, 0xFF050505);
        graphics.fill(x + 2, y + 1, x + size - 2, y + 2, color);
        graphics.fill(x + 1, y + 2, x + size - 1, y + size - 2, color);
        graphics.fill(x + 2, y + size - 2, x + size - 2, y + size - 1, color);
        if (lit) {
            graphics.fill(x + 2, y + 1, x + size - 2, y + 2, 0x88FFFFFF);
        }
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

    private static String ersActivityLabel(OpenwheelCarEntity car) {
        return switch (car.getErsActivity()) {
            case OpenwheelCarEntity.ERS_ACTIVITY_HARVESTING -> String.format("CHG %.0fkW", Math.abs(car.getErsPowerKw()));
            case OpenwheelCarEntity.ERS_ACTIVITY_DEPLOYING -> String.format("DEP %.0fkW", car.getErsPowerKw());
            case OpenwheelCarEntity.ERS_ACTIVITY_NEGATIVE -> String.format("NEG %.0fkW", Math.abs(car.getErsPowerKw()));
            default -> String.format("%3.0f%%", car.getErsEnergyPercent());
        };
    }

    private static int ersActivityColor(int activity) {
        return switch (activity) {
            case OpenwheelCarEntity.ERS_ACTIVITY_HARVESTING -> 0xFF34D058;
            case OpenwheelCarEntity.ERS_ACTIVITY_DEPLOYING -> 0xFF99DDFF;
            case OpenwheelCarEntity.ERS_ACTIVITY_NEGATIVE -> 0xFFFFD044;
            default -> 0xFFE8E8E8;
        };
    }

    private static void renderRankingBoard(GuiGraphics graphics, Font font) {
        List<OWRLapRecords.DriverBest> ranking = LapRankingClient.getRanking();
        String sessionName = LapRankingClient.getSessionName();
        int rowCount = ranking.size();
        int headerHeight = 20;
        int rowHeight = 9;
        int panelWidth = 148;
        int panelHeight = headerHeight + rowHeight * Math.max(1, rowCount) + 3;
        int px = graphics.guiWidth() - panelWidth - 8;
        int py = 8;

        graphics.fill(px, py, px + panelWidth, py + panelHeight, 0xBB000000);
        graphics.renderOutline(px, py, panelWidth, panelHeight, 0xFF444444);
        graphics.drawString(font, fit(font, sessionName, 130), px + 6, py + 2, 0xFFE8E8E8, false);
        graphics.drawString(font, "FASTEST LAPS", px + 6, py + 11, 0xFFAAAAAA, false);

        if (rowCount == 0) {
            graphics.drawString(font, "No laps yet", px + 6, py + headerHeight + 1, 0xFF666666, false);
            return;
        }
        int firstMillis = ranking.get(0).millis();
        for (int i = 0; i < rowCount; i++) {
            OWRLapRecords.DriverBest entry = ranking.get(i);
            int ry = py + headerHeight + i * rowHeight + 1;
            int nameColor = i == 0 ? 0xFFFFDD44 : 0xFFCCCCCC;
            String pos = (i + 1) + ".";
            String name = entry.name().length() > 10 ? entry.name().substring(0, 10) : entry.name();
            String time = formatLapTime(entry.millis());
            String gap = i == 0 ? "" : "+" + formatGap(entry.millis() - firstMillis);
            graphics.drawString(font, pos, px + 4, ry, 0xFF888888, false);
            graphics.drawString(font, name, px + 16, ry, nameColor, false);
            graphics.drawString(font, time, px + 80, ry, nameColor, false);
            if (!gap.isEmpty()) {
                graphics.drawString(font, gap, px + 116, ry, 0xFF888888, false);
            }
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
        int x = 4;
        int y = 4;
        int lineHeight = 8;
        int row = y;
        debugLine(graphics, font, x, row, 0xFF99DDFF, "OWR Phys"); row += lineHeight;
        debugLine(graphics, font, x, row, 0xFFFFFFFF, String.format("spd %.1f rpm %d g%s", car.getSpeedKmh(), car.getRpm(), car.getGearLabel())); row += lineHeight;
        debugLine(graphics, font, x, row, 0xFFFFFFFF, String.format("vL %.2f vY %.2f yaw %.3f", car.getDebugVelocityLong(), car.getDebugVelocityLat(), car.getDebugYawRate())); row += lineHeight;
        debugLine(graphics, font, x, row, 0xFFFFFFFF, String.format("steer %.1f slip %.2f", car.getFrontWheelSteerDegrees(), car.getTyreSlipIntensity())); row += lineHeight;
        debugLine(graphics, font, x, row, 0xFFFFDD88, String.format("drv %.0f drag %.0f df %.0f", car.getDebugDriveForce(), car.getDebugDragForce(), car.getDebugDownforce())); row += lineHeight;
        debugLine(graphics, font, x, row, 0xFFB7FFB7, String.format("Fx  FL %5.0f FR %5.0f", car.getDebugFlLongForce(), car.getDebugFrLongForce())); row += lineHeight;
        debugLine(graphics, font, x, row, 0xFFB7FFB7, String.format("    RL %5.0f RR %5.0f", car.getDebugRlLongForce(), car.getDebugRrLongForce())); row += lineHeight;
        debugLine(graphics, font, x, row, 0xFFB7FFB7, String.format("Fy  FL %5.0f FR %5.0f", car.getDebugFlLatForce(), car.getDebugFrLatForce())); row += lineHeight;
        debugLine(graphics, font, x, row, 0xFFB7FFB7, String.format("    RL %5.0f RR %5.0f", car.getDebugRlLatForce(), car.getDebugRrLatForce())); row += lineHeight;
        debugLine(graphics, font, x, row, 0xFFDDDDDD, String.format("Fz  FL %5.0f FR %5.0f", car.getDebugFlLoad(), car.getDebugFrLoad())); row += lineHeight;
        debugLine(graphics, font, x, row, 0xFFDDDDDD, String.format("    RL %5.0f RR %5.0f", car.getDebugRlLoad(), car.getDebugRrLoad())); row += lineHeight;
        debugLine(graphics, font, x, row, demandColor(maxDemand(car)), String.format("dem FL %.2f FR %.2f", car.getDebugFlDemand(), car.getDebugFrDemand())); row += lineHeight;
        debugLine(graphics, font, x, row, demandColor(maxDemand(car)), String.format("    RL %.2f RR %.2f", car.getDebugRlDemand(), car.getDebugRrDemand())); row += lineHeight;
        float flTemp = car.getTyreTemperatureFlCelsius();
        float frTemp = car.getTyreTemperatureFrCelsius();
        float rlTemp = car.getTyreTemperatureRlCelsius();
        float rrTemp = car.getTyreTemperatureRrCelsius();
        debugLine(graphics, font, x, row, tyreTemperatureColor(car, (flTemp + frTemp) * 0.5f), String.format("Tmp FL %.1f FR %.1f", flTemp, frTemp)); row += lineHeight;
        debugLine(graphics, font, x, row, tyreTemperatureColor(car, (rlTemp + rrTemp) * 0.5f), String.format("    RL %.1f RR %.1f", rlTemp, rrTemp)); row += lineHeight;
        debugLine(graphics, font, x, row, 0xFFFFAAAA, String.format("slp FL %.1f FR %.1f", car.getDebugFlSlipAngleDegrees(), car.getDebugFrSlipAngleDegrees())); row += lineHeight;
        debugLine(graphics, font, x, row, 0xFFFFAAAA, String.format("    RL %.1f RR %.1f", car.getDebugRlSlipAngleDegrees(), car.getDebugRrSlipAngleDegrees()));
    }

    private static void debugLine(GuiGraphics graphics, Font font, int x, int y, int color, String text) {
        graphics.drawString(font, text, x, y, color, true);
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
            return 0xFFFFDD66;
        }
        return 0xFFB7FFB7;
    }

    private static int tyreTemperatureColor(OpenwheelCarEntity car, float temperature) {
        float min = car.getTyreWorkingTemperatureMinCelsius();
        float max = car.getTyreWorkingTemperatureMaxCelsius();
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
}
