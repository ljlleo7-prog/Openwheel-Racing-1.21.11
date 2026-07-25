package com.openwheelracing.client;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.client.hud.CarHudOverlay;
import com.openwheelracing.client.input.OWRClientInputHandler;
import com.openwheelracing.client.input.OWRKeyMappings;
import com.openwheelracing.client.screen.OpenwheelSetupScreen;
import com.openwheelracing.client.sound.CarSoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

public final class OpenwheelRacingClientEvents {
    private static final Identifier CAR_HUD = Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "car_hud");

    private OpenwheelRacingClientEvents() {
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        OWRKeyMappings.register(event);
    }

    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(CAR_HUD, CarHudOverlay::render);
    }

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof PauseScreen)) {
            return;
        }
        event.addListener(Button.builder(Component.translatable("screen.openwheelracing.setup.open"), button -> Minecraft.getInstance().setScreen(new OpenwheelSetupScreen(screen)))
            .bounds(screen.width - 142, screen.height - 28, 134, 20)
            .build());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        OWRClientInputHandler.onClientTick(event);
        CarSoundManager.onClientTick();
    }
}
