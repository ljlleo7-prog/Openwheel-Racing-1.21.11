package com.openwheelracing.client.screen;

import com.openwheelracing.content.race.BoPDriverRow;
import com.openwheelracing.content.race.RaceDirectorSnapshot;
import com.openwheelracing.network.OWRNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Race Director's compact all-history driver balancing console. */
public final class BoPConsoleScreen extends Screen {
    private final Screen parent;
    private RaceDirectorSnapshot snapshot;
    public BoPConsoleScreen(Screen parent, RaceDirectorSnapshot snapshot) {
        super(Component.literal("Balance of Performance")); this.parent = parent; this.snapshot = snapshot;
    }
    public void applySnapshot(RaceDirectorSnapshot snapshot) { this.snapshot = snapshot; rebuildWidgets(); }
    @Override protected void init() { rebuildWidgets(); }
    @Override protected void rebuildWidgets() {
        clearWidgets();
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> Minecraft.getInstance().setScreen(parent)).bounds(width / 2 - 250, 24, 50, 18).build());
        addRenderableWidget(Button.builder(Component.literal("LAPS -"), b -> setLaps(snapshot.bopSelectedLaps() - 1)).bounds(width / 2 - 190, 24, 52, 18).build());
        addRenderableWidget(Button.builder(Component.literal("LAPS +"), b -> setLaps(snapshot.bopSelectedLaps() + 1)).bounds(width / 2 - 132, 24, 52, 18).build());
        int y = 66;
        for (BoPDriverRow row : snapshot.bopDrivers().stream().limit(10).toList()) {
            int rowY = y;
            addRenderableWidget(Button.builder(Component.literal("-"), b -> adjust(row, -1, 0)).bounds(width / 2 + 105, rowY - 2, 18, 16).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> adjust(row, 1, 0)).bounds(width / 2 + 125, rowY - 2, 18, 16).build());
            addRenderableWidget(Button.builder(Component.literal("-"), b -> adjust(row, 0, -1)).bounds(width / 2 + 190, rowY - 2, 18, 16).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> adjust(row, 0, 1)).bounds(width / 2 + 210, rowY - 2, 18, 16).build());
            y += 22;
        }
    }
    private void adjust(BoPDriverRow row, int weightDelta, int powerDelta) {
        OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSetBoPMessage(row.driverId(), row.weightPercent() + weightDelta, row.powerPercent() + powerDelta));
    }
    private void setLaps(int laps) { OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSetBoPLapsMessage(Math.max(1, Math.min(20, laps)))); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Do not call Screen.renderBackground here. The console can be opened from
        // another screen in the same frame, and 1.21.11 rejects a second blur pass.
        graphics.fill(0, 0, width, height, 0xFF15191F);
        graphics.fill(width / 2 - 260, 14, width / 2 + 300, Math.min(height - 18, 82 + Math.min(10, snapshot.bopDrivers().size()) * 22), 0xFF20262D);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFE8EDF2);
        graphics.drawString(font, "Average of " + snapshot.bopSelectedLaps() + " fastest valid laps from entire history", width / 2 - 245, 42, 0xFFC9D1D9);
        graphics.drawString(font, "DRIVER", width / 2 - 245, 54, 0xFF7EE787);
        graphics.drawString(font, "AVG / RECORD", width / 2 - 55, 54, 0xFF7EE787);
        graphics.drawString(font, "WEIGHT %", width / 2 + 100, 54, 0xFF7EE787);
        graphics.drawString(font, "POWER %", width / 2 + 185, 54, 0xFF7EE787);
        graphics.drawString(font, "EST LAP", width / 2 + 250, 54, 0xFF7EE787);
        int y = 68;
        for (BoPDriverRow row : snapshot.bopDrivers().stream().limit(10).toList()) {
            graphics.drawString(font, fit(row.driverName() + " (" + row.sampleLaps() + ")", 145), width / 2 - 245, y, 0xFFE8EDF2);
            graphics.drawString(font, format(row.averageLapMillis()) + " / " + format(row.bestLapMillis()), width / 2 - 55, y, 0xFFC9D1D9);
            graphics.drawString(font, fit(row.bestLapSessionName(), 72), width / 2 - 55, y + 10, 0xFF8792A2);
            graphics.drawString(font, String.format("%+d", row.weightPercent()), width / 2 + 148, y, 0xFFFFD866);
            graphics.drawString(font, String.format("%+d", row.powerPercent()), width / 2 + 233, y, 0xFFFFD866);
            graphics.drawString(font, format(row.estimatedLapMillis()), width / 2 + 270, y, 0xFF7EE787);
            y += 22;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    private String fit(String value, int max) { return font.width(value) <= max ? value : font.plainSubstrByWidth(value, max - 3) + "..."; }
    private String format(int millis) { return millis <= 0 ? "—" : String.format("%d:%02d.%03d", millis / 60000, millis / 1000 % 60, millis % 1000); }
    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
