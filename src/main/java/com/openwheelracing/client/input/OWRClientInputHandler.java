package com.openwheelracing.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.openwheelracing.client.screen.TrackEditorScreen;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.network.OWRNetwork;
import com.openwheelracing.registry.OWRSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.PacketDistributor;

public final class OWRClientInputHandler {
    private static boolean shiftUpWasDown;
    private static boolean shiftDownWasDown;
    private static boolean sentIdleDriveInput;
    private static int sentBalancedClipStart = Integer.MIN_VALUE;
    private static int sentBalancedClipEnd = Integer.MIN_VALUE;
    private static int sentHarvestNegativeStart = Integer.MIN_VALUE;
    private static int sentHarvestNegativeFull = Integer.MIN_VALUE;
    private static int sentBalancedStartPower = Integer.MIN_VALUE;
    private static int sentBalancedEndPower = Integer.MIN_VALUE;
    private static int sentHarvestStartPower = Integer.MIN_VALUE;
    private static int sentHarvestEndPower = Integer.MIN_VALUE;
    private static double sentCapacityMj = Double.NaN;

    private OWRClientInputHandler() {
    }

    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
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
            OWRNetwork.CHANNEL.send(new OWRNetwork.MountCarMessage(), PacketDistributor.SERVER.noArg());
        }
        if (!(mc.player.getVehicle() instanceof OpenwheelCarEntity) && wheel.pressed(WheelInputSettings.ButtonRole.MOUNT_CAR)) {
            OWRNetwork.CHANNEL.send(new OWRNetwork.MountCarMessage(), PacketDistributor.SERVER.noArg());
        }

        if (!(mc.player.getVehicle() instanceof OpenwheelCarEntity)) {
            return;
        }

        OpenwheelCarEntity car = (OpenwheelCarEntity) mc.player.getVehicle();
        syncErsThresholdsIfNeeded(WheelInputSettings.get());
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
            OWRNetwork.CHANNEL.send(new OWRNetwork.ShiftMessage(1), PacketDistributor.SERVER.noArg());
        }
        if (shiftDownDown && !shiftDownWasDown) {
            car.shiftLocal(-1);
            OWRNetwork.CHANNEL.send(new OWRNetwork.ShiftMessage(-1), PacketDistributor.SERVER.noArg());
        }
        shiftUpWasDown = shiftUpDown;
        shiftDownWasDown = shiftDownDown;
        while (OWRKeyMappings.EXIT_CAR.consumeClick()) {
            OWRNetwork.CHANNEL.send(new OWRNetwork.ExitCarMessage(), PacketDistributor.SERVER.noArg());
        }
        if (wheel.pressed(WheelInputSettings.ButtonRole.EXIT_CAR)) {
            OWRNetwork.CHANNEL.send(new OWRNetwork.ExitCarMessage(), PacketDistributor.SERVER.noArg());
        }
        while (OWRKeyMappings.TOGGLE_ABS.consumeClick()) {
            car.toggleAbs();
            OWRNetwork.CHANNEL.send(new OWRNetwork.ToggleAbsMessage(), PacketDistributor.SERVER.noArg());
        }
        if (wheel.pressed(WheelInputSettings.ButtonRole.TOGGLE_ABS)) {
            car.toggleAbs();
            OWRNetwork.CHANNEL.send(new OWRNetwork.ToggleAbsMessage(), PacketDistributor.SERVER.noArg());
        }
        while (OWRKeyMappings.TOGGLE_TC.consumeClick()) {
            car.toggleTractionControl();
            OWRNetwork.CHANNEL.send(new OWRNetwork.ToggleTractionControlMessage(), PacketDistributor.SERVER.noArg());
        }
        if (wheel.pressed(WheelInputSettings.ButtonRole.TOGGLE_TC)) {
            car.toggleTractionControl();
            OWRNetwork.CHANNEL.send(new OWRNetwork.ToggleTractionControlMessage(), PacketDistributor.SERVER.noArg());
        }
        while (OWRKeyMappings.TOGGLE_DRS.consumeClick()) {
            car.toggleDrs();
            OWRNetwork.CHANNEL.send(new OWRNetwork.ToggleDrsMessage(), PacketDistributor.SERVER.noArg());
            mc.player.playSound(OWRSoundEvents.DRS_BEEP.get(), 1.0f, 1.0f);
        }
        if (wheel.pressed(WheelInputSettings.ButtonRole.TOGGLE_DRS)) {
            car.toggleDrs();
            OWRNetwork.CHANNEL.send(new OWRNetwork.ToggleDrsMessage(), PacketDistributor.SERVER.noArg());
            mc.player.playSound(OWRSoundEvents.DRS_BEEP.get(), 1.0f, 1.0f);
        }
        while (OWRKeyMappings.ERS_MODE_PREVIOUS.consumeClick()) {
            car.cycleErsModeLocal(-1);
            OWRNetwork.CHANNEL.send(new OWRNetwork.CycleErsModeMessage(-1), PacketDistributor.SERVER.noArg());
            mc.player.playSound(OWRSoundEvents.DRS_BEEP.get(), 0.75f, 0.85f);
        }
        while (OWRKeyMappings.ERS_MODE_NEXT.consumeClick()) {
            car.cycleErsModeLocal(1);
            OWRNetwork.CHANNEL.send(new OWRNetwork.CycleErsModeMessage(1), PacketDistributor.SERVER.noArg());
            mc.player.playSound(OWRSoundEvents.DRS_BEEP.get(), 0.75f, 1.15f);
        }
    }

    private static void syncErsThresholdsIfNeeded(WheelInputSettings settings) {
        if (settings.ersBalancedClipStartKmh == sentBalancedClipStart
                && settings.ersBalancedClipEndKmh == sentBalancedClipEnd
                && settings.ersHarvestNegativeStartKmh == sentHarvestNegativeStart
                && settings.ersHarvestNegativeFullKmh == sentHarvestNegativeFull
                && settings.ersBalancedStartPowerKw == sentBalancedStartPower
                && settings.ersBalancedEndPowerKw == sentBalancedEndPower
                && settings.ersHarvestStartPowerKw == sentHarvestStartPower
                && settings.ersHarvestEndPowerKw == sentHarvestEndPower
                && settings.ersCapacityMj == sentCapacityMj) {
            return;
        }
        sentBalancedClipStart = settings.ersBalancedClipStartKmh;
        sentBalancedClipEnd = settings.ersBalancedClipEndKmh;
        sentHarvestNegativeStart = settings.ersHarvestNegativeStartKmh;
        sentHarvestNegativeFull = settings.ersHarvestNegativeFullKmh;
        sentBalancedStartPower = settings.ersBalancedStartPowerKw;
        sentBalancedEndPower = settings.ersBalancedEndPowerKw;
        sentHarvestStartPower = settings.ersHarvestStartPowerKw;
        sentHarvestEndPower = settings.ersHarvestEndPowerKw;
        sentCapacityMj = settings.ersCapacityMj;
        OWRNetwork.CHANNEL.send(new OWRNetwork.SetErsThresholdsMessage(
            sentBalancedClipStart,
            sentBalancedClipEnd,
            sentHarvestNegativeStart,
            sentHarvestNegativeFull,
            sentBalancedStartPower,
            sentBalancedEndPower,
            sentHarvestStartPower,
            sentHarvestEndPower,
            sentCapacityMj
        ), PacketDistributor.SERVER.noArg());
    }

    private static void sendDriveInputIfNeeded(float keyboardThrottle, float keyboardBrake, float keyboardSteering, float wheelThrottle, float wheelBrake, float wheelSteering) {
        boolean idle = keyboardThrottle == 0.0f && keyboardBrake == 0.0f && keyboardSteering == 0.0f && wheelThrottle == 0.0f && wheelBrake == 0.0f && wheelSteering == 0.0f;
        if (idle && sentIdleDriveInput) {
            return;
        }
        sentIdleDriveInput = idle;
        OWRNetwork.CHANNEL.send(new OWRNetwork.DriveInputMessage(keyboardThrottle, keyboardBrake, keyboardSteering, wheelThrottle, wheelBrake, wheelSteering), PacketDistributor.SERVER.noArg());
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
