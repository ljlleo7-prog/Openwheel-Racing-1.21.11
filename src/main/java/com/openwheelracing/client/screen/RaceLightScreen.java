package com.openwheelracing.client.screen;

import com.openwheelracing.content.menu.RaceLightMenu;
import com.openwheelracing.content.race.PitLightMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class RaceLightScreen extends AbstractContainerScreen<RaceLightMenu> {
    public RaceLightScreen(RaceLightMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 230; imageHeight = 128; inventoryLabelY = 1000;
    }
    @Override protected void init() { super.init(); rebuildWidgets(); }
    @Override protected void rebuildWidgets() {
        clearWidgets();
        switch (menu.getLightType()) {
            case FLAG -> {
                button("screen.openwheelracing.race_light.auto_detect", 0, 14, 48, 90);
                button("-", 1, 110, 48, 24); button("+", 2, 138, 48, 24);
                button("-", 3, 110, 72, 24); button("+", 4, 138, 72, 24);
            }
            case START -> { button("-", 5, 72, 58, 28); button("+", 6, 132, 58, 28); }
            case PIT -> { for (PitLightMode mode : PitLightMode.values()) button("screen.openwheelracing.race_light.pit." + mode.name().toLowerCase(), 10 + mode.ordinal(), 14 + mode.ordinal() * 70, 58, 64); }
        }
    }
    private void button(String key, int id, int x, int y, int width) {
        Component label = key.equals("-") || key.equals("+") ? Component.literal(key) : Component.translatable(key);
        addRenderableWidget(Button.builder(label, b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id)).bounds(leftPos + x, topPos + y, width, 18).build());
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF20252B);
        graphics.fill(leftPos + 7, topPos + 20, leftPos + imageWidth - 7, topPos + imageHeight - 7, 0xFF303840);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 7, 0xFFF0F3F5, false);
        Component status = switch (menu.getLightType()) {
            case FLAG -> Component.translatable("screen.openwheelracing.race_light.flag_status", menu.getSector(), menu.getMinisector(), menu.isAutomaticSector() ? "AUTO" : "MANUAL");
            case START -> Component.translatable("screen.openwheelracing.race_light.start_order", menu.getStartOrder());
            case PIT -> Component.translatable("screen.openwheelracing.race_light.pit_mode", menu.getPitMode().name());
        };
        graphics.drawCenteredString(font, status, imageWidth / 2, 30, 0xFFD8DEE4);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { renderBackground(graphics, mouseX, mouseY, partialTick); super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY); }
}
