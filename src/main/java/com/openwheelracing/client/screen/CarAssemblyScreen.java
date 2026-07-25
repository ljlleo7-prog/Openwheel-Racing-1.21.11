package com.openwheelracing.client.screen;

import com.openwheelracing.content.car.CarLivery;
import com.openwheelracing.content.car.CarLiveryColors;
import com.openwheelracing.content.item.PrototypeCarItem;
import com.openwheelracing.content.menu.CarAssemblyMenu;
import com.openwheelracing.network.OWRNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CarAssemblyScreen extends AbstractContainerScreen<CarAssemblyMenu> {
    private static final int[] WORKSTATION_SLOT_X = {52, 52, 18, 52, 86, 86, 130};
    private static final int[] WORKSTATION_SLOT_Y = {36, 70, 53, 16, 70, 36, 45};
    private int colorPickerChannel = -1;
    private int pickerRed;
    private int pickerGreen;
    private int pickerBlue;

    public CarAssemblyScreen(CarAssemblyMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageHeight = 218;
        imageWidth = 256;
        inventoryLabelY = 124;
    }

    @Override
    protected void init() {
        super.init();
        if (colorPickerChannel >= 0) {
            addColorPickerWidgets();
            return;
        }
        addTuneButtons(0, 28);
        addTuneButtons(1, 43);
        addTuneButtons(2, 58);
        addTuneButtons(3, 73);
        addLiveryButtons(88);
        addRenderableWidget(Button.builder(Component.literal("Edit Livery"), button -> openLiveryEditor())
            .bounds(leftPos + 184, topPos + 112, 62, 14)
            .build());
        addLiveryColorButtons(132);
        addRenderableWidget(Button.builder(Component.literal("Repair"), button -> OWRNetwork.sendToServer(new OWRNetwork.RepairCarMessage()))
            .bounds(leftPos + 190, topPos + 98, 52, 14)
            .build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        graphics.fill(x + 6, y + 6, x + 172, y + 114, 0xFFDADADA);
        graphics.fill(x + 178, y + 6, x + 250, y + 114, 0xFFE0E0E0);
        graphics.fill(x + 6, y + 120, x + 250, y + 210, 0xFFD0D0D0);
        graphics.fill(x + 70, y + 44, x + 95, y + 49, 0xFF55555A);
        for (int slot = 0; slot < WORKSTATION_SLOT_X.length; slot++) {
            drawSlot(graphics, x + WORKSTATION_SLOT_X[slot], y + WORKSTATION_SLOT_Y[slot]);
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(graphics, x + 46 + column * 18, y + 136 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(graphics, x + 46 + column * 18, y + 194);
        }

        int progress = menu.getScaledProgress();
        if (progress > 0) {
            graphics.fill(x + 96, y + 44, x + 96 + progress, y + 49, 0xFF55AAFF);
        }

        graphics.drawString(font, title, x + 10, y + 10, 0xFF404040, false);
        graphics.drawString(font, "Assembly", x + 98, y + 32, 0xFF404040, false);
        graphics.drawString(font, "Setup", x + 190, y + 10, 0xFF404040, false);
        graphics.drawString(font, "P", x + 190, y + 30, 0xFF404040, false);
        graphics.drawString(font, "T", x + 190, y + 45, 0xFF404040, false);
        graphics.drawString(font, "A", x + 190, y + 60, 0xFF404040, false);
        graphics.drawString(font, "G", x + 190, y + 75, 0xFF404040, false);
        graphics.drawString(font, "Livery", x + 190, y + 90, 0xFF404040, false);
        graphics.drawString(font, playerInventoryTitle, x + 8, y + inventoryLabelY, 0xFF404040, false);
        if (!menu.getOutputStack().isEmpty()) {
            String name = CarLivery.fromIndex(PrototypeCarItem.getLivery(menu.getOutputStack())).displayName();
            graphics.drawString(font, name, x + 190, y + 104, 0xFF404040, false);
            CarLiveryColors colors = PrototypeCarItem.getLiveryColors(menu.getOutputStack());
            graphics.drawString(font, "B " + CarLiveryColors.colorName(colors.body()), x + 190, y + 144, colors.bodySide(), false);
            graphics.drawString(font, "A1 " + CarLiveryColors.colorName(colors.accent1()), x + 190, y + 155, colors.accent1Side(), false);
            graphics.drawString(font, "A2 " + CarLiveryColors.colorName(colors.accent2()), x + 190, y + 166, colors.accent2Side(), false);
        }
        if (colorPickerChannel >= 0) {
            renderColorPicker(graphics);
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
        graphics.fill(x, y, x + 18, y + 18, 0xFFFFFFFF);
        graphics.fill(x, y, x + 17, y + 17, 0xFF8B8B8B);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFFEFEFEF);
    }

    private void addTuneButtons(int setupSlot, int yOffset) {
        addRenderableWidget(Button.builder(Component.literal("-"), button -> OWRNetwork.sendToServer(new OWRNetwork.TuneCarMessage(setupSlot, -1)))
            .bounds(leftPos + 214, topPos + yOffset, 12, 11)
            .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> OWRNetwork.sendToServer(new OWRNetwork.TuneCarMessage(setupSlot, 1)))
            .bounds(leftPos + 232, topPos + yOffset, 12, 11)
            .build());
    }

    private void addLiveryButtons(int yOffset) {
        addRenderableWidget(Button.builder(Component.literal("-"), button -> OWRNetwork.sendToServer(new OWRNetwork.CycleLiveryMessage(-1)))
            .bounds(leftPos + 214, topPos + yOffset, 12, 11)
            .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> OWRNetwork.sendToServer(new OWRNetwork.CycleLiveryMessage(1)))
            .bounds(leftPos + 232, topPos + yOffset, 12, 11)
            .build());
    }

    private void addLiveryColorButtons(int yOffset) {
        addRenderableWidget(Button.builder(Component.literal("B"), button -> openColorPicker(0))
            .bounds(leftPos + 190, topPos + yOffset, 12, 11)
            .build());
        addRenderableWidget(Button.builder(Component.literal("A1"), button -> openColorPicker(1))
            .bounds(leftPos + 207, topPos + yOffset, 14, 11)
            .build());
        addRenderableWidget(Button.builder(Component.literal("A2"), button -> openColorPicker(2))
            .bounds(leftPos + 232, topPos + yOffset, 14, 11)
            .build());
    }

    private void openLiveryEditor() {
        if (!menu.getOutputStack().isEmpty()) {
            Minecraft.getInstance().setScreen(new LiveryEditorScreen(this, menu.getOutputStack()));
        }
    }

    private void openColorPicker(int channel) {
        if (menu.getOutputStack().isEmpty()) {
            return;
        }
        colorPickerChannel = channel;
        int color = PrototypeCarItem.getLiveryColors(menu.getOutputStack()).channel(channel);
        pickerRed = CarLiveryColors.red(color);
        pickerGreen = CarLiveryColors.green(color);
        pickerBlue = CarLiveryColors.blue(color);
        rebuildWidgets();
    }

    private void closeColorPicker() {
        colorPickerChannel = -1;
        rebuildWidgets();
    }

    private void applyColorPicker() {
        if (colorPickerChannel >= 0) {
            int color = CarLiveryColors.rgb(pickerRed, pickerGreen, pickerBlue);
            OWRNetwork.sendToServer(new OWRNetwork.SetLiveryColorMessage(colorPickerChannel, color));
        }
        closeColorPicker();
    }

    private void addColorPickerWidgets() {
        int x = leftPos + 60;
        int y = topPos + 64;
        addRenderableWidget(new RgbSlider(x, y, 112, 18, 0));
        addRenderableWidget(new RgbSlider(x, y + 24, 112, 18, 1));
        addRenderableWidget(new RgbSlider(x, y + 48, 112, 18, 2));
        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applyColorPicker())
            .bounds(leftPos + 70, topPos + 128, 52, 18)
            .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> closeColorPicker())
            .bounds(leftPos + 134, topPos + 128, 52, 18)
            .build());
    }

    private void renderColorPicker(GuiGraphics graphics) {
        int x = leftPos + 34;
        int y = topPos + 34;
        int color = CarLiveryColors.rgb(pickerRed, pickerGreen, pickerBlue);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0x88000000);
        graphics.fill(x, y, x + 188, y + 118, 0xFF2A3038);
        graphics.fill(x + 4, y + 4, x + 184, y + 114, 0xFFDFDFDF);
        graphics.drawString(font, "RGB " + channelLabel(colorPickerChannel), x + 10, y + 10, 0xFF303030, false);
        graphics.fill(x + 132, y + 14, x + 172, y + 54, color);
        graphics.drawString(font, CarLiveryColors.colorName(color), x + 126, y + 60, 0xFF303030, false);
        graphics.drawString(font, "R", x + 12, y + 34, 0xFFB00020, false);
        graphics.drawString(font, "G", x + 12, y + 58, 0xFF006E36, false);
        graphics.drawString(font, "B", x + 12, y + 82, 0xFF0057B8, false);
    }

    private static String channelLabel(int channel) {
        return switch (channel) {
            case 0 -> "Body";
            case 1 -> "Accent 1";
            case 2 -> "Accent 2";
            default -> "Color";
        };
    }

    private class RgbSlider extends AbstractSliderButton {
        private final int channel;

        private RgbSlider(int x, int y, int width, int height, int channel) {
            super(x, y, width, height, Component.empty(), 0.0);
            this.channel = channel;
            value = valueForChannel(channel) / 255.0;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(Integer.toString(valueForChannel(channel))));
        }

        @Override
        protected void applyValue() {
            int valueInt = (int) Math.round(value * 255.0);
            switch (channel) {
                case 0 -> pickerRed = valueInt;
                case 1 -> pickerGreen = valueInt;
                case 2 -> pickerBlue = valueInt;
                default -> {
                }
            }
        }

        private int valueForChannel(int channel) {
            return switch (channel) {
                case 0 -> pickerRed;
                case 1 -> pickerGreen;
                case 2 -> pickerBlue;
                default -> 0;
            };
        }
    }
}
