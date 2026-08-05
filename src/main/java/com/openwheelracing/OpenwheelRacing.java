package com.openwheelracing;

import com.mojang.logging.LogUtils;
import com.openwheelracing.content.command.OWRCommands;
import com.openwheelracing.content.race.OWRLegacyDimensionDataImporter;
import com.openwheelracing.content.track.TrackMapAutoDetector;
import com.openwheelracing.network.OWRNetwork;
import com.openwheelracing.registry.OWRBlockEntities;
import com.openwheelracing.registry.OWRBlocks;
import com.openwheelracing.registry.OWRCreativeTabs;
import com.openwheelracing.registry.OWRDataComponents;
import com.openwheelracing.registry.OWREntities;
import com.openwheelracing.registry.OWRFluids;
import com.openwheelracing.registry.OWRFuelHandler;
import com.openwheelracing.registry.OWRItems;
import com.openwheelracing.registry.OWRMenus;
import com.openwheelracing.registry.OWRRecipes;
import com.openwheelracing.registry.OWRSoundEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(OpenwheelRacing.MODID)
public final class OpenwheelRacing {
    public static final String MODID = "openwheelracing";

    private static final Logger LOGGER = LogUtils.getLogger();

    public OpenwheelRacing(IEventBus modBus) {
        modBus.addListener(this::commonSetup);
        modBus.addListener(OWRNetwork::register);
        OWRDataComponents.register(modBus);
        OWREntities.register(modBus);
        OWRFluids.register(modBus);
        OWRItems.register(modBus);
        OWRBlocks.register(modBus);
        OWRBlockEntities.register(modBus);
        OWRMenus.register(modBus);
        OWRRecipes.register(modBus);
        OWRSoundEvents.register(modBus);
        OWRCreativeTabs.register(modBus);
        NeoForge.EVENT_BUS.addListener(OWRFuelHandler::onFuelBurnTime);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> OWRCommands.register(event));
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(this::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(TrackMapAutoDetector::onServerTick);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Openwheel Racing initialized");
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        syncPlayerCircuit(event);
    }

    private void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        syncPlayerCircuit(event);
    }

    private void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        syncPlayerCircuit(event);
    }

    private void onServerStarted(ServerStartedEvent event) {
        TrackMapAutoDetector.clearJobs();
        OWRLegacyDimensionDataImporter.importOnServerStarted(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        TrackMapAutoDetector.clearJobs();
    }

    private void syncPlayerCircuit(PlayerEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) || !(serverPlayer.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        OWRNetwork.sendRankingBoard(serverPlayer, level);
        OWRNetwork.sendRaceFlag(serverPlayer, level, false);
        OWRNetwork.syncVisibleLiveries(serverPlayer);
    }
}
