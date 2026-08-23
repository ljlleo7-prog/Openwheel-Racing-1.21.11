package com.openwheelracing.client.input;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

public final class OWRClientCommands {
    private OWRClientCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
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
