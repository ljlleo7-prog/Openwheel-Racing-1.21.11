package com.openwheelracing.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.openwheelracing.client.screen.TrackEditorScreen;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.network.OWRNetwork;
import com.openwheelracing.registry.OWRSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

public final class OWRClientInputHandler {
    private static boolean shiftUpWasDown;
    private static boolean shiftDownWasDown;
    private static int autoShiftCooldownTicks;
    private static final boolean[] onboardNumberWasDown = new boolean[9];
    private static boolean sentIdleDriveInput;
    private static int driveInputSequence;
    private static boolean keyboardSteeringSource = true;
    private static int lastSyncedCarId = Integer.MIN_VALUE;
    private static int sentBalancedClipStart = Integer.MIN_VALUE;
    private static int sentBalancedClipEnd = Integer.MIN_VALUE;
    private static int sentHarvestNegativeStart = Integer.MIN_VALUE;
    private static int sentHarvestNegativeFull = Integer.MIN_VALUE;
    private static int sentBalancedStartPower = Integer.MIN_VALUE;
    private static int sentBalancedEndPower = Integer.MIN_VALUE;
    private static int sentHarvestStartPower = Integer.MIN_VALUE;
    private static int sentHarvestEndPower = Integer.MIN_VALUE;
    private static int sentLicoSpeedThreshold = Integer.MIN_VALUE;
    private static double sentLicoSteeringThreshold = Double.NaN;
    private static double sentLicoLateralGThreshold = Double.NaN;
    private static int sentLicoHarvestPower = Integer.MIN_VALUE;
    private static int sentLicoBalancedPower = Integer.MIN_VALUE;
    private static int sentLicoAttackPower = Integer.MIN_VALUE;
    private static double sentCapacityMj = Double.NaN;
    private static int sentTimingScope = Integer.MIN_VALUE;
    private static float mappedKeyboardThrottle;
    private static float mappedKeyboardBrake;

    private OWRClientInputHandler() {
    }

    public static void resetErsSync() {
        lastSyncedCarId = Integer.MIN_VALUE;
    }

    public static void resetTimingScopeSync() {
        sentTimingScope = Integer.MIN_VALUE;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }

        int timingScope = Boolean.TRUE.equals(WheelInputSettings.get().allTimeLapTiming) ? 1 : 0;
        if (sentTimingScope != timingScope) {
            OWRNetwork.sendToServer(new OWRNetwork.SetLapTimingScopeMessage(timingScope));
            sentTimingScope = timingScope;
        }

        TrackEditorScreen.preloadAroundPlayer(mc);

        while (OWRKeyMappings.TRACK_EDITOR.consumeClick()) {
            mc.setScreen(new TrackEditorScreen());
            return;
        }

        WheelInputManager.Output wheel = WheelInputManager.poll(WheelInputSettings.get());
        while (!(mc.player.getVehicle() instanceof OpenwheelCarEntity) && OWRKeyMappings.MOUNT_CAR.consumeClick()) {
            OWRNetwork.sendToServer(new OWRNetwork.MountCarMessage());
        }
        if (!(mc.player.getVehicle() instanceof OpenwheelCarEntity) && wheel.pressed(WheelInputSettings.ButtonRole.MOUNT_CAR)) {
            OWRNetwork.sendToServer(new OWRNetwork.MountCarMessage());
        }

        if (!(mc.player.getVehicle() instanceof OpenwheelCarEntity)) {
            lastSyncedCarId = Integer.MIN_VALUE;
            keyboardSteeringSource = true;
            mappedKeyboardThrottle = 0.0f;
            mappedKeyboardBrake = 0.0f;
            return;
        }

