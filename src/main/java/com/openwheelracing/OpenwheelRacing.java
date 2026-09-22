package com.openwheelracing;

import com.mojang.logging.LogUtils;
import com.openwheelracing.content.ai.BasicAiFleetChunkTickets;
import com.openwheelracing.content.ai.BasicAiFleetManager;
import com.openwheelracing.content.block.TrackWeatherChunkProgression;
import com.openwheelracing.content.block.TrackMoistureTelemetryService;
import com.openwheelracing.content.command.OWRCommands;
import com.openwheelracing.content.race.OWRLegacyDimensionDataImporter;
import com.openwheelracing.content.race.RaceAutoFlagService;
import com.openwheelracing.content.race.timing.LiveRaceTimingService;
import com.openwheelracing.content.race.weekend.GrandPrixWeekendService;
import com.openwheelracing.content.track.TrackMapAutoDetector;
import com.openwheelracing.content.track.survey.SurveyRouteRuntime;
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
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(OpenwheelRacing.MODID)
public final class OpenwheelRacing {
    public static final String MODID = "openwheelracing";

    private static final Logger LOGGER = LogUtils.getLogger();

    public OpenwheelRacing(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();
        FMLCommonSetupEvent.getBus(modBus).addListener(this::commonSetup);
        OWRNetwork.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            new com.openwheelracing.client.OpenwheelRacingClient(context);
        }
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
        FurnaceFuelBurnTimeEvent.BUS.addListener(OWRFuelHandler::onFuelBurnTime);
        RegisterCommandsEvent.BUS.addListener(OWRCommands::register);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(this::onPlayerLoggedIn);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(this::onPlayerLoggedOut);
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(this::onPlayerChangedDimension);
        PlayerEvent.PlayerRespawnEvent.BUS.addListener(this::onPlayerRespawn);
        ServerStartedEvent.BUS.addListener(this::onServerStarted);
        ServerStoppedEvent.BUS.addListener(this::onServerStopped);
        net.minecraftforge.event.TickEvent.ServerTickEvent.Post.BUS.addListener(TrackMapAutoDetector::onServerTick);
        net.minecraftforge.event.level.ChunkEvent.Load.BUS.addListener(TrackWeatherChunkProgression::onChunkLoad);
        net.minecraftforge.event.level.ChunkEvent.Unload.BUS.addListener(TrackWeatherChunkProgression::onChunkUnload);
        net.minecraftforge.event.TickEvent.ServerTickEvent.Post.BUS.addListener(TrackWeatherChunkProgression::onServerTick);
        net.minecraftforge.event.TickEvent.ServerTickEvent.Post.BUS.addListener(BasicAiFleetManager::onServerTick);
        net.minecraftforge.event.TickEvent.ServerTickEvent.Post.BUS.addListener(LiveRaceTimingService::onServerTick);
        net.minecraftforge.event.TickEvent.ServerTickEvent.Post.BUS.addListener(GrandPrixWeekendService::onServerTick);
        net.minecraftforge.event.TickEvent.ServerTickEvent.Post.BUS.addListener(RaceAutoFlagService::onServerTick);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Openwheel Racing initialized");
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        syncPlayerCircuit(event);
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SurveyRouteRuntime.clearPlayer(event.getEntity().getUUID());
    }

    private void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        SurveyRouteRuntime.clearPlayer(event.getEntity().getUUID());
        syncPlayerCircuit(event);
    }

    private void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        SurveyRouteRuntime.clearPlayer(event.getEntity().getUUID());
        syncPlayerCircuit(event);
    }

    private void onServerStarted(ServerStartedEvent event) {
        TrackMapAutoDetector.clearJobs();
        TrackWeatherChunkProgression.clearAll();
        TrackMoistureTelemetryService.clearAll();
        OWRLegacyDimensionDataImporter.importOnServerStarted(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        BasicAiFleetManager.clearAll();
        LiveRaceTimingService.clearAll();
        RaceAutoFlagService.clearAll();
        BasicAiFleetChunkTickets.releaseAll();
        TrackMapAutoDetector.clearJobs();
        SurveyRouteRuntime.clearAll();
    }

    private void syncPlayerCircuit(PlayerEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) || !(serverPlayer.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        OWRNetwork.sendRankingBoard(serverPlayer, level);
        OWRNetwork.sendRaceFlag(serverPlayer, level, false);
        OWRNetwork.sendVehiclePhysicsPreset(serverPlayer);
        LiveRaceTimingService.sendCurrent(serverPlayer);
        OWRNetwork.sendSurveyRouteOverlay(serverPlayer, false, "", new java.util.UUID(0L, 0L), "", false, null);
        OWRNetwork.syncVisibleLiveries(serverPlayer);
    }
}
