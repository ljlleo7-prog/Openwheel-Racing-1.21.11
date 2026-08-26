package com.openwheelracing.client.screen;

import com.openwheelracing.client.input.OWRClientInputHandler;
import com.openwheelracing.client.input.WheelInputSettings;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
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
    private static final int CONTENT_HEIGHT = 980;
    private static final int GRAPH_WIDTH = 156;
    private static final int GRAPH_HEIGHT = 74;
    private static final int GRAPH_TEMP_MIN = 50;
    private static final int GRAPH_TEMP_MAX = 140;
    private static final int TYRE_MU_COLOR = 0xFF99DDFF;
    private static final int TYRE_WEAR_COLOR = 0xFFFFDD66;
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
        addRenderableWidget(Button.builder(timingScopeLabel(), button -> {
            settings.allTimeLapTiming = !settings.allTimeLapTiming;
            button.setMessage(timingScopeLabel());
        }).bounds(right, y + 82, buttonWidth, 20).build());
        addRenderableWidget(new ShiftLightRangeSlider(x + 24, y + 118, PANEL_WIDTH - 48, 18));

        int ersY = y + 156;
        addRenderableWidget(new ErsRangeSlider(x + 24, ersY + 28, PANEL_WIDTH - 48, 18, ErsRange.BALANCED));
        addRenderableWidget(new PowerBox(left, ersY + 54, true, PowerTarget.BALANCED_START));
        addRenderableWidget(new PowerBox(right, ersY + 54, false, PowerTarget.BALANCED_END));
        addRenderableWidget(new ErsRangeSlider(x + 24, ersY + 102, PANEL_WIDTH - 48, 18, ErsRange.HARVEST));
        addRenderableWidget(new PowerBox(left, ersY + 128, true, PowerTarget.HARVEST_START));
        addRenderableWidget(new PowerBox(right, ersY + 128, false, PowerTarget.HARVEST_END));
        addRenderableWidget(new LicoSpeedSlider(left, ersY + 184, PANEL_WIDTH - 32, 20));
        addRenderableWidget(new LicoSteeringSlider(left, ersY + 214, 160, 20));
        addRenderableWidget(new LicoLateralGSlider(right, ersY + 214, 160, 20));
        addRenderableWidget(new PowerBox(left, ersY + 270, true, PowerTarget.LICO_HARVEST));
        addRenderableWidget(new PowerBox(right, ersY + 270, false, PowerTarget.LICO_BALANCED));
        addRenderableWidget(new PowerBox(left, ersY + 320, true, PowerTarget.LICO_ATTACK));
        addRenderableWidget(new BatterySlider(left, ersY + 364, PANEL_WIDTH - 32, 20));

        int controlsY = y + 702;
        addRenderableWidget(new TractionControlStrengthSlider(left, controlsY + 18, PANEL_WIDTH - 32, 20));
        addRenderableWidget(new AssistEnvelopeSlider(left, controlsY + 44, PANEL_WIDTH - 32, 20, AssistEnvelope.TC));
        addRenderableWidget(new AssistEnvelopeSlider(left, controlsY + 70, PANEL_WIDTH - 32, 20, AssistEnvelope.ABS));
        addRenderableWidget(new YawAdjustmentSlider(left, controlsY + 98, PANEL_WIDTH - 32, 20,
            YawAdjustment.BRAKING, settings.brakingYawAdjustment));
        addRenderableWidget(new YawAdjustmentSlider(left, controlsY + 124, PANEL_WIDTH - 32, 20,
            YawAdjustment.NEUTRAL, settings.neutralYawAdjustment));
        addRenderableWidget(new YawAdjustmentSlider(left, controlsY + 150, PANEL_WIDTH - 32, 20,
            YawAdjustment.THROTTLE, settings.throttleYawAdjustment));
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.setup.keyboard_setup"), button -> Minecraft.getInstance().setScreen(new KeyboardSetupScreen(this)))
            .bounds(left, controlsY + 178, PANEL_WIDTH - 32, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.setup.wheel_setup"), button -> Minecraft.getInstance().setScreen(new WheelSetupScreen(this)))
            .bounds(left, controlsY + 202, PANEL_WIDTH - 32, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.setup.keybind_setup"), button -> Minecraft.getInstance().setScreen(new KeyBindsScreen(this, Minecraft.getInstance().options)))
            .bounds(left, controlsY + 226, PANEL_WIDTH - 32, 20)
            .build());

        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.setup.done"), button -> saveAndClose())
            .bounds(x + 106, y + 952, 76, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.setup.cancel"), button -> closeToParent())
            .bounds(x + 198, y + 952, 76, 20)
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
        fillPanel(graphics, x + 6, y + 22, y + 148, 0xFF2A3038);
        fillPanel(graphics, x + 6, y + 140, y + 478, 0xFF293443);
        fillPanel(graphics, x + 6, y + 494, y + 672, 0xFF242D38);
        fillPanel(graphics, x + 6, y + 688, y + 934, 0xFF2F3640);
        drawIfVisible(graphics, title, x + 10, y + 8, 0xFFE8EDF2);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.visual"), x + 12, y + 24, 0xFFC9D1D9);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.shift_lights"), x + 24, y + 106, 0xFFE8EDF2);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.ers"), x + 12, y + 142, 0xFFC9D1D9);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.ers.balanced_range"), x + 24, y + 160, 0xFFE8EDF2);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.ers.harvest_range"), x + 24, y + 234, 0xFFE8EDF2);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.ers.lico"), x + 24, y + 310, 0xFFE8EDF2);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.ers.lico_thresholds"), x + 24, y + 328, 0xFFC9D1D9);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.ers.lico_power"), x + 24, y + 396, 0xFFC9D1D9);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.ers.lico_harvest"), x + 16, y + 420, 0xFFE8EDF2);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.ers.lico_balanced"), x + 204, y + 420, 0xFFE8EDF2);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.ers.lico_attack"), x + 16, y + 470, 0xFFE8EDF2);
        drawTyreThermalGraphs(graphics, x + 18, y + 516);
        drawIfVisible(graphics, Component.translatable("screen.openwheelracing.setup.controls"), x + 12, y + 690, 0xFFC9D1D9);
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

    private void drawTyreThermalGraphs(GuiGraphics graphics, int x, int y) {
        if (y + 152 < 8 || y > height - 8) {
            return;
        }
        int left = x;
        int right = x + 184;
        graphics.drawString(font, Component.translatable("screen.openwheelracing.setup.tyre_thermal"), x, y, 0xFFC9D1D9, false);
        drawTyreGraph(graphics, left, y + 18, Component.translatable("screen.openwheelracing.setup.tyre_mu_graph"), true);
        drawTyreGraph(graphics, right, y + 18, Component.translatable("screen.openwheelracing.setup.tyre_wear_graph"), false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.setup.tyre_graph_legend"), x, y + 110, 0xFF8B949E, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.setup.tyre_graph_note"), x, y + 124, 0xFF8B949E, false);
    }

    private void drawTyreGraph(GuiGraphics graphics, int x, int y, Component title, boolean muGraph) {
        int bottom = y + GRAPH_HEIGHT;
        graphics.fill(x, y, x + GRAPH_WIDTH, bottom, 0xCC101820);
        graphics.renderOutline(x, y, GRAPH_WIDTH, GRAPH_HEIGHT, 0xFF3F464F);
        graphics.drawString(font, title, x, y - 11, 0xFFE8EDF2, false);
        for (int i = 1; i < 4; i++) {
            int gy = y + i * GRAPH_HEIGHT / 4;
            graphics.hLine(x + 1, x + GRAPH_WIDTH - 2, gy, 0x333F464F);
        }
        int color = muGraph ? TYRE_MU_COLOR : TYRE_WEAR_COLOR;
        for (int compound = 0; compound < 5; compound++) {
            int minX = graphX(x, OpenwheelCarEntity.tyreWorkingTemperatureMin(compound));
            int maxX = graphX(x, OpenwheelCarEntity.tyreWorkingTemperatureMax(compound));
            graphics.fill(minX, y + 1, maxX, bottom - 1, compound == 2 ? 0x2234D058 : 0x111F6FEB);
        }
        for (int compound = 0; compound < 5; compound++) {
            int previousX = x;
            int previousY = graphY(y, sampleTyreGraph(compound, GRAPH_TEMP_MIN, muGraph), muGraph);
            for (int temperature = GRAPH_TEMP_MIN + 2; temperature <= GRAPH_TEMP_MAX; temperature += 2) {
                int nextX = graphX(x, temperature);
                int nextY = graphY(y, sampleTyreGraph(compound, temperature, muGraph), muGraph);
                drawLine(graphics, previousX, previousY, nextX, nextY, color);
                if (compound % 2 == 0) {
                    drawLine(graphics, previousX, previousY + 1, nextX, nextY + 1, color);
                }
                previousX = nextX;
                previousY = nextY;
            }
            graphics.drawString(font, "C" + (compound + 1), graphX(x, OpenwheelCarEntity.tyreWorkingTemperatureMin(compound)) + 1, y + 5 + compound * 9, color, false);
        }
        graphics.drawString(font, GRAPH_TEMP_MIN + "C", x, bottom + 3, 0xFF8B949E, false);
        String maxLabel = GRAPH_TEMP_MAX + "C";
        graphics.drawString(font, maxLabel, x + GRAPH_WIDTH - font.width(maxLabel), bottom + 3, 0xFF8B949E, false);
    }

    private double sampleTyreGraph(int compound, double temperature, boolean muGraph) {
        return muGraph
            ? OpenwheelCarEntity.tyreTemperatureMuMultiplier(compound, temperature)
            : OpenwheelCarEntity.tyreTemperatureWearMultiplier(compound, temperature);
    }

    private int graphX(int x, double temperature) {
        double t = (temperature - GRAPH_TEMP_MIN) / (double) (GRAPH_TEMP_MAX - GRAPH_TEMP_MIN);
        return x + (int) Math.round(Math.max(0.0, Math.min(1.0, t)) * (GRAPH_WIDTH - 1));
    }

    private int graphY(int y, double value, boolean muGraph) {
        double min = muGraph ? 0.60 : 1.0;
        double max = muGraph ? 1.05 : 2.3;
        double t = (value - min) / (max - min);
        return y + GRAPH_HEIGHT - 2 - (int) Math.round(Math.max(0.0, Math.min(1.0, t)) * (GRAPH_HEIGHT - 4));
    }

    private void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                return;
            }
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
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

    private Component timingScopeLabel() {
        return Component.translatable(settings.allTimeLapTiming
            ? "screen.openwheelracing.setup.timing_scope.all_time"
            : "screen.openwheelracing.setup.timing_scope.session");
    }

    private void saveAndClose() {
        WheelInputSettings.set(settings);
        WheelInputSettings.save(Minecraft.getInstance());
        OWRClientInputHandler.resetErsSync();
        OWRClientInputHandler.resetTimingScopeSync();
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

    private class TractionControlStrengthSlider extends AbstractSliderButton {
        private TractionControlStrengthSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), settings.tractionControlStrength);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.openwheelracing.setup.tc_strength", Math.round(settings.tractionControlStrength * 100.0f)));
        }

        @Override
        protected void applyValue() {
            settings.tractionControlStrength = (float) value;
        }
    }

    private enum AssistEnvelope {
        TC,
        ABS
    }

    private enum YawAdjustment {
        BRAKING,
        NEUTRAL,
        THROTTLE
    }

    private class YawAdjustmentSlider extends AbstractSliderButton {
        private final YawAdjustment adjustment;

        private YawAdjustmentSlider(int x, int y, int width, int height,
                                    YawAdjustment adjustment, float initialValue) {
            super(x, y, width, height, Component.empty(), initialValue / 1.5f);
            this.adjustment = adjustment;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.openwheelracing.setup.yaw_adjustment."
                + adjustment.name().toLowerCase(Locale.ROOT), Math.round(yawAdjustmentValue(adjustment) * 100.0f)));
        }

        @Override
        protected void applyValue() {
            float setting = (float) value * 1.5f;
            switch (adjustment) {
                case BRAKING -> settings.brakingYawAdjustment = setting;
                case NEUTRAL -> settings.neutralYawAdjustment = setting;
                case THROTTLE -> settings.throttleYawAdjustment = setting;
            }
        }

        private float yawAdjustmentValue(YawAdjustment target) {
            return switch (target) {
                case BRAKING -> settings.brakingYawAdjustment;
                case NEUTRAL -> settings.neutralYawAdjustment;
                case THROTTLE -> settings.throttleYawAdjustment;
            };
        }
    }

    private class AssistEnvelopeSlider extends AbstractSliderButton {
        private final AssistEnvelope envelope;

        private AssistEnvelopeSlider(int x, int y, int width, int height, AssistEnvelope envelope) {
            super(x, y, width, height, Component.empty(),
                ((envelope == AssistEnvelope.TC ? settings.tractionControlEnvelope : settings.absEnvelope) - 0.90f) / 0.20f);
            this.envelope = envelope;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            float setting = envelope == AssistEnvelope.TC ? settings.tractionControlEnvelope : settings.absEnvelope;
            setMessage(Component.translatable(envelope == AssistEnvelope.TC
                ? "screen.openwheelracing.setup.tc_envelope"
                : "screen.openwheelracing.setup.abs_envelope", Math.round(setting * 100.0f)));
        }

        @Override
        protected void applyValue() {
            float setting = 0.90f + (float) value * 0.20f;
            if (envelope == AssistEnvelope.TC) {
                settings.tractionControlEnvelope = setting;
            } else {
                settings.absEnvelope = setting;
            }
        }
    }

    private class ShiftLightRangeSlider extends AbstractWidget {
        private static final int MIN_RPM = 5_000;
        private static final int MAX_RPM = 15_000;
        private static final int MIN_RANGE_RPM = 500;
        private boolean draggingLower;
        private boolean draggingUpper;

        private ShiftLightRangeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty());
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int cy = getY() + height / 2;
            int lowerX = valueToX(settings.shiftLightStartRpm);
            int upperX = valueToX(settings.shiftLightFullRpm);
            graphics.fill(getX(), cy - 2, getX() + width, cy + 2, 0xFF3F464F);
            graphics.fill(lowerX, cy - 3, upperX, cy + 3, 0xFFD65CFF);
            drawHandle(graphics, lowerX, cy, 0xFF34D058);
            drawHandle(graphics, upperX, cy, 0xFFD65CFF);
            graphics.drawString(font, Component.translatable("screen.openwheelracing.setup.shift_lights.start", settings.shiftLightStartRpm), getX(), getY() + height + 2, 0xFFC9D1D9, false);
            String upperLabel = Component.translatable("screen.openwheelracing.setup.shift_lights.full", settings.shiftLightFullRpm).getString();
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
            int lowerX = valueToX(settings.shiftLightStartRpm);
            int upperX = valueToX(settings.shiftLightFullRpm);
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
                settings.shiftLightStartRpm = clampInt(Math.min(value, settings.shiftLightFullRpm - MIN_RANGE_RPM), MIN_RPM, MAX_RPM - MIN_RANGE_RPM);
            } else if (draggingUpper) {
                settings.shiftLightFullRpm = clampInt(Math.max(value, settings.shiftLightStartRpm + MIN_RANGE_RPM), MIN_RPM + MIN_RANGE_RPM, MAX_RPM);
            }
        }

        private int valueToX(int value) {
            return getX() + (int) Math.round((value - MIN_RPM) / (double) (MAX_RPM - MIN_RPM) * width);
        }

        private int xToValue(int x) {
            double t = (x - getX()) / (double) width;
            return MIN_RPM + (int) Math.round(Math.max(0.0, Math.min(1.0, t)) * (MAX_RPM - MIN_RPM) / 100.0) * 100;
        }
    }

    private enum PowerTarget {
        BALANCED_START,
        BALANCED_END,
        HARVEST_START,
        HARVEST_END,
        LICO_HARVEST,
        LICO_BALANCED,
        LICO_ATTACK
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
                case LICO_HARVEST -> settings.ersLicoHarvestPowerKw;
                case LICO_BALANCED -> settings.ersLicoBalancedPowerKw;
                case LICO_ATTACK -> settings.ersLicoAttackPowerKw;
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
                    case HARVEST_START -> settings.ersHarvestStartPowerKw = clampInt(power, -350, 0);
                    case HARVEST_END -> settings.ersHarvestEndPowerKw = clampInt(power, -350, 0);
                    case LICO_HARVEST -> settings.ersLicoHarvestPowerKw = clampInt(power, -350, 0);
                    case LICO_BALANCED -> settings.ersLicoBalancedPowerKw = clampInt(power, -350, 0);
                    case LICO_ATTACK -> settings.ersLicoAttackPowerKw = clampInt(power, -350, 0);
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

    private class LicoSpeedSlider extends AbstractSliderButton {
        private LicoSpeedSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (settings.ersLicoSpeedThresholdKmh - 180) / 180.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.openwheelracing.setup.ers.lico_speed", settings.ersLicoSpeedThresholdKmh));
        }

        @Override
        protected void applyValue() {
            settings.ersLicoSpeedThresholdKmh = 180 + (int) Math.round(value * 180.0);
        }
    }

    private class LicoSteeringSlider extends AbstractSliderButton {
        private LicoSteeringSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (settings.ersLicoSteeringThresholdDegrees - 0.2) / 7.8);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.openwheelracing.setup.ers.lico_steer", String.format(Locale.ROOT, "%.1f", settings.ersLicoSteeringThresholdDegrees)));
        }

        @Override
        protected void applyValue() {
            settings.ersLicoSteeringThresholdDegrees = Math.round((0.2 + value * 7.8) * 10.0) / 10.0;
        }
    }

    private class LicoLateralGSlider extends AbstractSliderButton {
        private LicoLateralGSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (settings.ersLicoLateralGThreshold - 0.05) / 0.95);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.openwheelracing.setup.ers.lico_lateral_g", String.format(Locale.ROOT, "%.2f", settings.ersLicoLateralGThreshold)));
        }

        @Override
        protected void applyValue() {
            settings.ersLicoLateralGThreshold = Math.round((0.05 + value * 0.95) * 100.0) / 100.0;
        }
    }
}
