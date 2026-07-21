package com.openwheelracing.client.screen;

import com.openwheelracing.client.input.WheelInputSettings;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class OpenwheelSetupScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int CONTENT_HEIGHT = 430;
    private final Screen parent;
    private WheelInputSettings settings;
    private int scrollOffset;

    public OpenwheelSetupScreen(Screen parent) {
        super(Component.translatable("screen.openwheelracing.setup.title"));
        this.parent = parent;
        this.settings = WheelInputSettings.copyOfCurrent();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    protected void rebuildWidgets() {
        clearWidgets();
        int x = (width - PANEL_WIDTH) / 2;
        int y = 42 - scrollOffset;
        int buttonWidth = 160;
        int left = x + 16;
        int right = x + 204;

        addRenderableWidget(Button.builder(hudToggleLabel("physics_debug", settings.showPhysicsDebugHud), button -> {
            settings.showPhysicsDebugHud = !settings.showPhysicsDebugHud;
            button.setMessage(hudToggleLabel("physics_debug", settings.showPhysicsDebugHud));
        }).bounds(left, y + 34, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(hudToggleLabel("ranking", settings.showRankingHud), button -> {
            settings.showRankingHud = !settings.showRankingHud;
            button.setMessage(hudToggleLabel("ranking", settings.showRankingHud));
        }).bounds(right, y + 34, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(hudToggleLabel("setup", settings.showSetupHud), button -> {
            settings.showSetupHud = !settings.showSetupHud;
            button.setMessage(hudToggleLabel("setup", settings.showSetupHud));
        }).bounds(left, y + 58, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(hudToggleLabel("driving", settings.showDrivingHud), button -> {
            settings.showDrivingHud = !settings.showDrivingHud;
            button.setMessage(hudToggleLabel("driving", settings.showDrivingHud));
        }).bounds(right, y + 58, buttonWidth, 20).build());

        int ersY = y + 118;
        addRenderableWidget(new ErsRangeSlider(x + 24, ersY + 28, PANEL_WIDTH - 48, 18, ErsRange.BALANCED));
        addRenderableWidget(new PowerBox(left, ersY + 54, true, PowerTarget.BALANCED_START));
        addRenderableWidget(new PowerBox(right, ersY + 54, false, PowerTarget.BALANCED_END));
        addRenderableWidget(new ErsRangeSlider(x + 24, ersY + 102, PANEL_WIDTH - 48, 18, ErsRange.HARVEST));
        addRenderableWidget(new PowerBox(left, ersY + 128, true, PowerTarget.HARVEST_START));
        addRenderableWidget(new PowerBox(right, ersY + 128, false, PowerTarget.HARVEST_END));
        addRenderableWidget(new BatterySlider(left, ersY + 174, PANEL_WIDTH - 32, 20));

        int controlsY = y + 340;
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.setup.wheel_setup"), button -> Minecraft.getInstance().setScreen(new WheelSetupScreen(this)))
            .bounds(left, controlsY + 24, PANEL_WIDTH - 32, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.setup.keybind_setup"), button -> Minecraft.getInstance().setScreen(new KeyBindsScreen(this, Minecraft.getInstance().options)))
            .bounds(left, controlsY + 48, PANEL_WIDTH - 32, 20)
            .build());

        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.setup.done"), button -> saveAndClose())
            .bounds(x + 106, y + 402, 76, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.setup.cancel"), button -> closeToParent())
            .bounds(x + 198, y + 402, 76, 20)
            .build());
        updateWidgetVisibility();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int x = (width - PANEL_WIDTH) / 2;
        int y = 42 - scrollOffset;
        graphics.fill(x, Math.max(8, y), x + PANEL_WIDTH, Math.min(height - 8, y + CONTENT_HEIGHT), 0xDD1F2328);
        fillPanel(graphics, x + 6, y + 22, y + 86, 0xFF2A3038);
        fillPanel(graphics, x + 6, y + 102, y + 310, 0xFF293443);
        fillPanel(graphics, x + 6, y + 326, y + 390, 0xFF2F3640);
        drawIfVisible(graphics, title, x + 10, y + 8, 0xFFE8EDF2);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.visual"), x + 12, y + 24, 0xFFC9D1D9);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.ers"), x + 12, y + 104, 0xFFC9D1D9);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.ers.balanced_range"), x + 24, y + 122, 0xFFE8EDF2);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.ers.harvest_range"), x + 24, y + 196, 0xFFE8EDF2);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.controls"), x + 12, y + 328, 0xFFC9D1D9);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderScrollbar(graphics, x + PANEL_WIDTH + 4);
    }

    private void fillPanel(GuiGraphics graphics, int x, int top, int bottom, int color) {
        int visibleTop = Math.max(8, top);
        int visibleBottom = Math.min(height - 8, bottom);
        if (visibleBottom > visibleTop) {
            graphics.fill(x, visibleTop, x + PANEL_WIDTH - 12, visibleBottom, color);
        }
    }

    private void drawIfVisible(GuiGraphics graphics, Component text, int x, int y, int color) {
        if (y >= 8 && y <= height - 18) {
            graphics.drawString(font, text, x, y, color, false);
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int x) {
        int trackTop = 42;
        int trackBottom = height - 42;
        int maxScroll = maxScroll();
        if (maxScroll <= 0 || trackBottom <= trackTop) {
            return;
        }
        int trackHeight = trackBottom - trackTop;
        int thumbHeight = Math.max(24, trackHeight * trackHeight / CONTENT_HEIGHT);
        int thumbY = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
        graphics.fill(x, trackTop, x + 4, trackBottom, 0x66000000);
        graphics.fill(x, thumbY, x + 4, thumbY + thumbHeight, 0xFF8B949E);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - (int) Math.round(scrollY * 24.0)));
        rebuildWidgets();
        return true;
    }

    private int maxScroll() {
        return Math.max(0, CONTENT_HEIGHT + 58 - height);
    }

    private void updateWidgetVisibility() {
        for (var widget : children()) {
            if (widget instanceof AbstractWidget abstractWidget) {
                abstractWidget.visible = abstractWidget.getY() >= 8 && abstractWidget.getY() <= height - 28;
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            closeToParent();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    private Component hudToggleLabel(String hud, boolean shown) {
        return Component.translatable(
            "screen.openwheelracing.setup.hud_toggle",
            Component.translatable("screen.openwheelracing.setup.hud." + hud),
            Component.translatable(shown ? "screen.openwheelracing.setup.shown" : "screen.openwheelracing.setup.hidden")
        );
    }

    private void saveAndClose() {
        WheelInputSettings.set(settings);
        WheelInputSettings.save(Minecraft.getInstance());
        closeToParent();
    }

    private void closeToParent() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum ErsRange {
        BALANCED(220, 360),
        HARVEST(220, 370);

        final int min;
        final int max;

        ErsRange(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }

    private enum PowerTarget {
        BALANCED_START,
        BALANCED_END,
        HARVEST_START,
        HARVEST_END
    }

    private class ErsRangeSlider extends AbstractWidget {
        private final ErsRange range;
        private boolean draggingLower;
        private boolean draggingUpper;

        private ErsRangeSlider(int x, int y, int width, int height, ErsRange range) {
            super(x, y, width, height, Component.empty());
            this.range = range;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int cy = getY() + height / 2;
            int lowerX = valueToX(lower());
            int upperX = valueToX(upper());
            graphics.fill(getX(), cy - 2, getX() + width, cy + 2, 0xFF3F464F);
            graphics.fill(lowerX, cy - 3, upperX, cy + 3, range == ErsRange.BALANCED ? 0xFF99DDFF : 0xFFFFD044);
            drawHandle(graphics, lowerX, cy, 0xFFE8EDF2);
            drawHandle(graphics, upperX, cy, 0xFFE8EDF2);
            graphics.drawString(font, lower() + " km/h", getX(), getY() + height + 2, 0xFFC9D1D9, false);
            String upperLabel = upper() + " km/h";
            graphics.drawString(font, upperLabel, getX() + width - font.width(upperLabel), getY() + height + 2, 0xFFC9D1D9, false);
        }

        private void drawHandle(GuiGraphics graphics, int x, int cy, int color) {
            graphics.fill(x - 3, cy - 7, x + 4, cy + 8, 0xFF000000);
            graphics.fill(x - 2, cy - 6, x + 3, cy + 7, color);
        }

        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!isMouseOver(event.x(), event.y())) {
                return false;
            }
            int lowerX = valueToX(lower());
            int upperX = valueToX(upper());
            draggingLower = Math.abs(event.x() - lowerX) <= Math.abs(event.x() - upperX);
            draggingUpper = !draggingLower;
            updateFromMouse(event.x());
            return true;
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            if (!draggingLower && !draggingUpper) {
                return false;
            }
            updateFromMouse(event.x());
            return true;
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            draggingLower = false;
            draggingUpper = false;
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        private void updateFromMouse(double mouseX) {
            int value = xToValue((int) Math.round(mouseX));
            if (draggingLower) {
                setLower(Math.min(value, upper() - 10));
            } else if (draggingUpper) {
                setUpper(Math.max(value, lower() + 10));
            }
        }

        private int valueToX(int value) {
            return getX() + (int) Math.round((value - range.min) / (double) (range.max - range.min) * width);
        }

        private int xToValue(int x) {
            double t = (x - getX()) / (double) width;
            return range.min + (int) Math.round(Math.max(0.0, Math.min(1.0, t)) * (range.max - range.min));
        }

        private int lower() {
            return range == ErsRange.BALANCED ? settings.ersBalancedClipStartKmh : settings.ersHarvestNegativeStartKmh;
        }

        private int upper() {
            return range == ErsRange.BALANCED ? settings.ersBalancedClipEndKmh : settings.ersHarvestNegativeFullKmh;
        }

        private void setLower(int value) {
            if (range == ErsRange.BALANCED) {
                settings.ersBalancedClipStartKmh = clampInt(value, range.min, range.max - 10);
            } else {
                settings.ersHarvestNegativeStartKmh = clampInt(value, range.min, range.max - 10);
            }
        }

        private void setUpper(int value) {
            if (range == ErsRange.BALANCED) {
                settings.ersBalancedClipEndKmh = clampInt(value, range.min + 10, range.max);
            } else {
                settings.ersHarvestNegativeFullKmh = clampInt(value, range.min + 10, range.max);
            }
        }
    }

    private class PowerBox extends EditBox {
        private final PowerTarget target;

        private PowerBox(int x, int y, boolean leftSide, PowerTarget target) {
            super(font, x, y, 160, 20, Component.empty());
            this.target = target;
            setMaxLength(4);
            setValue(Integer.toString(currentValue()));
            setResponder(value -> updatePower(value, target));
            setHint(Component.translatable(leftSide ? "screen.openwheelracing.setup.ers.power_left" : "screen.openwheelracing.setup.ers.power_right"));
        }

        private int currentValue() {
            return switch (target) {
                case BALANCED_START -> settings.ersBalancedStartPowerKw;
                case BALANCED_END -> settings.ersBalancedEndPowerKw;
                case HARVEST_START -> settings.ersHarvestStartPowerKw;
                case HARVEST_END -> settings.ersHarvestEndPowerKw;
            };
        }

        private void updatePower(String value, PowerTarget target) {
            if (value.isBlank()) {
                return;
            }
            try {
                int power = Integer.parseInt(value);
                switch (target) {
                    case BALANCED_START -> settings.ersBalancedStartPowerKw = clampInt(power, 0, 350);
                    case BALANCED_END -> settings.ersBalancedEndPowerKw = clampInt(power, 0, 350);
                    case HARVEST_START -> settings.ersHarvestStartPowerKw = clampInt(power, -250, 0);
                    case HARVEST_END -> settings.ersHarvestEndPowerKw = clampInt(power, -250, 0);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private class BatterySlider extends AbstractSliderButton {
        private BatterySlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (settings.ersCapacityMj - 2.0) / 10.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.openwheelracing.setup.ers.capacity", String.format(Locale.ROOT, "%.1f", settings.ersCapacityMj)));
        }

        @Override
        protected void applyValue() {
            settings.ersCapacityMj = Math.round((2.0 + value * 10.0) * 10.0) / 10.0;
        }
    }
}
