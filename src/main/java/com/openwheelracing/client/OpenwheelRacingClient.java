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
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = OpenwheelRacing.MODID, dist = net.neoforged.api.distmarker.Dist.CLIENT)
public final class OpenwheelRacingClient {
    public OpenwheelRacingClient(IEventBus modBus) {
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterMenuScreens);
        modBus.addListener(OpenwheelRacingClientEvents::onRegisterKeyMappings);
        modBus.addListener(OpenwheelRacingClientEvents::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.addListener(OpenwheelRacingClientEvents::onScreenInit);
        NeoForge.EVENT_BUS.addListener(OpenwheelRacingClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(OpenwheelRacingClientEvents::onMouseButton);
        NeoForge.EVENT_BUS.addListener(OpenwheelRacingClientEvents::onRenderPlayer);
        NeoForge.EVENT_BUS.addListener(OpenwheelRacingClientEvents::onRenderLevelAfterEntities);
        NeoForge.EVENT_BUS.addListener(OWRClientCommands::register);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            WheelInputSettings.load(Minecraft.getInstance());
            EntityRenderers.register(OWREntities.PROTOTYPE_CAR.get(), OpenwheelCarRenderer::new);
            EntityRenderers.register(OWREntities.SAFETY_CAR.get(), SafetyCarRenderer::new);
        });
    }

    private void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(OWRMenus.CAR_ASSEMBLY.get(), CarAssemblyScreen::new);
        event.register(OWRMenus.CAR_PARTS_REPLACEMENT.get(), CarPartsReplacementScreen::new);
        event.register(OWRMenus.REFINERY.get(), RefineryScreen::new);
        event.register(OWRMenus.RACE_DIRECTOR.get(), RaceDirectorScreen::new);
        event.register(OWRMenus.RACE_BOARD_TERMINAL.get(), RaceDirectorScreen::new);
        event.register(OWRMenus.TEAM_TERMINAL.get(), RaceDirectorScreen::new);
        event.register(OWRMenus.RACE_LIGHT.get(), RaceLightScreen::new);
    }
}
