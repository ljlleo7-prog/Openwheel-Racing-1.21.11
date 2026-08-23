package com.openwheelracing.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.openwheelracing.client.input.KeyboardInputSettings;
import com.openwheelracing.client.input.KeyboardPedalResponse;
import com.openwheelracing.client.input.OWRKeyMappings;
import com.openwheelracing.client.input.WheelInputSettings;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class KeyboardSetupScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final double LOW_SPEED_LOCK_DEGREES = 34.0;
    private static final double HIGH_SPEED_LOCK_DEGREES = 2.45;
    private static final double LOW_SPEED_RACK_RATE_DEGREES = 120.0;
    private static final double HIGH_SPEED_RACK_RATE_DEGREES = 4.0;
    private static final double LOW_SPEED_CENTERING_RATE_DEGREES = 90.0;
    private static final double HIGH_SPEED_CENTERING_RATE_DEGREES = 180.0;
    private final Screen parent;
    private WheelInputSettings settings;
    private double lowSpeedPreviewDegrees;
    private double highSpeedPreviewDegrees;
    private float throttlePreview;
    private float brakePreview;

    public KeyboardSetupScreen(Screen parent) {
        super(Component.translatable("screen.openwheelracing.keyboard_setup.title"));
        this.parent = parent;
        this.settings = WheelInputSettings.copyOfCurrent();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int x = (width - PANEL_WIDTH) / 2;
        int y = 28;
        addRenderableWidget(new KeyboardSlider(x + 16, y + 34, 164, 20, SliderKind.LOW_SPEED_RATE));
        addRenderableWidget(new KeyboardSlider(x + 200, y + 34, 164, 20, SliderKind.HIGH_SPEED_RATE));
        addRenderableWidget(new KeyboardSlider(x + 16, y + 64, 164, 20, SliderKind.LOW_SPEED_CENTERING));
        addRenderableWidget(new KeyboardSlider(x + 200, y + 64, 164, 20, SliderKind.HIGH_SPEED_CENTERING));
        addRenderableWidget(new KeyboardSlider(x + 16, y + 94, 164, 20, SliderKind.LOW_SPEED_GAIN));
        addRenderableWidget(new KeyboardSlider(x + 200, y + 94, 164, 20, SliderKind.HIGH_SPEED_GAIN));
        addRenderableWidget(new KeyboardSlider(x + 108, y + 124, 164, 20, SliderKind.SPEED_CURVE));
        addRenderableWidget(new KeyboardSlider(x + 16, y + 154, 164, 20, SliderKind.THROTTLE_RESPONSE));
        addRenderableWidget(new KeyboardSlider(x + 200, y + 154, 164, 20, SliderKind.BRAKE_RESPONSE));
        addRenderableWidget(new KeyboardSlider(x + 108, y + 184, 164, 20, SliderKind.STABILITY_ASSIST));
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.keyboard_setup.reset_all"), button -> {
            settings.keyboard = KeyboardInputSettings.defaults();
            lowSpeedPreviewDegrees = 0.0;
            highSpeedPreviewDegrees = 0.0;
            throttlePreview = 0.0f;
            brakePreview = 0.0f;
            rebuildWidgets();
        }).bounds(x + 16, y + 284, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.keyboard_setup.save"), button -> saveAndClose())
            .bounds(x + 196, y + 284, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.keyboard_setup.cancel"), button -> closeToParent())
            .bounds(x + 288, y + 284, 76, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void tick() {
        KeyboardInputSettings keyboard = keyboardSettings();
        double target = (keyDown(OWRKeyMappings.STEER_RIGHT) ? 1.0 : 0.0) - (keyDown(OWRKeyMappings.STEER_LEFT) ? 1.0 : 0.0);
        lowSpeedPreviewDegrees = updatePreview(lowSpeedPreviewDegrees, target * LOW_SPEED_LOCK_DEGREES * keyboard.lowSpeedSteeringGain,
            LOW_SPEED_RACK_RATE_DEGREES * keyboard.lowSpeedSteeringRate, LOW_SPEED_CENTERING_RATE_DEGREES * keyboard.lowSpeedCenteringRate);
        highSpeedPreviewDegrees = updatePreview(highSpeedPreviewDegrees, target * HIGH_SPEED_LOCK_DEGREES * keyboard.highSpeedSteeringGain,
            HIGH_SPEED_RACK_RATE_DEGREES * keyboard.highSpeedSteeringRate, HIGH_SPEED_CENTERING_RATE_DEGREES * keyboard.highSpeedCenteringRate);
        throttlePreview = KeyboardPedalResponse.next(throttlePreview, keyDown(OWRKeyMappings.THROTTLE), 0.30f,
            keyboard.throttleRiseSeconds(), keyboard.throttleReleaseSeconds(), KeyboardPedalResponse.TICK_SECONDS);
        brakePreview = KeyboardPedalResponse.next(brakePreview, keyDown(OWRKeyMappings.BRAKE), 0.65f,
            keyboard.brakeRiseSeconds(), keyboard.brakeReleaseSeconds(), KeyboardPedalResponse.TICK_SECONDS);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int x = (width - PANEL_WIDTH) / 2;
        int y = 28;
        graphics.fill(x, y, x + PANEL_WIDTH, Math.min(height - 8, y + 314), 0xDD1F2328);
        graphics.fill(x + 6, y + 20, x + PANEL_WIDTH - 6, y + 212, 0xFF2A3038);
        graphics.fill(x + 6, y + 218, x + PANEL_WIDTH - 6, y + 276, 0xFF2F3640);
        graphics.drawString(font, title, x + 10, y + 6, 0xFFE8EDF2, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.keyboard_setup.response"), x + 12, y + 220, 0xFFC9D1D9, false);
        drawPreview(graphics, x + 98, y + 236);
        super.render(graphics, mouseX, mouseY, partialTick);
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

    private double updatePreview(double current, double target, double rackRate, double centeringRate) {
        boolean centering = Math.abs(target) < Math.abs(current) && Math.signum(target) != Math.signum(target - current);
        double rate = centering ? centeringRate : rackRate;
        double gain = 1.0 - Math.exp(-Math.toRadians(rate) * 0.05 / Math.max(Math.toRadians(0.25), Math.toRadians(Math.max(0.25, Math.abs(target)))));
        return current + (target - current) * gain;
    }

    private void drawPreview(GuiGraphics graphics, int x, int y) {
        drawBar(graphics, x, y, 170, throttlePreview, 0.0f, 1.0f, 0xFF7EE787, Component.translatable("screen.openwheelracing.keyboard_setup.preview_throttle"), String.format(Locale.ROOT, "%.0f%%", throttlePreview * 100.0f));
        drawBar(graphics, x, y + 12, 170, brakePreview, 0.0f, 1.0f, 0xFFFF7B72, Component.translatable("screen.openwheelracing.keyboard_setup.preview_brake"), String.format(Locale.ROOT, "%.0f%%", brakePreview * 100.0f));
        drawBar(graphics, x, y + 24, 170, (float) lowSpeedPreviewDegrees, (float) -LOW_SPEED_LOCK_DEGREES, (float) LOW_SPEED_LOCK_DEGREES, 0xFF58A6FF,
            Component.translatable("screen.openwheelracing.keyboard_setup.preview_low_steering"), String.format(Locale.ROOT, "%+.1f°", lowSpeedPreviewDegrees));
        drawBar(graphics, x, y + 36, 170, (float) highSpeedPreviewDegrees, (float) -HIGH_SPEED_LOCK_DEGREES, (float) HIGH_SPEED_LOCK_DEGREES, 0xFFD2A8FF,
            Component.translatable("screen.openwheelracing.keyboard_setup.preview_high_steering"), String.format(Locale.ROOT, "%+.2f°", highSpeedPreviewDegrees));
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int width, float value, float min, float max, int color, Component label, String valueText) {
        graphics.drawString(font, label, x - 84, y, 0xFFC9D1D9, false);
        graphics.fill(x, y + 2, x + width, y + 8, 0xFF15191F);
        int filled = Math.round(WheelInputSettings.clamp((value - min) / (max - min), 0.0f, 1.0f) * width);
        graphics.fill(x, y + 2, x + filled, y + 8, color);
        graphics.drawString(font, valueText, x + width + 6, y, 0xFFE8EDF2, false);
    }

    private void saveAndClose() {
        settings.keyboard = keyboardSettings();
        WheelInputSettings.set(settings);
        WheelInputSettings.save(Minecraft.getInstance());
        closeToParent();
    }

    private void closeToParent() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private KeyboardInputSettings keyboardSettings() {
        if (settings.keyboard == null) {
            settings.keyboard = KeyboardInputSettings.defaults();
        }
        return settings.keyboard.sanitized();
    }

    private static boolean keyDown(net.minecraft.client.KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key.getValue());
    }

    private enum SliderKind {
        LOW_SPEED_RATE(0.5f, 2.0f),
        HIGH_SPEED_RATE(0.5f, 2.0f),
        LOW_SPEED_CENTERING(0.5f, 2.0f),
        HIGH_SPEED_CENTERING(0.5f, 2.0f),
        LOW_SPEED_GAIN(0.7f, 1.3f),
        HIGH_SPEED_GAIN(0.5f, 1.3f),
        SPEED_CURVE(0.6f, 1.4f),
        THROTTLE_RESPONSE(0.5f, 2.0f),
        BRAKE_RESPONSE(0.5f, 2.0f),
        STABILITY_ASSIST(0.0f, 1.0f);

        private final float min;
        private final float max;

        SliderKind(float min, float max) {
            this.min = min;
            this.max = max;
        }
    }

    private class KeyboardSlider extends AbstractSliderButton {
        private final SliderKind kind;

        private KeyboardSlider(int x, int y, int width, int height, SliderKind kind) {
            super(x, y, width, height, Component.empty(), 0.0);
            this.kind = kind;
            value = (get(kind) - kind.min) / (kind.max - kind.min);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.openwheelracing.keyboard_setup.slider." + kind.name().toLowerCase(Locale.ROOT),
                String.format(Locale.ROOT, "%.0f%%", get(kind) * 100.0f)));
        }

        @Override
        protected void applyValue() {
            set(kind, (float) (kind.min + value * (kind.max - kind.min)));
        }

        private float get(SliderKind kind) {
            KeyboardInputSettings keyboard = keyboardSettings();
            return switch (kind) {
                case LOW_SPEED_RATE -> keyboard.lowSpeedSteeringRate;
                case HIGH_SPEED_RATE -> keyboard.highSpeedSteeringRate;
                case LOW_SPEED_CENTERING -> keyboard.lowSpeedCenteringRate;
                case HIGH_SPEED_CENTERING -> keyboard.highSpeedCenteringRate;
                case LOW_SPEED_GAIN -> keyboard.lowSpeedSteeringGain;
                case HIGH_SPEED_GAIN -> keyboard.highSpeedSteeringGain;
                case SPEED_CURVE -> keyboard.speedResponseCurve;
                case THROTTLE_RESPONSE -> keyboard.throttleResponse;
                case BRAKE_RESPONSE -> keyboard.brakeResponse;
                case STABILITY_ASSIST -> keyboard.stabilityAssistStrength;
            };
        }

        private void set(SliderKind kind, float value) {
            KeyboardInputSettings keyboard = keyboardSettings();
            switch (kind) {
                case LOW_SPEED_RATE -> keyboard.lowSpeedSteeringRate = value;
                case HIGH_SPEED_RATE -> keyboard.highSpeedSteeringRate = value;
                case LOW_SPEED_CENTERING -> keyboard.lowSpeedCenteringRate = value;
                case HIGH_SPEED_CENTERING -> keyboard.highSpeedCenteringRate = value;
                case LOW_SPEED_GAIN -> keyboard.lowSpeedSteeringGain = value;
                case HIGH_SPEED_GAIN -> keyboard.highSpeedSteeringGain = value;
                case SPEED_CURVE -> keyboard.speedResponseCurve = value;
                case THROTTLE_RESPONSE -> keyboard.throttleResponse = value;
                case BRAKE_RESPONSE -> keyboard.brakeResponse = value;
                case STABILITY_ASSIST -> keyboard.stabilityAssistStrength = value;
            }
            keyboard.sanitized();
        }
    }
}
