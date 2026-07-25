package com.openwheelracing.client.input;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

public class WheelInputSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "openwheelracing-wheel.json";
    private static final int CURRENT_VERSION = 2;
    private static WheelInputSettings instance = defaults();

    public int version = CURRENT_VERSION;
    public boolean enabled;
    public int selectedJoystickId = -1;
    public String selectedJoystickName = "";
    public boolean combinedPedals;
    public Boolean showDrivingHud;
    public Boolean showSetupHud;
    public Boolean showRankingHud;
    public Boolean showPhysicsDebugHud;
    public Integer ersBalancedClipStartKmh;
    public Integer ersBalancedClipEndKmh;
    public Integer ersHarvestNegativeStartKmh;
    public Integer ersHarvestNegativeFullKmh;
    public Integer ersBalancedStartPowerKw;
    public Integer ersBalancedEndPowerKw;
    public Integer ersHarvestStartPowerKw;
    public Integer ersHarvestEndPowerKw;
    public Integer ersLicoSpeedThresholdKmh;
    public Double ersLicoSteeringThresholdDegrees;
    public Double ersLicoLateralGThreshold;
    public Integer ersLicoHarvestPowerKw;
    public Integer ersLicoBalancedPowerKw;
    public Integer ersLicoAttackPowerKw;
    public Double ersCapacityMj;
    public AxisBinding steering = new AxisBinding(-1, false, -1.0f, 1.0f, 0.0f, 0.08f, 1.0f, 1.0f);
    public AxisBinding throttle = new AxisBinding(-1, true, -1.0f, 1.0f, 1.0f, 0.03f, 1.0f, 1.0f);
    public AxisBinding brake = new AxisBinding(-1, true, -1.0f, 1.0f, 1.0f, 0.03f, 1.0f, 1.0f);
    public AxisBinding combinedPedal = new AxisBinding(-1, false, -1.0f, 1.0f, 0.0f, 0.03f, 1.0f, 1.0f);
    public Map<ButtonRole, Integer> buttons = new EnumMap<>(ButtonRole.class);

    public static WheelInputSettings get() {
        return instance;
    }

    public static void set(WheelInputSettings settings) {
        instance = settings == null ? defaults() : settings.sanitized();
    }

    public static void load(Minecraft minecraft) {
        Path path = configPath(minecraft);
        if (!Files.isRegularFile(path)) {
            instance = defaults();
            return;
        }
        try {
            WheelInputSettings loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), WheelInputSettings.class);
            if (loaded != null && loaded.version < CURRENT_VERSION) {
                loaded.clearAxisBindings();
            }
            instance = loaded == null ? defaults() : loaded.sanitized();
        } catch (IOException | JsonSyntaxException ignored) {
            instance = defaults();
        }
    }

    public static void save(Minecraft minecraft) {
        Path path = configPath(minecraft);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(instance.sanitized()), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static WheelInputSettings copyOfCurrent() {
        return GSON.fromJson(GSON.toJson(instance), WheelInputSettings.class).sanitized();
    }

    public WheelInputSettings copy() {
        return GSON.fromJson(GSON.toJson(this), WheelInputSettings.class).sanitized();
    }

    public AxisBinding axis(AxisRole role) {
        return switch (role) {
            case STEERING -> steering;
            case THROTTLE -> throttle;
            case BRAKE -> brake;
            case COMBINED_PEDALS -> combinedPedal;
        };
    }

    public void setAxis(AxisRole role, AxisBinding binding) {
        switch (role) {
            case STEERING -> steering = binding;
            case THROTTLE -> throttle = binding;
            case BRAKE -> brake = binding;
            case COMBINED_PEDALS -> combinedPedal = binding;
        }
    }

    public int button(ButtonRole role) {
        return buttons.getOrDefault(role, -1);
    }

    public void setButton(ButtonRole role, int button) {
        if (button < 0) {
            buttons.remove(role);
        } else {
            buttons.put(role, button);
        }
    }

    public WheelInputSettings sanitized() {
        version = CURRENT_VERSION;
        if (selectedJoystickName == null) {
            selectedJoystickName = "";
        }
        if (steering == null) {
            steering = defaults().steering;
        }
        if (throttle == null) {
            throttle = defaults().throttle;
        }
        if (brake == null) {
            brake = defaults().brake;
        }
        if (combinedPedal == null) {
            combinedPedal = defaults().combinedPedal;
        }
        if (showDrivingHud == null) {
            showDrivingHud = true;
        }
        if (showSetupHud == null) {
            showSetupHud = true;
        }
        if (showRankingHud == null) {
            showRankingHud = true;
        }
        if (showPhysicsDebugHud == null) {
            showPhysicsDebugHud = true;
        }
        ersBalancedClipStartKmh = clampInt(ersBalancedClipStartKmh == null ? 260 : ersBalancedClipStartKmh, 220, 350);
        ersBalancedClipEndKmh = clampInt(ersBalancedClipEndKmh == null ? 315 : ersBalancedClipEndKmh, 230, 360);
        if (ersBalancedClipEndKmh < ersBalancedClipStartKmh + 10) {
            ersBalancedClipEndKmh = Math.min(360, ersBalancedClipStartKmh + 10);
        }
        ersHarvestNegativeStartKmh = clampInt(ersHarvestNegativeStartKmh == null ? 260 : ersHarvestNegativeStartKmh, 220, 360);
        ersHarvestNegativeFullKmh = clampInt(ersHarvestNegativeFullKmh == null ? 320 : ersHarvestNegativeFullKmh, 230, 370);
        if (ersHarvestNegativeFullKmh < ersHarvestNegativeStartKmh + 10) {
            ersHarvestNegativeFullKmh = Math.min(370, ersHarvestNegativeStartKmh + 10);
        }
        ersBalancedStartPowerKw = clampInt(ersBalancedStartPowerKw == null ? 200 : ersBalancedStartPowerKw, 0, 350);
        ersBalancedEndPowerKw = clampInt(ersBalancedEndPowerKw == null ? 0 : ersBalancedEndPowerKw, 0, 350);
        ersHarvestStartPowerKw = clampInt(ersHarvestStartPowerKw == null ? 0 : ersHarvestStartPowerKw, -350, 0);
        ersHarvestEndPowerKw = clampInt(ersHarvestEndPowerKw == null ? -110 : ersHarvestEndPowerKw, -350, 0);
        ersLicoSpeedThresholdKmh = clampInt(ersLicoSpeedThresholdKmh == null ? 260 : ersLicoSpeedThresholdKmh, 180, 360);
        ersLicoSteeringThresholdDegrees = clampDouble(ersLicoSteeringThresholdDegrees == null ? 1.8 : ersLicoSteeringThresholdDegrees, 0.2, 8.0);
        ersLicoLateralGThreshold = clampDouble(ersLicoLateralGThreshold == null ? 0.28 : ersLicoLateralGThreshold, 0.05, 1.0);
        ersLicoHarvestPowerKw = clampInt(ersLicoHarvestPowerKw == null ? -350 : ersLicoHarvestPowerKw, -350, 0);
        ersLicoBalancedPowerKw = clampInt(ersLicoBalancedPowerKw == null ? -180 : ersLicoBalancedPowerKw, -350, 0);
        ersLicoAttackPowerKw = clampInt(ersLicoAttackPowerKw == null ? 0 : ersLicoAttackPowerKw, -350, 0);
        ersCapacityMj = clampDouble(ersCapacityMj == null ? 4.0 : ersCapacityMj, 2.0, 12.0);
        EnumMap<ButtonRole, Integer> sanitizedButtons = new EnumMap<>(ButtonRole.class);
        if (buttons != null) {
            for (ButtonRole role : ButtonRole.values()) {
                Integer button = buttons.get(role);
                if (button != null && button >= 0) {
                    sanitizedButtons.put(role, button);
                }
            }
        }
        buttons = sanitizedButtons;
        steering = steering.sanitized(false);
        throttle = throttle.sanitized(true);
        brake = brake.sanitized(true);
        combinedPedal = combinedPedal.sanitized(false);
        return this;
    }

    public static WheelInputSettings defaults() {
        WheelInputSettings settings = new WheelInputSettings();
        settings.version = CURRENT_VERSION;
        settings.showDrivingHud = true;
        settings.showSetupHud = true;
        settings.showRankingHud = true;
        settings.showPhysicsDebugHud = true;
        settings.ersBalancedClipStartKmh = 260;
        settings.ersBalancedClipEndKmh = 315;
        settings.ersHarvestNegativeStartKmh = 260;
        settings.ersHarvestNegativeFullKmh = 320;
        settings.ersBalancedStartPowerKw = 200;
        settings.ersBalancedEndPowerKw = 0;
        settings.ersHarvestStartPowerKw = 0;
        settings.ersHarvestEndPowerKw = -110;
        settings.ersLicoSpeedThresholdKmh = 260;
        settings.ersLicoSteeringThresholdDegrees = 1.8;
        settings.ersLicoLateralGThreshold = 0.28;
        settings.ersLicoHarvestPowerKw = -350;
        settings.ersLicoBalancedPowerKw = -180;
        settings.ersLicoAttackPowerKw = 0;
        settings.ersCapacityMj = 4.0;
        settings.buttons = new EnumMap<>(ButtonRole.class);
        return settings;
    }

    private void clearAxisBindings() {
        steering.axis = -1;
        throttle.axis = -1;
        brake.axis = -1;
        combinedPedal.axis = -1;
    }

    private static Path configPath(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve("config").resolve(CONFIG_FILE);
    }

    public enum AxisRole {
        STEERING,
        THROTTLE,
        BRAKE,
        COMBINED_PEDALS
    }

    public enum ButtonRole {
        SHIFT_UP,
        SHIFT_DOWN,
        TOGGLE_DRS,
        TOGGLE_ABS,
        TOGGLE_TC,
        EXIT_CAR,
        MOUNT_CAR
    }

    public static class AxisBinding {
        public int axis;
        public boolean invert;
        public float min;
        public float max;
        public float rest;
        public float deadzone;
        public float sensitivity;
        public float linearity;

        public AxisBinding() {
        }

        public AxisBinding(int axis, boolean invert, float min, float max, float rest, float deadzone, float sensitivity, float linearity) {
            this.axis = axis;
            this.invert = invert;
            this.min = min;
            this.max = max;
            this.rest = rest;
            this.deadzone = deadzone;
            this.sensitivity = sensitivity;
            this.linearity = linearity;
        }

        public AxisBinding copy() {
            return new AxisBinding(axis, invert, min, max, rest, deadzone, sensitivity, linearity);
        }

        public AxisBinding sanitized(boolean pedal) {
            if (axis < -1) {
                axis = -1;
            }
            if (!Float.isFinite(min)) {
                min = -1.0f;
            }
            if (!Float.isFinite(max)) {
                max = 1.0f;
            }
            if (!Float.isFinite(rest)) {
                rest = pedal ? 1.0f : 0.0f;
            }
            if (Math.abs(max - min) < 0.001f) {
                min = -1.0f;
                max = 1.0f;
            }
            deadzone = clamp(Float.isFinite(deadzone) ? deadzone : 0.05f, 0.0f, 0.6f);
            sensitivity = clamp(Float.isFinite(sensitivity) ? sensitivity : 1.0f, 0.1f, 3.0f);
            linearity = clamp(Float.isFinite(linearity) ? linearity : 1.0f, 0.25f, 3.0f);
            rest = clamp(rest, Math.min(min, max), Math.max(min, max));
            return this;
        }
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
