package com.openwheelracing.client.screen;

import com.openwheelracing.content.menu.CarPartsReplacementMenu;
import com.openwheelracing.content.item.PrototypeCarItem;
import com.openwheelracing.content.block.entity.CarPartsReplacementWorkstationBlockEntity;
import com.openwheelracing.network.OWRNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CarPartsReplacementScreen extends AbstractContainerScreen<CarPartsReplacementMenu> {
    private Button startButton;

    public CarPartsReplacementScreen(CarPartsReplacementMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 286;
        imageHeight = 176;
        inventoryLabelY = 82;
    }

    @Override
    protected void init() {
        super.init();
        startButton = addRenderableWidget(Button.builder(
            Component.translatable("screen.openwheelracing.car_parts_replacement.start"),
            button -> OWRNetwork.sendToServer(new OWRNetwork.StartPartReplacementMessage()))
            .bounds(leftPos + 50, topPos + 72, 76, 16)
            .build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        startButton.active = menu.canStartReplacement() && !menu.isReplacing();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        graphics.fill(x + 6, y + 18, x + 170, y + 88, 0xFFDADADA);
        graphics.fill(x + 176, y + 18, x + 280, y + 142, 0xFFE0E0E0);
        graphics.fill(x + 6, y + 90, x + 170, y + 170, 0xFFD0D0D0);
        drawSlot(graphics, x + 44, y + 48);
        drawSlot(graphics, x + 80, y + 48);
        drawSlot(graphics, x + 134, y + 48);
        graphics.fill(x + 99, y + 54, x + 131, y + 59, 0xFF55555A);
        int progress = menu.getScaledProgress();
        if (progress > 0) graphics.fill(x + 99, y + 54, x + 99 + progress, y + 59, 0xFF55AAFF);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            drawSlot(graphics, x + 8 + column * 18, y + 94 + row * 18);
        for (int column = 0; column < 9; column++) drawSlot(graphics, x + 8 + column * 18, y + 152);
        graphics.drawString(font, title, x + 8, y + 6, 0xFF404040, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.car_parts_replacement.car"), x + 36, y + 34, 0xFF404040, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.car_parts_replacement.new_part"), x + 69, y + 34, 0xFF404040, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.car_parts_replacement.removed_part"), x + 122, y + 34, 0xFF404040, false);
        graphics.drawString(font, playerInventoryTitle, x + 8, y + inventoryLabelY, 0xFF404040, false);
        var car = menu.getContainer().getItem(CarPartsReplacementWorkstationBlockEntity.SLOT_CAR);
        if (!car.isEmpty()) {
            ComponentDamageDisplay.draw(graphics, font, PrototypeCarItem.getComponentDamage(car), x + 184, y + 24);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF555555);
        graphics.fill(x, y, x + 17, y + 17, 0xFF8B8B8B);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFFEFEFEF);
    }
}
