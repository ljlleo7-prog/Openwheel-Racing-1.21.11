package com.openwheelracing.client.screen;

import com.openwheelracing.client.map.LiveMapClient;
import com.openwheelracing.client.map.LiveMapRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LiveMapScreen extends Screen {
    private int scroll;

    public LiveMapScreen() { super(Component.translatable("map.openwheelracing.title")); }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, scroll - (int) vertical);
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF0B1119);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        int panelWidth = Math.max(50, (width - 30) / 2);
        int panelHeight = Math.max(50, (height - 60) * 2 / 3);
        LiveMapRenderer.panel(graphics, 10, 24, panelWidth, panelHeight, false);
        LiveMapRenderer.panel(graphics, 20 + panelWidth, 24, panelWidth, panelHeight, true);
        int top = panelHeight + 30;
        graphics.drawString(font, Component.translatable("map.openwheelracing.legend"), 12, top, 0xFFCCD4DF, false);
        var snapshot = LiveMapClient.snapshot();
        if (snapshot != null) {
            var cars = snapshot.cars().stream().sorted(java.util.Comparator.comparingInt(car -> LiveMapClient.COLORS.identity(car.driverId()).number())).toList();
            int rows = Math.max(1, (height - top - 42) / 11);
            int columns = Math.max(1, width / 180);
            int totalRows = (cars.size() + columns - 1) / columns;
            scroll = Math.clamp(scroll, 0, Math.max(0, totalRows - rows));
            for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
                int index = (row + scroll) * columns + column;
                if (index >= cars.size()) continue;
                var car = cars.get(index);
                var identity = LiveMapClient.COLORS.identity(car.driverId());
                String label = "#" + identity.number() + " " + car.label();
                graphics.drawString(font, font.plainSubstrByWidth(label, width / columns - 16), 12 + column * (width / columns), top + 14 + row * 11, identity.color(), false);
            }
        }
        graphics.drawCenteredString(font, Component.translatable("map.openwheelracing.screen_hint"), width / 2, height - 15, 0xFFCCD4DF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
