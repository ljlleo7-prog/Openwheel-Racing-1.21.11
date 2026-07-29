package com.openwheelracing.client.hud;

import com.openwheelracing.client.input.WheelInputSettings;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.race.OWRLapRecords;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class CarHudOverlay {
    private static final int PANEL_WIDTH = 142;
    private static final int PANEL_HEIGHT = 100;
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

        if (settings.showDrivingHud) {
            renderErsMeter(graphics, font, car);

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
            graphics.drawString(font, "CP   " + (car.hasCheckpoint() ? "OK" : "--"), x + 8, y + 62, car.hasCheckpoint() ? 0xFFB7FFB7 : 0xFFFFDD66, false);
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

        if (settings.showSetupHud) {
            int setupX = 8;
            int setupY = graphics.guiHeight() - 89;
            graphics.fill(setupX, setupY, setupX + 172, setupY + 81, 0x99000000);
            graphics.renderOutline(setupX, setupY, 172, 81, 0xFF555555);
            graphics.drawString(font, "PWR " + car.getSetup().power(), setupX + 7, setupY + 7, 0xFFFF9999, false);
            graphics.drawString(font, "TYRE C" + (car.getTyreCompound() + 1), setupX + 7, setupY + 18, 0xFFB7FFB7, false);
            graphics.drawString(font, "AERO " + car.getSetup().aero(), setupX + 7, setupY + 29, 0xFF99DDFF, false);
            graphics.drawString(font, "GEAR " + car.getSetup().gearing(), setupX + 52, setupY + 29, 0xFFFFDD88, false);
            graphics.drawString(font, Component.translatable("hud.openwheelracing.controls.drive"), setupX + 7, setupY + 43, 0xFFDDDDDD, false);
            graphics.drawString(font, Component.translatable("hud.openwheelracing.controls.shift"), setupX + 7, setupY + 54, 0xFFDDDDDD, false);
            graphics.drawString(font, Component.translatable("hud.openwheelracing.controls.exit"), setupX + 7, setupY + 65, 0xFFDDDDDD, false);
        }

        if (settings.showPhysicsDebugHud) {
            renderPhysicsDebug(graphics, font, car);
        }

        if (settings.showRankingHud) {
            renderRankingBoard(graphics, font);
        }
    }

    private static void renderErsMeter(GuiGraphics graphics, Font font, OpenwheelCarEntity car) {
        int width = 182;
        int height = 7;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 31;
        float energyPercent = Math.max(0.0f, Math.min(100.0f, car.getErsEnergyPercent()));
        int fillWidth = Math.round((width - 2) * energyPercent / 100.0f);
        int fillColor = ersEnergyColor(energyPercent);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xCC000000);
        graphics.fill(x, y, x + width, y + height, 0xFF2B2118);
        if (fillWidth > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + height - 1, fillColor);
            graphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + 2, 0x66FFFFFF);
        }
        graphics.renderOutline(x - 1, y - 1, width + 2, height + 2, 0xFF3F3F3F);

        String left = "ERS " + car.getErsModeLabel();
        String right = ersActivityLabel(car);
        int labelY = y - 10;
        graphics.drawString(font, left, x, labelY, 0xFFE8E8E8, true);
        graphics.drawString(font, right, x + width - font.width(right), labelY, ersActivityColor(car.getErsActivity()), true);
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
        int firstTicks = ranking.get(0).ticks();
        for (int i = 0; i < rowCount; i++) {
            OWRLapRecords.DriverBest entry = ranking.get(i);
            int ry = py + headerHeight + i * rowHeight + 1;
            int nameColor = i == 0 ? 0xFFFFDD44 : 0xFFCCCCCC;
            String pos = (i + 1) + ".";
            String name = entry.name().length() > 10 ? entry.name().substring(0, 10) : entry.name();
            String time = formatLapTime(entry.ticks());
            String gap = i == 0 ? "" : "+" + formatGap(entry.ticks() - firstTicks);
            graphics.drawString(font, pos, px + 4, ry, 0xFF888888, false);
            graphics.drawString(font, name, px + 16, ry, nameColor, false);
            graphics.drawString(font, time, px + 80, ry, nameColor, false);
            if (!gap.isEmpty()) {
                graphics.drawString(font, gap, px + 116, ry, 0xFF888888, false);
            }
        }
    }

    private static String formatGap(int ticks) {
        int cs = ticks * 5;
        int s = cs / 100;
        int frac = cs % 100;
        return s + "." + String.format("%02d", frac);
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

    private static String formatLapTime(int ticks) {
        if (ticks <= 0) {
            return "--:--.--";
        }
        int totalCentiseconds = ticks * 5;
        int minutes = totalCentiseconds / 6000;
        int seconds = totalCentiseconds / 100 % 60;
        int centiseconds = totalCentiseconds % 100;
        return String.format("%d:%02d.%02d", minutes, seconds, centiseconds);
    }
}
