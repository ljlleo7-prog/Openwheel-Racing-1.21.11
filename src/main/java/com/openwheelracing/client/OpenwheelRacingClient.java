package com.openwheelracing.client;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.client.input.WheelInputSettings;
import com.openwheelracing.client.input.OWRClientCommands;
import com.openwheelracing.client.render.OpenwheelCarRenderer;
import com.openwheelracing.client.render.SafetyCarRenderer;
import com.openwheelracing.client.screen.CarAssemblyScreen;
import com.openwheelracing.client.screen.CarPartsReplacementScreen;
import com.openwheelracing.client.screen.RaceDirectorScreen;
import com.openwheelracing.client.screen.RefineryScreen;
import com.openwheelracing.client.screen.RaceLightScreen;
import com.openwheelracing.registry.OWREntities;
import com.openwheelracing.registry.OWRMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.AddFramePassEvent;
import net.minecraftforge.client.event.RenderAvatarEvent;
import net.minecraftforge.client.FramePassManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class OpenwheelRacingClient {
    public OpenwheelRacingClient(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();
        FMLClientSetupEvent.getBus(modBus).addListener(this::onClientSetup);
        net.minecraftforge.client.event.RegisterKeyMappingsEvent.BUS.addListener(OpenwheelRacingClientEvents::onRegisterKeyMappings);
        AddGuiOverlayLayersEvent.BUS.addListener(OpenwheelRacingClientEvents::onAddGuiOverlayLayers);
        net.minecraftforge.client.event.ScreenEvent.Init.Post.BUS.addListener(OpenwheelRacingClientEvents::onScreenInit);
        net.minecraftforge.event.TickEvent.ClientTickEvent.Post.BUS.addListener(OpenwheelRacingClientEvents::onClientTick);
        net.minecraftforge.client.event.InputEvent.MouseButton.Pre.BUS.addListener(OpenwheelRacingClientEvents::onMouseButton);
        RenderAvatarEvent.Pre.BUS.addListener(OpenwheelRacingClientEvents::onRenderPlayer);
        AddFramePassEvent.BUS.addListener(event -> event.addPass(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "world_overlays"),
            new FramePassManager.PassDefinition() {
                @Override
                public void extracts(net.minecraft.client.renderer.LevelTargetBundle bundle, com.mojang.blaze3d.framegraph.FramePass pass) {
                    bundle.main = pass.readsAndWrites(bundle.main);
                }

                @Override
                public void executes(net.minecraft.client.renderer.state.LevelRenderState state) {
                    OpenwheelRacingClientEvents.renderWorldOverlays(state);
                }
            }
        ));
        net.minecraftforge.client.event.RegisterClientCommandsEvent.BUS.addListener(OWRClientCommands::register);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            WheelInputSettings.load(Minecraft.getInstance());
            EntityRenderers.register(OWREntities.PROTOTYPE_CAR.get(), OpenwheelCarRenderer::new);
            EntityRenderers.register(OWREntities.SAFETY_CAR.get(), SafetyCarRenderer::new);
            MenuScreens.register(OWRMenus.CAR_ASSEMBLY.get(), CarAssemblyScreen::new);
            MenuScreens.register(OWRMenus.CAR_PARTS_REPLACEMENT.get(), CarPartsReplacementScreen::new);
            MenuScreens.register(OWRMenus.REFINERY.get(), RefineryScreen::new);
            MenuScreens.register(OWRMenus.RACE_DIRECTOR.get(), RaceDirectorScreen::new);
            MenuScreens.register(OWRMenus.RACE_BOARD_TERMINAL.get(), RaceDirectorScreen::new);
            MenuScreens.register(OWRMenus.TEAM_TERMINAL.get(), RaceDirectorScreen::new);
            MenuScreens.register(OWRMenus.RACE_LIGHT.get(), RaceLightScreen::new);
        });
    }
}