        OpenwheelCarEntity car = (OpenwheelCarEntity) mc.player.getVehicle();
        WheelInputSettings settings = WheelInputSettings.get();
        syncErsThresholdsIfNeeded(settings, car);
        KeyboardInputSettings keyboard = settings.keyboard.sanitized();
        car.setKeyboardSteeringTuning(keyboard.lowSpeedSteeringRate, keyboard.highSpeedSteeringRate, keyboard.lowSpeedCenteringRate, keyboard.highSpeedCenteringRate,
            keyboard.lowSpeedSteeringGain, keyboard.highSpeedSteeringGain, keyboard.speedResponseCurve);
        boolean keyboardThrottlePressed = isDown(OWRKeyMappings.THROTTLE);
        boolean keyboardBrakePressed = isDown(OWRKeyMappings.BRAKE);
        boolean assistedKeyboard = settings.drivingMode.usesKeyboardAssistance();
        if (assistedKeyboard) {
            mappedKeyboardThrottle = KeyboardPedalResponse.next(mappedKeyboardThrottle, keyboardThrottlePressed, 0.30f,
                keyboard.throttleRiseSeconds(), keyboard.throttleReleaseSeconds(), KeyboardPedalResponse.TICK_SECONDS);
            mappedKeyboardBrake = KeyboardPedalResponse.next(mappedKeyboardBrake, keyboardBrakePressed, 0.65f,
                keyboard.brakeRiseSeconds(), keyboard.brakeReleaseSeconds(), KeyboardPedalResponse.TICK_SECONDS);
        } else {
            mappedKeyboardThrottle = 0.0f;
            mappedKeyboardBrake = 0.0f;
        }
        float keyboardThrottle = assistedKeyboard ? mappedKeyboardThrottle : 0.0f;
        float keyboardBrake = assistedKeyboard ? mappedKeyboardBrake : 0.0f;
        float keyboardSteering = assistedKeyboard
            ? (isDown(OWRKeyMappings.STEER_RIGHT) ? 1.0f : 0.0f) - (isDown(OWRKeyMappings.STEER_LEFT) ? 1.0f : 0.0f)
            : 0.0f;
        float wheelThrottle = assistedKeyboard ? 0.0f : wheel.throttle();
        float wheelBrake = assistedKeyboard ? 0.0f : wheel.brake();
        float wheelSteering = assistedKeyboard ? 0.0f : wheel.steering();
        float throttle = assistedKeyboard ? keyboardThrottle : wheelThrottle;
        float brake = assistedKeyboard ? keyboardBrake : wheelBrake;
        float steering = assistedKeyboard ? keyboardSteering : wheelSteering;
        keyboardSteeringSource = assistedKeyboard;
        float stabilityAssistStrength = assistedKeyboard ? 1.0f : 0.0f;
        car.setTractionControlStrength(settings.tractionControlStrength);
        car.setAssistGripEnvelopes(settings.tractionControlEnvelope, settings.absEnvelope);
        car.setYawAdjustments(settings.brakingYawAdjustment, settings.neutralYawAdjustment, settings.throttleYawAdjustment);
        car.setKeyboardStabilityAssistStrength(stabilityAssistStrength);
        car.tickLocalClientMovement(driveInputSequence + 1, throttle, brake, steering, keyboardSteeringSource);
        sendDriveInputIfNeeded(keyboardThrottle, keyboardBrake, wheelThrottle, wheelBrake,
            (float) car.getSteeringAngleRadians(), keyboard,
            settings.tractionControlStrength, settings.tractionControlEnvelope, settings.absEnvelope,
            settings.brakingYawAdjustment, settings.neutralYawAdjustment, settings.throttleYawAdjustment,
            stabilityAssistStrength, keyboardSteeringSource);

