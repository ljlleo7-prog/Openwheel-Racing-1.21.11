package com.openwheelracing.client.input;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

public final class OWRClientCommands {
    private static com.openwheelracing.content.entity.OpenwheelCarEntity handlingCaptureCar;
    private OWRClientCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("owrhandling")
            .then(Commands.literal("start").executes(context -> handlingCapture(context.getSource(), "start")))
            .then(Commands.literal("mark").executes(context -> handlingCapture(context.getSource(), "mark")))
            .then(Commands.literal("stop").executes(context -> handlingCapture(context.getSource(), "stop"))));
        dispatcher.register(Commands.literal("owrnet")
            .executes(context -> networkTest(context.getSource(), "status", 0))
            .then(Commands.literal("lag")
                .executes(context -> networkTest(context.getSource(), "lag", 6))
                .then(Commands.literal("off").executes(context -> networkTest(context.getSource(), "lag", 0)))
                .then(Commands.argument("ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 40))
                    .executes(context -> networkTest(context.getSource(), "lag",
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "ticks")))))
            .then(Commands.literal("auto-rewind")
                .then(Commands.literal("on").executes(context -> networkTest(context.getSource(), "auto", 1)))
                .then(Commands.literal("off").executes(context -> networkTest(context.getSource(), "auto", 0))))
            .then(Commands.literal("rewind-preview")
                .executes(context -> networkTest(context.getSource(), "preview", 0))));
        dispatcher.register(Commands.literal("owrgap")
            .executes(context -> setRaceGap(context.getSource(), !WheelInputSettings.get().raceGapToLeader))
            .then(Commands.literal("interval").executes(context -> setRaceGap(context.getSource(), false)))
            .then(Commands.literal("leader").executes(context -> setRaceGap(context.getSource(), true))));
        dispatcher.register(Commands.literal("owrmap")
            .executes(context -> setMap(context.getSource(), !com.openwheelracing.client.map.LiveMapClient.enabled()))
            .then(Commands.literal("on").executes(context -> setMap(context.getSource(), true)))
            .then(Commands.literal("off").executes(context -> setMap(context.getSource(), false)))
            .then(Commands.literal("screen").executes(context -> {
                setMap(context.getSource(), true);
                com.openwheelracing.client.map.LiveMapClient.requestScreen();
                return 1;
            })));
        dispatcher.register(Commands.literal("owrinput")
            .executes(context -> setMode(context.getSource(), WheelInputSettings.get().drivingMode.toggled()))
            .then(Commands.literal("toggle")
                .executes(context -> setMode(context.getSource(), WheelInputSettings.get().drivingMode.toggled())))
            .then(Commands.literal("assisted")
                .executes(context -> setMode(context.getSource(), KeyboardDrivingMode.ASSISTED_KEYBOARD)))
            .then(Commands.literal("direct")
                .executes(context -> setMode(context.getSource(), KeyboardDrivingMode.DIRECT_JOYSTICK)))
            .then(Commands.literal("status")
                .executes(context -> showMode(context.getSource()))));
    }

    public static void tickHandlingCapture() {
        var mc = Minecraft.getInstance();
        if (handlingCaptureCar != null && (handlingCaptureCar.level() != mc.level || handlingCaptureCar.isRemoved())) {
            try {
                var path = handlingCaptureCar.saveHandlingTrace(
                    mc.gameDirectory.toPath().resolve("logs").resolve("openwheelracing-handling"));
                if (mc.player != null) mc.player.displayClientMessage(
                    Component.translatable("command.openwheelracing.handling.saved", path.toString()), false);
            } catch (java.io.IOException | IllegalStateException exception) {
                com.mojang.logging.LogUtils.getLogger().error("Unable to save handling capture on vehicle/world removal", exception);
            } finally { handlingCaptureCar = null; }
        }
    }

    private static int handlingCapture(CommandSourceStack source, String action) {
        var mc = Minecraft.getInstance();
        try {
            if (action.equals("start")) {
                if (handlingCaptureCar != null) throw new IllegalStateException("A capture is already active");
                if (mc.player == null || !(mc.player.getVehicle() instanceof com.openwheelracing.content.entity.OpenwheelCarEntity car)) {
                    source.sendFailure(Component.translatable("command.openwheelracing.net.car_required"));
                    return 0;
                }
                car.startHandlingTrace();
                handlingCaptureCar = car;
                source.sendSuccess(() -> Component.translatable("command.openwheelracing.handling.started"), false);
            } else {
                if (handlingCaptureCar == null) throw new IllegalStateException("No handling capture is active");
                if (action.equals("mark")) {
                    handlingCaptureCar.markHandlingTrace();
                    source.sendSuccess(() -> Component.translatable("command.openwheelracing.handling.marked"), false);
                } else {
                    var path = handlingCaptureCar.saveHandlingTrace(
                        mc.gameDirectory.toPath().resolve("logs").resolve("openwheelracing-handling"));
                    handlingCaptureCar = null;
                    source.sendSuccess(() -> Component.translatable("command.openwheelracing.handling.saved", path.toString()), false);
                }
            }
            return 1;
        } catch (java.io.IOException | IllegalStateException exception) {
            source.sendFailure(Component.translatable("command.openwheelracing.handling.failed", exception.getMessage()));
            return 0;
        }
    }

    private static int networkTest(CommandSourceStack source, String action, int value) {
        var mc = Minecraft.getInstance();
        if (action.equals("lag") && !com.openwheelracing.client.debug.LocalVehicleNetworkTest.localSession()) {
            source.sendFailure(Component.translatable("command.openwheelracing.net.local_only"));
            return 0;
        }
        if (action.equals("lag")) com.openwheelracing.client.debug.LocalVehicleNetworkTest.setDelay(value);
        if (action.equals("auto")) com.openwheelracing.client.debug.LocalVehicleNetworkTest.setAutoRewind(value != 0);
        if (action.equals("preview")) {
            if (!(mc.player.getVehicle() instanceof com.openwheelracing.content.entity.OpenwheelCarEntity)) {
                source.sendFailure(Component.translatable("command.openwheelracing.net.car_required"));
                return 0;
            }
            com.openwheelracing.client.hud.RewindNotification.show(0.15, true);
        }
        source.sendSuccess(() -> Component.translatable("command.openwheelracing.net.status",
            com.openwheelracing.client.debug.LocalVehicleNetworkTest.delayTicks(),
            com.openwheelracing.client.debug.LocalVehicleNetworkTest.autoRewind()), false);
        return 1;
    }

    private static int setRaceGap(CommandSourceStack source, boolean leader) {
        WheelInputSettings.get().raceGapToLeader = leader;
        WheelInputSettings.save(Minecraft.getInstance());
        source.sendSuccess(() -> Component.translatable(leader ? "hud.openwheelracing.race.gap_leader" : "hud.openwheelracing.race.gap_interval"), false);
        return 1;
    }

    private static int setMap(CommandSourceStack source, boolean enabled) {
        com.openwheelracing.client.map.LiveMapClient.setEnabled(enabled);
        source.sendSuccess(() -> Component.translatable(enabled ? "map.openwheelracing.enabled" : "map.openwheelracing.disabled"), false);
        return 1;
    }

    private static int setMode(CommandSourceStack source, KeyboardDrivingMode mode) {
        WheelInputSettings settings = WheelInputSettings.get();
        settings.drivingMode = mode;
        WheelInputSettings.save(Minecraft.getInstance());
        return showMode(source);
    }

    private static int showMode(CommandSourceStack source) {
        KeyboardDrivingMode mode = WheelInputSettings.get().drivingMode;
        String description = mode == KeyboardDrivingMode.ASSISTED_KEYBOARD
            ? "assisted keyboard only (mapped pedals and full stability assistance)"
            : "direct joystick only (raw axes and no keyboard assistance)";
        source.sendSuccess(() -> Component.literal("OWR input mode: " + description), false);
        return 1;
    }
}
