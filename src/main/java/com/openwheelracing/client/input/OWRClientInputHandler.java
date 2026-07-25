package com.openwheelracing.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.openwheelracing.client.screen.TrackEditorScreen;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.network.OWRNetwork;
import com.openwheelracing.registry.OWRSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class OWRClientInputHandler {
    private static boolean shiftUpWasDown;
    private static boolean shiftDownWasDown;
    private static boolean sentIdleDriveInput;
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

    private OWRClientInputHandler() {
    }

    public static void resetErsSync() {
        lastSyncedCarId = Integer.MIN_VALUE;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
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
            return;
        }

        OpenwheelCarEntity car = (OpenwheelCarEntity) mc.player.getVehicle();
        syncErsThresholdsIfNeeded(WheelInputSettings.get(), car);
        float keyboardThrottle = isDown(OWRKeyMappings.THROTTLE)    ? 1.0f : 0.0f;
        float keyboardBrake    = isDown(OWRKeyMappings.BRAKE)        ? 1.0f : 0.0f;
        float keyboardSteering = (isDown(OWRKeyMappings.STEER_RIGHT) ? 1.0f : 0.0f)
                              - (isDown(OWRKeyMappings.STEER_LEFT)  ? 1.0f : 0.0f);
        float throttle = Math.max(keyboardThrottle, wheel.throttle());
        float brake = Math.max(keyboardBrake, wheel.brake());
        float steering = keyboardSteering;
        if (Math.abs(wheel.steering()) > 0.0f) {
            steering = wheel.steering();
        }
        car.tickLocalClientMovement(throttle, brake, steering);
        sendDriveInputIfNeeded(keyboardThrottle, keyboardBrake, keyboardSteering, wheel.throttle(), wheel.brake(), wheel.steering());

        boolean shiftUpDown = isDown(OWRKeyMappings.SHIFT_UP) || wheel.pressed(WheelInputSettings.ButtonRole.SHIFT_UP);
        boolean shiftDownDown = isDown(OWRKeyMappings.SHIFT_DOWN) || wheel.pressed(WheelInputSettings.ButtonRole.SHIFT_DOWN);
        if (shiftUpDown && !shiftUpWasDown) {
            car.shiftLocal(1);
            OWRNetwork.sendToServer(new OWRNetwork.ShiftMessage(1));
        }
        if (shiftDownDown && !shiftDownWasDown) {
            car.shiftLocal(-1);
            OWRNetwork.sendToServer(new OWRNetwork.ShiftMessage(-1));
        }
        shiftUpWasDown = shiftUpDown;
        shiftDownWasDown = shiftDownDown;
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
            car.toggleDrs();
            OWRNetwork.sendToServer(new OWRNetwork.ToggleDrsMessage());
            mc.player.playSound(OWRSoundEvents.DRS_BEEP.get(), 1.0f, 1.0f);
        }
        if (wheel.pressed(WheelInputSettings.ButtonRole.TOGGLE_DRS)) {
            car.toggleDrs();
            OWRNetwork.sendToServer(new OWRNetwork.ToggleDrsMessage());
            mc.player.playSound(OWRSoundEvents.DRS_BEEP.get(), 1.0f, 1.0f);
        }
        while (OWRKeyMappings.ERS_MODE_PREVIOUS.consumeClick()) {
            car.cycleErsModeLocal(-1);
            OWRNetwork.sendToServer(new OWRNetwork.CycleErsModeMessage(-1));
            mc.player.playSound(OWRSoundEvents.DRS_BEEP.get(), 0.75f, 0.85f);
        }
        while (OWRKeyMappings.ERS_MODE_NEXT.consumeClick()) {
            car.cycleErsModeLocal(1);
            OWRNetwork.sendToServer(new OWRNetwork.CycleErsModeMessage(1));
            mc.player.playSound(OWRSoundEvents.DRS_BEEP.get(), 0.75f, 1.15f);
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

    private static void sendDriveInputIfNeeded(float keyboardThrottle, float keyboardBrake, float keyboardSteering, float wheelThrottle, float wheelBrake, float wheelSteering) {
        boolean idle = keyboardThrottle == 0.0f && keyboardBrake == 0.0f && keyboardSteering == 0.0f && wheelThrottle == 0.0f && wheelBrake == 0.0f && wheelSteering == 0.0f;
        if (idle && sentIdleDriveInput) {
            return;
        }
        sentIdleDriveInput = idle;
        OWRNetwork.sendToServer(new OWRNetwork.DriveInputMessage(keyboardThrottle, keyboardBrake, keyboardSteering, wheelThrottle, wheelBrake, wheelSteering));
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