        boolean shiftUpDown = isDown(OWRKeyMappings.SHIFT_UP) || wheel.pressed(WheelInputSettings.ButtonRole.SHIFT_UP);
        boolean shiftDownDown = isDown(OWRKeyMappings.SHIFT_DOWN) || wheel.pressed(WheelInputSettings.ButtonRole.SHIFT_DOWN);
        if (shiftUpDown && !shiftUpWasDown) {
            shiftUp();
        }
        if (shiftDownDown && !shiftDownWasDown) {
            shiftDown();
        }
        shiftUpWasDown = shiftUpDown;
        shiftDownWasDown = shiftDownDown;
        if (!shiftUpDown && !shiftDownDown) {
            handleAutoShift(settings, car, throttle, brake);
        } else {
            autoShiftCooldownTicks = AutoShiftPolicy.COOLDOWN_TICKS;
        }
        handleOnboardNumberKeys();
        while (OWRKeyMappings.EXIT_CAR.consumeClick()) {
            OWRNetwork.sendToServer(new OWRNetwork.ExitCarMessage());
        }
        if (wheel.pressed(WheelInputSettings.ButtonRole.EXIT_CAR)) {
            OWRNetwork.sendToServer(new OWRNetwork.ExitCarMessage());
        }
        while (OWRKeyMappings.TOGGLE_ABS.consumeClick()) {
            car.toggleAbs();
            OWRNetwork.sendToServer(new OWRNetwork.ToggleAbsMessage());
        }
        if (wheel.pressed(WheelInputSettings.ButtonRole.TOGGLE_ABS)) {
            car.toggleAbs();
            OWRNetwork.sendToServer(new OWRNetwork.ToggleAbsMessage());
        }
        while (OWRKeyMappings.TOGGLE_TC.consumeClick()) {
            car.toggleTractionControl();
            OWRNetwork.sendToServer(new OWRNetwork.ToggleTractionControlMessage());
        }
        if (wheel.pressed(WheelInputSettings.ButtonRole.TOGGLE_TC)) {
            car.toggleTractionControl();
            OWRNetwork.sendToServer(new OWRNetwork.ToggleTractionControlMessage());
        }
        while (OWRKeyMappings.TOGGLE_DRS.consumeClick()) {
            toggleDrs();
        }
        if (wheel.pressed(WheelInputSettings.ButtonRole.TOGGLE_DRS)) {
            toggleDrs();
        }
        while (OWRKeyMappings.ERS_MODE_PREVIOUS.consumeClick()) {
            cycleErsMode(-1);
        }
        while (OWRKeyMappings.ERS_MODE_NEXT.consumeClick()) {
            cycleErsMode(1);
        }
    }

    public static boolean shiftUp() {
        OpenwheelCarEntity car = onboardCar();
        if (car == null) {
            return false;
        }
        car.shiftLocal(1);
        OWRNetwork.sendToServer(new OWRNetwork.ShiftMessage(1, false));
        return true;
    }

    public static boolean shiftDown() {
        OpenwheelCarEntity car = onboardCar();
        if (car == null) {
            return false;
        }
        car.shiftLocal(-1);
        OWRNetwork.sendToServer(new OWRNetwork.ShiftMessage(-1, false));
        return true;
    }

    private static void handleAutoShift(WheelInputSettings settings, OpenwheelCarEntity car, float throttle, float brake) {
        if (!settings.autoShiftEnabled) {
            autoShiftCooldownTicks = 0;
            return;
        }
        AutoShiftPolicy.Decision decision = AutoShiftPolicy.decide(car.getGear(), car.getMaxForwardGear(), car.getRpm(),
            car.getRedlineRpm(), car.getProjectedRpmForGear(car.getGear() - 1), throttle, brake, autoShiftCooldownTicks);
        autoShiftCooldownTicks = decision.cooldownTicks();
        if (decision.direction() != 0) {
            OWRNetwork.sendToServer(new OWRNetwork.ShiftMessage(decision.direction(), true));
        }
    }

    public static boolean toggleDrs() {
        Minecraft mc = Minecraft.getInstance();
        OpenwheelCarEntity car = onboardCar();
        if (car == null || mc.player == null) {
            return false;
        }
        car.toggleDrs();
        OWRNetwork.sendToServer(new OWRNetwork.ToggleDrsMessage());
        mc.player.playSound(OWRSoundEvents.DRS_BEEP.get(), 1.0f, 1.0f);
        return true;
    }

    public static boolean cycleErsMode(int direction) {
        Minecraft mc = Minecraft.getInstance();
        OpenwheelCarEntity car = onboardCar();
        if (car == null || mc.player == null || direction == 0) {
            return false;
        }
        car.cycleErsModeLocal(direction);
        OWRNetwork.sendToServer(new OWRNetwork.CycleErsModeMessage(direction));
        mc.player.playSound(OWRSoundEvents.DRS_BEEP.get(), 0.75f, direction > 0 ? 1.15f : 0.85f);
        return true;
    }

    public static boolean setErsMode(int mode) {
        Minecraft mc = Minecraft.getInstance();
        OpenwheelCarEntity car = onboardCar();
        if (car == null || mc.player == null) {
            return false;
        }
        int clampedMode = Math.max(OpenwheelCarEntity.ERS_MODE_HARVEST, Math.min(OpenwheelCarEntity.ERS_MODE_ATTACK, mode));
        car.setErsMode(clampedMode);
        OWRNetwork.sendToServer(new OWRNetwork.SetErsModeMessage(clampedMode));
        mc.player.playSound(OWRSoundEvents.DRS_BEEP.get(), 0.75f, clampedMode == OpenwheelCarEntity.ERS_MODE_ATTACK ? 1.15f : clampedMode == OpenwheelCarEntity.ERS_MODE_HARVEST ? 0.85f : 1.0f);
        return true;
    }

    public static boolean handleOnboardNumberKey(int keyCode) {
        int slot = keyCode - GLFW.GLFW_KEY_1;
        if (slot < 0 || slot >= 9 || onboardCar() == null) {
            return false;
        }
        if (slot < 3) {
            setErsMode(slot);
        }
        return true;
    }

    public static OpenwheelCarEntity onboardCar() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null && mc.player.getVehicle() instanceof OpenwheelCarEntity car) {
            return car;
        }
        return null;
    }

    private static void handleOnboardNumberKeys() {
        for (int i = 0; i < onboardNumberWasDown.length; i++) {
            boolean down = isRawKeyDown(GLFW.GLFW_KEY_1 + i);
            if (down && !onboardNumberWasDown[i]) {
                handleOnboardNumberKey(GLFW.GLFW_KEY_1 + i);
            }
            onboardNumberWasDown[i] = down;
        }
    }

    private static void syncErsThresholdsIfNeeded(WheelInputSettings settings, OpenwheelCarEntity car) {
        boolean sameCar = car.getId() == lastSyncedCarId;
        if (sameCar
                && settings.ersBalancedClipStartKmh == sentBalancedClipStart
                && settings.ersBalancedClipEndKmh == sentBalancedClipEnd
                && settings.ersHarvestNegativeStartKmh == sentHarvestNegativeStart
                && settings.ersHarvestNegativeFullKmh == sentHarvestNegativeFull
                && settings.ersBalancedStartPowerKw == sentBalancedStartPower
                && settings.ersBalancedEndPowerKw == sentBalancedEndPower
                && settings.ersHarvestStartPowerKw == sentHarvestStartPower
                && settings.ersHarvestEndPowerKw == sentHarvestEndPower
                && settings.ersLicoSpeedThresholdKmh == sentLicoSpeedThreshold
                && settings.ersLicoSteeringThresholdDegrees == sentLicoSteeringThreshold
                && settings.ersLicoLateralGThreshold == sentLicoLateralGThreshold
                && settings.ersLicoHarvestPowerKw == sentLicoHarvestPower
                && settings.ersLicoBalancedPowerKw == sentLicoBalancedPower
                && settings.ersLicoAttackPowerKw == sentLicoAttackPower
                && settings.ersCapacityMj == sentCapacityMj) {
            return;
        }
        lastSyncedCarId = car.getId();
        sentBalancedClipStart = settings.ersBalancedClipStartKmh;
        sentBalancedClipEnd = settings.ersBalancedClipEndKmh;
        sentHarvestNegativeStart = settings.ersHarvestNegativeStartKmh;
        sentHarvestNegativeFull = settings.ersHarvestNegativeFullKmh;
        sentBalancedStartPower = settings.ersBalancedStartPowerKw;
        sentBalancedEndPower = settings.ersBalancedEndPowerKw;
        sentHarvestStartPower = settings.ersHarvestStartPowerKw;
        sentHarvestEndPower = settings.ersHarvestEndPowerKw;
        sentLicoSpeedThreshold = settings.ersLicoSpeedThresholdKmh;
        sentLicoSteeringThreshold = settings.ersLicoSteeringThresholdDegrees;
        sentLicoLateralGThreshold = settings.ersLicoLateralGThreshold;
        sentLicoHarvestPower = settings.ersLicoHarvestPowerKw;
        sentLicoBalancedPower = settings.ersLicoBalancedPowerKw;
        sentLicoAttackPower = settings.ersLicoAttackPowerKw;
        sentCapacityMj = settings.ersCapacityMj;
        OWRNetwork.sendToServer(new OWRNetwork.SetErsThresholdsMessage(
            sentBalancedClipStart,
            sentBalancedClipEnd,
            sentHarvestNegativeStart,
            sentHarvestNegativeFull,
            sentBalancedStartPower,
            sentBalancedEndPower,
            sentHarvestStartPower,
            sentHarvestEndPower,
            sentCapacityMj,
            sentLicoSpeedThreshold,
            sentLicoSteeringThreshold,
            sentLicoLateralGThreshold,
            sentLicoHarvestPower,
            sentLicoBalancedPower,
            sentLicoAttackPower
        ));
    }

    private static void sendDriveInputIfNeeded(float keyboardThrottle, float keyboardBrake, float wheelThrottle, float wheelBrake,
            float steeringAngleRadians,
            KeyboardInputSettings keyboard, float tractionControlStrength, float tractionControlEnvelope,
            float absEnvelope, float brakingYawAdjustment, float neutralYawAdjustment,
            float throttleYawAdjustment, float stabilityAssistStrength, boolean keyboardSteeringSource) {
        boolean idle = keyboardThrottle == 0.0f && keyboardBrake == 0.0f
            && wheelThrottle == 0.0f && wheelBrake == 0.0f && Math.abs(steeringAngleRadians) < 1.0E-5f;
        if (idle && sentIdleDriveInput) {
            return;
        }
        sentIdleDriveInput = idle;
        OWRNetwork.sendToServer(new OWRNetwork.DriveInputMessage(++driveInputSequence,
            keyboardThrottle, keyboardBrake, wheelThrottle, wheelBrake, steeringAngleRadians,
            keyboard.lowSpeedSteeringRate, keyboard.highSpeedSteeringRate, keyboard.lowSpeedCenteringRate, keyboard.highSpeedCenteringRate,
            keyboard.lowSpeedSteeringGain, keyboard.highSpeedSteeringGain, keyboard.speedResponseCurve,
            tractionControlStrength, tractionControlEnvelope, absEnvelope,
            brakingYawAdjustment, neutralYawAdjustment, throttleYawAdjustment,
            stabilityAssistStrength, keyboardSteeringSource));
    }

    /** Poll the raw GLFW key state regardless of Minecraft conflict context. */
    private static boolean isDown(net.minecraft.client.KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        return isRawKeyDown(key.getValue());
    }

    private static boolean isRawKeyDown(int keyCode) {
        com.mojang.blaze3d.platform.Window win = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(win, keyCode);
    }
}
