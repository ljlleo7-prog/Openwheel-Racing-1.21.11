package com.openwheelracing.client.screen;

import com.openwheelracing.client.input.WheelInputManager;
import com.openwheelracing.client.input.WheelInputSettings;
import com.openwheelracing.content.entity.VehiclePhysics;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class WheelSetupScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private final Screen parent;
    private WheelInputSettings settings;
    private WheelInputSettings.AxisRole selectedAxis = WheelInputSettings.AxisRole.STEERING;
    private WheelInputSettings.AxisRole capturingAxis;
    private WheelInputSettings.ButtonRole capturingButton;
    private float[] axisBaseline = new float[0];
    private Button deviceButton;
    private Button enabledButton;
    private Button combinedPedalsButton;
    private Button selectedAxisButton;
    private Button captureAxisButton;
    private Button captureButtonButton;

    public WheelSetupScreen(Screen parent) {
        super(Component.translatable("screen.openwheelracing.wheel_setup.title"));
        this.parent = parent;
        this.settings = WheelInputSettings.copyOfCurrent();
    }

    @Override
    protected void init() {
        clearWidgets();
        int x = (width - PANEL_WIDTH) / 2;
        int y = 28;
        deviceButton = addRenderableWidget(Button.builder(deviceLabel(), button -> cycleDevice())
            .bounds(x + 12, y + 20, 174, 20)
            .build());
        enabledButton = addRenderableWidget(Button.builder(enabledLabel(), button -> {
            settings.enabled = !settings.enabled;
            updateButtons();
        }).bounds(x + 194, y + 20, 82, 20).build());
        combinedPedalsButton = addRenderableWidget(Button.builder(combinedPedalsLabel(), button -> {
            settings.combinedPedals = !settings.combinedPedals;
            updateButtons();
        }).bounds(x + 284, y + 20, 84, 20).build());

        selectedAxisButton = addRenderableWidget(Button.builder(selectedAxisLabel(), button -> cycleAxis())
            .bounds(x + 12, y + 52, 118, 20)
            .build());
        captureAxisButton = addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.wheel_setup.detect_axis"), button -> beginAxisCapture())
            .bounds(x + 138, y + 52, 86, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.wheel_setup.set_min"), button -> captureCalibration(CalibrationPoint.MIN))
            .bounds(x + 232, y + 52, 42, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.wheel_setup.set_rest"), button -> captureCalibration(CalibrationPoint.REST))
            .bounds(x + 280, y + 52, 42, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.wheel_setup.set_max"), button -> captureCalibration(CalibrationPoint.MAX))
            .bounds(x + 326, y + 52, 42, 20)
            .build());

        addRenderableWidget(new AxisSlider(x + 12, y + 84, 112, 20, SliderKind.DEADZONE));
        addRenderableWidget(new AxisSlider(x + 134, y + 84, 112, 20, SliderKind.SENSITIVITY));
        addRenderableWidget(new AxisSlider(x + 256, y + 84, 112, 20, SliderKind.LINEARITY));
        addRenderableWidget(Button.builder(invertLabel(), button -> {
            WheelInputSettings.AxisBinding binding = selectedBinding();
            binding.invert = !binding.invert;
            rebuildWidgets();
        }).bounds(x + 12, y + 112, 112, 20).build());

        int buttonY = y + 144;
        WheelInputSettings.ButtonRole[] roles = WheelInputSettings.ButtonRole.values();
        for (int i = 0; i < roles.length; i++) {
            WheelInputSettings.ButtonRole role = roles[i];
            int row = i / 2;
            int col = i % 2;
            addRenderableWidget(Button.builder(buttonBindingLabel(role), button -> beginButtonCapture(role))
                .bounds(x + 12 + col * 178, buttonY + row * 24, 168, 20)
                .build());
        }

        int bottomY = Math.min(height - 30, y + 236);
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.wheel_setup.reset_axis"), button -> resetSelectedAxis())
            .bounds(x + 12, bottomY, 82, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.wheel_setup.reset_all"), button -> {
            settings = WheelInputSettings.defaults();
            rebuildWidgets();
        }).bounds(x + 102, bottomY, 82, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.wheel_setup.save"), button -> saveAndClose())
            .bounds(x + 208, bottomY, 76, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.wheel_setup.cancel"), button -> closeToParent())
            .bounds(x + 292, bottomY, 76, 20)
            .build());
        updateButtons();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // skip blur — parent pause screen already blurred; a second blur-per-frame crashes
    }

    @Override
    public void tick() {
        if (capturingAxis != null && selectedDeviceId() >= 0) {
            int axis = WheelInputManager.detectMovedAxis(selectedDeviceId(), axisBaseline, 0.25f);
            if (axis >= 0) {
                WheelInputSettings.AxisBinding binding = settings.axis(capturingAxis).copy();
                binding.axis = axis;
                binding.rest = WheelInputManager.rawAxis(selectedDeviceId(), axis);
                settings.setAxis(capturingAxis, binding);
                selectedAxis = capturingAxis;
                capturingAxis = null;
                rebuildWidgets();
            }
        }
        if (capturingButton != null && selectedDeviceId() >= 0) {
            int button = WheelInputManager.detectPressedButton(selectedDeviceId());
            if (button >= 0) {
                settings.setButton(capturingButton, button);
                capturingButton = null;
                rebuildWidgets();
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int x = (width - PANEL_WIDTH) / 2;
        int y = 28;
        graphics.fill(x, y, x + PANEL_WIDTH, Math.min(height - 8, y + 262), 0xDD1F2328);
        graphics.fill(x + 6, y + 16, x + PANEL_WIDTH - 6, y + 134, 0xFF2A3038);
        graphics.fill(x + 6, y + 138, x + PANEL_WIDTH - 6, Math.min(height - 36, y + 228), 0xFF2F3640);
        graphics.drawString(font, title, x + 10, y + 6, 0xFFE8EDF2, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.wheel_setup.device"), x + 12, y + 42, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.wheel_setup.live_preview"), x + 134, y + 116, 0xFFC9D1D9, false);
        drawPreview(graphics, x + 210, y + 112);
        if (capturingAxis != null) {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.wheel_setup.move_axis"), x + 12, y + 250, 0xFFFFD166, false);
        } else if (capturingButton != null) {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.wheel_setup.press_button"), x + 12, y + 250, 0xFFFFD166, false);
        }
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

    private void cycleDevice() {
        List<WheelInputManager.Device> devices = WheelInputManager.devices();
        if (devices.isEmpty()) {
            settings.selectedJoystickId = -1;
            settings.selectedJoystickName = "";
            updateButtons();
            return;
        }
        int current = -1;
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).id() == settings.selectedJoystickId) {
                current = i;
                break;
            }
        }
        WheelInputManager.Device device = devices.get((current + 1) % devices.size());
        settings.selectedJoystickId = device.id();
        settings.selectedJoystickName = device.name();
        settings.enabled = true;
        updateButtons();
    }

    private void cycleAxis() {
        WheelInputSettings.AxisRole[] roles = WheelInputSettings.AxisRole.values();
        selectedAxis = roles[(selectedAxis.ordinal() + 1) % roles.length];
        rebuildWidgets();
    }

    private void beginAxisCapture() {
        int joystickId = selectedDeviceId();
        if (joystickId < 0) {
            return;
        }
        capturingAxis = selectedAxis;
        capturingButton = null;
        axisBaseline = WheelInputManager.axisSnapshot(joystickId);
        updateButtons();
    }

    private void beginButtonCapture(WheelInputSettings.ButtonRole role) {
        if (selectedDeviceId() < 0) {
            return;
        }
        capturingButton = role;
        capturingAxis = null;
        updateButtons();
    }

    private void captureCalibration(CalibrationPoint point) {
        int joystickId = selectedDeviceId();
        if (joystickId < 0) {
            return;
        }
        WheelInputSettings.AxisBinding binding = selectedBinding();
        float raw = WheelInputManager.rawAxis(joystickId, binding.axis);
        switch (point) {
            case MIN -> binding.min = raw;
            case REST -> binding.rest = raw;
            case MAX -> binding.max = raw;
        }
        rebuildWidgets();
    }

    private void resetSelectedAxis() {
        WheelInputSettings defaults = WheelInputSettings.defaults();
        settings.setAxis(selectedAxis, defaults.axis(selectedAxis).copy());
        rebuildWidgets();
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

    private void updateButtons() {
        if (deviceButton != null) {
            deviceButton.setMessage(deviceLabel());
        }
        if (enabledButton != null) {
            enabledButton.setMessage(enabledLabel());
        }
        if (combinedPedalsButton != null) {
            combinedPedalsButton.setMessage(combinedPedalsLabel());
        }
        if (selectedAxisButton != null) {
            selectedAxisButton.setMessage(selectedAxisLabel());
        }
        if (captureAxisButton != null) {
            captureAxisButton.setMessage(capturingAxis == null ? Component.translatable("screen.openwheelracing.wheel_setup.detect_axis") : Component.translatable("screen.openwheelracing.wheel_setup.listening"));
        }
    }

    private Component deviceLabel() {
        List<WheelInputManager.Device> devices = WheelInputManager.devices();
        if (devices.isEmpty()) {
            return Component.translatable("screen.openwheelracing.wheel_setup.no_device");
        }
        WheelInputManager.Device selected = WheelInputManager.selectedDevice(settings);
        if (selected == null) {
            return Component.translatable("screen.openwheelracing.wheel_setup.select_device");
        }
        return Component.literal(selected.name());
    }

    private Component enabledLabel() {
        return Component.translatable(settings.enabled ? "screen.openwheelracing.wheel_setup.enabled" : "screen.openwheelracing.wheel_setup.disabled");
    }

    private Component combinedPedalsLabel() {
        return Component.translatable(settings.combinedPedals ? "screen.openwheelracing.wheel_setup.combined_pedals" : "screen.openwheelracing.wheel_setup.split_pedals");
    }

    private Component selectedAxisLabel() {
        return Component.translatable("screen.openwheelracing.wheel_setup.axis", axisName(selectedAxis), selectedBinding().axis);
    }

    private Component invertLabel() {
        return Component.translatable(selectedBinding().invert ? "screen.openwheelracing.wheel_setup.inverted" : "screen.openwheelracing.wheel_setup.normal");
    }

    private Component buttonBindingLabel(WheelInputSettings.ButtonRole role) {
        if (capturingButton == role) {
            return Component.translatable("screen.openwheelracing.wheel_setup.listening");
        }
        int button = settings.button(role);
        return Component.translatable("screen.openwheelracing.wheel_setup.button", buttonName(role), button < 0 ? "-" : Integer.toString(button));
    }

    private Component buttonName(WheelInputSettings.ButtonRole role) {
        return Component.translatable("screen.openwheelracing.wheel_setup.button." + role.name().toLowerCase(Locale.ROOT));
    }

    private Component axisName(WheelInputSettings.AxisRole role) {
        return Component.translatable("screen.openwheelracing.wheel_setup.axis." + role.name().toLowerCase(Locale.ROOT));
    }

    private WheelInputSettings.AxisBinding selectedBinding() {
        return settings.axis(selectedAxis);
    }

    private int selectedDeviceId() {
        WheelInputManager.Device device = WheelInputManager.selectedDevice(settings);
        return device == null ? -1 : device.id();
    }

    private void drawPreview(GuiGraphics graphics, int x, int y) {
        WheelInputManager.Output output = WheelInputManager.poll(settings);
        double steeringDegrees = VehiclePhysics.steeringCommandDegrees(output.steering(),
            Math.toRadians(VehiclePhysics.PROTOTYPE_MECHANICAL_STEERING_LOCK_DEGREES));
        drawBar(graphics, x, y, 140, output.steering(), -1.0f, 1.0f, 0xFF58A6FF,
            Component.translatable("screen.openwheelracing.wheel_setup.preview_steering"),
            String.format(Locale.ROOT, "%+.1f°", steeringDegrees));
        drawBar(graphics, x, y + 12, 140, output.throttle(), 0.0f, 1.0f, 0xFF7EE787,
            Component.translatable("screen.openwheelracing.wheel_setup.preview_throttle"), null);
        drawBar(graphics, x, y + 24, 140, output.brake(), 0.0f, 1.0f, 0xFFFF7B72,
            Component.translatable("screen.openwheelracing.wheel_setup.preview_brake"), null);
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int width, float value, float min, float max,
                         int color, Component label, String displayedValue) {
        graphics.drawString(font, label, x - 76, y, 0xFFC9D1D9, false);
        graphics.fill(x, y + 2, x + width, y + 8, 0xFF15191F);
        float normalized = (value - min) / (max - min);
        int filled = Math.round(WheelInputSettings.clamp(normalized, 0.0f, 1.0f) * width);
        graphics.fill(x, y + 2, x + filled, y + 8, color);
        graphics.drawString(font, displayedValue == null ? String.format(Locale.ROOT, "%.2f", value) : displayedValue,
            x + width + 6, y, 0xFFE8EDF2, false);
    }

    private enum CalibrationPoint {
        MIN,
        REST,
        MAX
    }

    private enum SliderKind {
        DEADZONE,
        SENSITIVITY,
        LINEARITY
    }

    private class AxisSlider extends AbstractSliderButton {
        private final SliderKind kind;

        private AxisSlider(int x, int y, int width, int height, SliderKind kind) {
            super(x, y, width, height, Component.empty(), 0.0);
            this.kind = kind;
            value = initialValue(kind);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            WheelInputSettings.AxisBinding binding = selectedBinding();
            float shown = switch (kind) {
                case DEADZONE -> binding.deadzone;
                case SENSITIVITY -> binding.sensitivity;
                case LINEARITY -> binding.linearity;
            };
            setMessage(Component.translatable("screen.openwheelracing.wheel_setup.slider." + kind.name().toLowerCase(Locale.ROOT), String.format(Locale.ROOT, "%.2f", shown)));
        }

        @Override
        protected void applyValue() {
            WheelInputSettings.AxisBinding binding = selectedBinding();
            switch (kind) {
                case DEADZONE -> binding.deadzone = (float) (value * 0.4);
                case SENSITIVITY -> binding.sensitivity = (float) (0.1 + value * 2.9);
                case LINEARITY -> binding.linearity = (float) (0.25 + value * 2.75);
            }
        }

        private double initialValue(SliderKind kind) {
            WheelInputSettings.AxisBinding binding = selectedBinding();
            return switch (kind) {
                case DEADZONE -> binding.deadzone / 0.4;
                case SENSITIVITY -> (binding.sensitivity - 0.1) / 2.9;
                case LINEARITY -> (binding.linearity - 0.25) / 2.75;
            };
        }
    }
}
