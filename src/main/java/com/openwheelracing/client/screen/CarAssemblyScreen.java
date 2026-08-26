package com.openwheelracing.client.screen;

import com.openwheelracing.content.car.CarLivery;
import com.openwheelracing.content.car.CarLiveryColors;
import com.openwheelracing.content.car.PrototypeCarSetup;
import com.openwheelracing.content.car.CarSetupPrediction;
import com.openwheelracing.content.item.PrototypeCarItem;
import com.openwheelracing.content.menu.CarAssemblyMenu;
import com.openwheelracing.content.block.entity.CarWorkstationType;
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
    private CarWorkstationType visibleWorkstationType;
    private int pickerRed;
    private int pickerGreen;
    private int pickerBlue;
    private PrototypeCarSetup displayedSetup;
    private PrototypeCarSetup draftSetup;
    private final SetupSlider[] setupSliders = new SetupSlider[6];

    public CarAssemblyScreen(CarAssemblyMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageHeight = 218;
        imageWidth = 256;
        inventoryLabelY = 124;
    }

    @Override
    protected void init() {
        imageWidth = menu.getWorkstationType() == CarWorkstationType.SETUP ? 548 : 256;
        super.init();
        visibleWorkstationType = menu.getWorkstationType();
        displayedSetup = currentSetup();
        draftSetup = displayedSetup;
        if (colorPickerChannel >= 0) {
            addColorPickerWidgets();
            return;
        }
        if (visibleWorkstationType == CarWorkstationType.SETUP) {
            addSetupSliders();
            addRenderableWidget(Button.builder(Component.literal("Apply setup"), button -> applyDraftSetup())
                .bounds(leftPos + 400, topPos + 154, 88, 16)
                .build());
            addRenderableWidget(Button.builder(Component.literal("Repair"), button -> OWRNetwork.sendToServer(new OWRNetwork.RepairCarMessage()))
                .bounds(leftPos + 492, topPos + 154, 48, 16)
                .build());
        } else if (menu.allowsSetup()) {
            addTuneButtons(0, 28);
            addTuneButtons(3, 43);
            addRenderableWidget(Button.builder(Component.literal("Repair"), button -> OWRNetwork.sendToServer(new OWRNetwork.RepairCarMessage()))
                .bounds(leftPos + 190, topPos + 98, 52, 14)
                .build());
        }
        if (menu.allowsLivery()) {
            addLiveryButtons(88);
            addRenderableWidget(Button.builder(Component.literal("Edit Livery"), button -> openLiveryEditor())
                .bounds(leftPos + 184, topPos + 112, 62, 14)
                .build());
            addLiveryColorButtons(132);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (visibleWorkstationType == CarWorkstationType.SETUP && !currentSetup().equals(displayedSetup)) {
            rebuildWidgets();
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (visibleWorkstationType != menu.getWorkstationType()) {
            rebuildWidgets();
        }
        int x = leftPos;
        int y = topPos;

        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        if (visibleWorkstationType == CarWorkstationType.SETUP) {
            renderDedicatedSetup(graphics, x, y);
            return;
        }
        if (menu.allowsConstruction()) {
            graphics.fill(x + 6, y + 6, x + 172, y + 114, 0xFFDADADA);
        }
        if (menu.allowsSetup() || menu.allowsLivery()) {
            graphics.fill(x + 178, y + 6, x + 250, y + 114, 0xFFE0E0E0);
        }
        graphics.fill(x + 6, y + 120, x + 250, y + 210, 0xFFD0D0D0);
        if (menu.allowsConstruction()) {
            graphics.fill(x + 70, y + 44, x + 95, y + 49, 0xFF55555A);
        }
        for (int slot = 0; slot < WORKSTATION_SLOT_X.length; slot++) {
            if (slot == 6 || menu.allowsConstruction()) {
                drawSlot(graphics, x + WORKSTATION_SLOT_X[slot], y + WORKSTATION_SLOT_Y[slot]);
            }
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
        if (menu.allowsConstruction()) {
            graphics.drawString(font, "Construction", x + 88, y + 32, 0xFF404040, false);
        }
        if (menu.allowsSetup()) {
            graphics.drawString(font, "Setup", x + 190, y + 10, 0xFF404040, false);
            graphics.drawString(font, "P", x + 190, y + 30, 0xFF404040, false);
            graphics.drawString(font, "T", x + 190, y + 45, 0xFF404040, false);
            graphics.drawString(font, "A", x + 190, y + 60, 0xFF404040, false);
            graphics.drawString(font, "G", x + 190, y + 75, 0xFF404040, false);
        }
        if (menu.allowsLivery()) {
            graphics.drawString(font, "Livery", x + 190, y + 90, 0xFF404040, false);
        }
        graphics.drawString(font, playerInventoryTitle, x + 8, y + inventoryLabelY, 0xFF404040, false);
        if (!menu.getOutputStack().isEmpty() && menu.allowsSetup()) {
            ComponentDamageDisplay.drawCompact(graphics, font, PrototypeCarItem.getComponentDamage(menu.getOutputStack()), x + 178, y + 86, 0xFF404040);
        }
        if (!menu.getOutputStack().isEmpty() && menu.allowsLivery()) {
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
        if (visibleWorkstationType == CarWorkstationType.SETUP) renderCurrentSetupMarkers(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF555555);
        graphics.fill(x, y, x + 18, y + 18, 0xFFFFFFFF);
        graphics.fill(x, y, x + 17, y + 17, 0xFF8B8B8B);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFFEFEFEF);
    }

    private void addTuneButtons(int setupSlot, int yOffset) {
        addRenderableWidget(Button.builder(Component.literal("-"), button -> sendRelativeTune(setupSlot, -1))
            .bounds(leftPos + 214, topPos + yOffset, 12, 11)
            .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> sendRelativeTune(setupSlot, 1))
            .bounds(leftPos + 232, topPos + yOffset, 12, 11)
            .build());
    }

    private void addSetupSliders() {
        PrototypeCarSetup setup = draftSetup;
        int sliderX = leftPos + 174;
        int sliderWidth = 210;
        setupSliders[0] = null;
        setupSliders[1] = addRenderableWidget(new SetupSlider(sliderX, topPos + 32, sliderWidth, 18, 1, 3, 7, setup.frontWing(), "Front wing", "°"));
        setupSliders[2] = addRenderableWidget(new SetupSlider(sliderX, topPos + 52, sliderWidth, 18, 2, 9, 15, setup.rearWing(), "Rear wing", "°"));
        setupSliders[4] = addRenderableWidget(new SetupSlider(sliderX, topPos + 72, sliderWidth, 18, 4, 0, 10, setup.antiRoll(), "Anti-roll", ""));
        setupSliders[3] = addRenderableWidget(new SetupSlider(sliderX, topPos + 92, sliderWidth, 18, 3, 0, 2, setup.gearing(), "Final drive", ""));
        setupSliders[5] = addRenderableWidget(new SetupSlider(sliderX, topPos + 112, sliderWidth, 18, 5, 50, 65, setup.brakeBias(), "Brake bias", "% F"));
    }

    private void renderDedicatedSetup(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 6, y + 6, x + 168, y + 130, 0xFF252B33);
        graphics.fill(x + 170, y + 6, x + 390, y + 144, 0xFF303841);
        graphics.fill(x + 392, y + 6, x + imageWidth - 6, y + 144, 0xFF272F38);
        graphics.fill(x + 6, y + 132, x + imageWidth - 6, y + 210, 0xFFD0D0D0);
        graphics.drawString(font, title, x + 12, y + 10, 0xFFE8EDF2, false);
        graphics.drawString(font, "Power mode: 1 (locked)", x + 174, y + 17, 0xFF8B949E, false);
        graphics.drawString(font, "Place car here", x + 45, y + 38, 0xFFADB7C2, false);
        drawSlot(graphics, x + WORKSTATION_SLOT_X[6], y + WORKSTATION_SLOT_Y[6]);
        graphics.drawString(font, "Move sliders to preview", x + 16, y + 74, 0xFFADB7C2, false);
        graphics.drawString(font, "Apply commits the full setup", x + 16, y + 86, 0xFFADB7C2, false);
        drawCombinedPrediction(graphics, x + 400, y + 14);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            drawSlot(graphics, x + 46 + column * 18, y + 136 + row * 18);
        for (int column = 0; column < 9; column++) drawSlot(graphics, x + 46 + column * 18, y + 194);
        graphics.drawString(font, playerInventoryTitle, x + 8, y + inventoryLabelY, 0xFF404040, false);
        int progress = menu.getScaledProgress();
        if (progress > 0) graphics.fill(x + 174, y + 143, x + 174 + progress * 8, y + 146, 0xFF55AAFF);
    }

    private void drawCombinedPrediction(GuiGraphics graphics, int x, int y) {
        CarSetupPrediction.Summary summary = CarSetupPrediction.combined(
            draftSetup.power() / 3.0, draftSetup.gearing() / 2.0,
            (draftSetup.frontWing() - 3) / 4.0, (draftSetup.rearWing() - 9) / 6.0,
            draftSetup.antiRoll() / 10.0, (draftSetup.brakeBias() - 50) / 15.0);
        graphics.drawString(font, "COMBINED DRAFT", x, y, 0xFFE8EDF2, false);
        drawSummaryLine(graphics, x, y + 11, "accel", summary.acceleration(), false);
        drawSummaryLine(graphics, x, y + 21, "top speed", summary.topSpeed(), false);
        drawSummaryLine(graphics, x, y + 31, "aero grip", summary.grip(), false);
        drawSummaryLine(graphics, x, y + 41, "drag", summary.drag(), false);
        drawSummaryLine(graphics, x, y + 51, "balance", summary.balance(), true);
    }

    private void drawSummaryLine(GuiGraphics graphics, int x, int y, String name, double position, boolean balance) {
        String text = balance ? CarSetupPrediction.balanceTerm(position) : name + " - " + CarSetupPrediction.level(position);
        graphics.drawString(font, text, x, y, CarSetupPrediction.color(position), false);
    }

    private void renderCurrentSetupMarkers(GuiGraphics graphics) {
        for (SetupSlider slider : setupSliders) {
            if (slider == null) continue;
            int markerX = slider.getX() + 4 + (int) Math.round(slider.currentPosition() * (slider.getWidth() - 8));
            int top = slider.getY() - 2;
            graphics.fill(markerX - 2, top, markerX + 3, top + 2, 0xFFFFFFFF);
            graphics.fill(markerX, top + 2, markerX + 1, slider.getY() + slider.getHeight() + 1, 0xFFFFFFFF);
        }
        graphics.drawString(font, "white marker = current", leftPos + 396, topPos + 134, 0xFFADB7C2, false);
    }

    private void applyDraftSetup() {
        if (menu.getOutputStack().isEmpty() || draftSetup.equals(displayedSetup)) return;
        OWRNetwork.sendToServer(new OWRNetwork.ApplyCarSetupMessage(draftSetup.power(), draftSetup.gearing(),
            draftSetup.frontWing(), draftSetup.rearWing(), draftSetup.antiRoll(), draftSetup.brakeBias()));
    }

    private PrototypeCarSetup currentSetup() {
        return menu.getOutputStack().isEmpty() ? PrototypeCarSetup.DEFAULT : PrototypeCarItem.getSetup(menu.getOutputStack());
    }

    private void sendRelativeTune(int slot, int delta) {
        PrototypeCarSetup setup = currentSetup();
        int current = slot == 0 ? setup.power() : setup.gearing();
        OWRNetwork.sendToServer(new OWRNetwork.TuneCarMessage(slot, current + delta));
    }

    private class SetupSlider extends AbstractSliderButton {
        private final int slot;
        private final int min;
        private final int max;
        private final String label;
        private final String suffix;

        SetupSlider(int x, int y, int width, int height, int slot, int min, int max, int initial, String label, String suffix) {
            super(x, y, width, height, Component.empty(), (initial - min) / (double) (max - min));
            this.slot = slot; this.min = min; this.max = max; this.label = label; this.suffix = suffix;
            updateMessage();
        }

        private int selectedValue() { return min + (int) Math.round(value * (max - min)); }
        @Override protected void updateMessage() { setMessage(Component.literal(label + ": " + selectedValue() + suffix)); }
        @Override protected void applyValue() { draftSetup = draftSetup.withTuning(slot, selectedValue()); }
        private double normalizedValue() { return (selectedValue() - min) / (double) (max - min); }
        private double currentPosition() {
            int current = switch (slot) {
                case 0 -> displayedSetup.power(); case 1 -> displayedSetup.frontWing(); case 2 -> displayedSetup.rearWing();
                case 3 -> displayedSetup.gearing(); case 4 -> displayedSetup.antiRoll(); case 5 -> displayedSetup.brakeBias(); default -> min;
            };
            return (current - min) / (double) (max - min);
        }
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
