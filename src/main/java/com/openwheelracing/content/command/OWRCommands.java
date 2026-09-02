package com.openwheelracing.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.openwheelracing.content.ai.BasicAiDriverIdentity;
import com.openwheelracing.content.ai.BasicAiFleetChunkTickets;
import com.openwheelracing.content.ai.BasicAiFleetManager;
import com.openwheelracing.content.ai.BasicAiStatus;
import com.openwheelracing.content.ai.BasicAiTrafficMode;
import com.openwheelracing.content.ai.OWRAiTrainingData;
import com.openwheelracing.content.car.CarLivery;
import com.openwheelracing.content.car.CarLiveryColors;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.content.race.OWRGrandPrixRegistry;
import com.openwheelracing.content.race.timing.LiveRaceTimingService;
import com.openwheelracing.network.OWRNetwork;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackDefinitionsData;
import com.openwheelracing.content.track.TrackGeometry;
import com.openwheelracing.content.track.TrackStewardingGeometryBuilder;
import com.openwheelracing.content.track.survey.SurveyRoute;
import com.openwheelracing.content.track.survey.SurveyRouteRuntime;
import com.openwheelracing.content.track.survey.TrackSurveyData;
import com.openwheelracing.content.track.survey.PitLaneSurveyData;
import com.openwheelracing.content.track.survey.PitLaneSurveyRuntime;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.entity.VehiclePhysicsPreset;
import com.openwheelracing.content.entity.VehiclePhysicsPresetState;
import com.openwheelracing.registry.OWREntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class OWRCommands {
    private OWRCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("owr")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
            .then(Commands.literal("regulation")
                .then(Commands.literal("wheel")
                    .then(Commands.literal("allow")
                        .executes(context -> setWheelInputAllowed(context, true)))
                    .then(Commands.literal("forbid")
                        .executes(context -> setWheelInputAllowed(context, false)))
                    .then(Commands.literal("status")
                        .executes(OWRCommands::showWheelInputStatus))))
            .then(Commands.literal("physics")
                .then(Commands.literal("preset")
                    .then(Commands.literal("classic")
                        .executes(context -> setVehiclePhysicsPreset(context, VehiclePhysicsPreset.CLASSIC)))
                    .then(Commands.literal("dynamic")
                        .executes(context -> setVehiclePhysicsPreset(context, VehiclePhysicsPreset.DYNAMIC)))
                    .then(Commands.literal("status")
                        .executes(OWRCommands::showVehiclePhysicsPreset))))
            .then(Commands.literal("race")
                .then(Commands.literal("timing")
                    .then(Commands.literal("resume").executes(OWRCommands::resumeRaceTiming))
                    .then(Commands.literal("stop").executes(OWRCommands::stopRaceTiming))
                    .then(Commands.literal("status").executes(OWRCommands::showRaceTimingStatus))))
            .then(Commands.literal("physicslog")
                .then(Commands.literal("start").executes(OWRCommands::startPhysicsLog))
                .then(Commands.literal("export").executes(OWRCommands::exportPhysicsLog))
                .then(Commands.literal("stop").executes(OWRCommands::stopPhysicsLog))
                .then(Commands.literal("status").executes(OWRCommands::showPhysicsLogStatus)))
            .then(Commands.literal("gp")
                .then(Commands.literal("register")
                    .then(Commands.argument("gp", StringArgumentType.string())
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("code", StringArgumentType.word())
                                .executes(OWRCommands::registerGrandPrixEntry)))))
                .then(Commands.literal("unregister")
                    .then(Commands.argument("gp", StringArgumentType.string())
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(OWRCommands::unregisterGrandPrixEntry))))
                .then(Commands.literal("list")
                    .executes(OWRCommands::listGrandPrixWeekends)
                    .then(Commands.argument("gp", StringArgumentType.string())
                        .executes(OWRCommands::listGrandPrixEntries))))
            .then(Commands.literal("ai")
                .then(Commands.literal("fleet")
                    .then(Commands.literal("spawn")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 24))
                            .executes(OWRCommands::spawnAiFleet)))
                    .then(Commands.literal("start").executes(OWRCommands::startAiFleet))
                    .then(Commands.literal("stop").executes(OWRCommands::stopAiFleet))
                    .then(Commands.literal("despawn").executes(OWRCommands::despawnAiFleet))
                    .then(Commands.literal("mode")
                        .then(Commands.argument("mode", StringArgumentType.word()).executes(OWRCommands::setAiFleetMode)))
                    .then(Commands.literal("calibration")
                        .then(Commands.literal("status").executes(OWRCommands::showAiCalibrationStatus))
                        .then(Commands.literal("step")
                            .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 10.0))
                                .executes(OWRCommands::setAiCalibrationStep)))
                        .then(Commands.literal("random")
                            .then(Commands.literal("on").executes(context -> setAiCalibrationRandom(context, true)))
                            .then(Commands.literal("off").executes(context -> setAiCalibrationRandom(context, false))))
                        .then(Commands.literal("reset").executes(OWRCommands::resetAiCalibration)))
                    .then(Commands.literal("line")
                        .then(Commands.literal("planned").executes(context -> showAiLine(context, "planned")))
                        .then(Commands.literal("current").executes(context -> showAiLine(context, "current")))
                        .then(Commands.literal("player").executes(context -> showAiLine(context, "player")))
                        .then(Commands.literal("hide").executes(OWRCommands::hideAiLine)))
                    .then(Commands.literal("status").executes(OWRCommands::showAiFleetStatus))))
            .then(Commands.literal("steward")
                .then(Commands.literal("list")
                    .executes(OWRCommands::listTracks))
                .then(Commands.literal("active")
                    .executes(OWRCommands::showActiveTrack))
                .then(Commands.literal("create")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(OWRCommands::createTrack)))
                .then(Commands.literal("select")
                    .then(Commands.argument("trackId", StringArgumentType.word())
                        .executes(OWRCommands::selectTrack)))
                .then(Commands.literal("remove")
                    .then(Commands.argument("trackId", StringArgumentType.word())
                        .executes(OWRCommands::removeTrack)))
                .then(Commands.literal("survey")
                    .then(Commands.literal("start").executes(OWRCommands::startSurvey))
                    .then(Commands.literal("finish").executes(OWRCommands::finishSurvey))
                    .then(Commands.literal("cancel").executes(OWRCommands::cancelSurvey))
                    .then(Commands.literal("status").executes(OWRCommands::showSurveyStatus))
                    .then(Commands.literal("clear").executes(OWRCommands::clearSurvey))
                    .then(Commands.literal("show").executes(OWRCommands::showSurvey))
                    .then(Commands.literal("hide").executes(OWRCommands::hideSurvey)))
                .then(Commands.literal("pit-lane")
                    .then(Commands.literal("entry-here")
                        .executes(context -> setPitLimitHere(context, TrackDefinition.StewardLineType.PIT_LIMIT_START, 8))
                        .then(Commands.argument("width", IntegerArgumentType.integer(1, 64))
                            .executes(context -> setPitLimitHere(context, TrackDefinition.StewardLineType.PIT_LIMIT_START, IntegerArgumentType.getInteger(context, "width")))))
                    .then(Commands.literal("exit-here")
                        .executes(context -> setPitLimitHere(context, TrackDefinition.StewardLineType.PIT_LIMIT_END, 8))
                        .then(Commands.argument("width", IntegerArgumentType.integer(1, 64))
                            .executes(context -> setPitLimitHere(context, TrackDefinition.StewardLineType.PIT_LIMIT_END, IntegerArgumentType.getInteger(context, "width")))))
                    .then(Commands.literal("survey")
                        .then(Commands.literal("start").executes(OWRCommands::startPitLaneSurvey))
                        .then(Commands.literal("finish").executes(OWRCommands::finishPitLaneSurvey))
                        .then(Commands.literal("cancel").executes(OWRCommands::cancelPitLaneSurvey))
                        .then(Commands.literal("status").executes(OWRCommands::showPitLaneSurveyStatus))
                        .then(Commands.literal("clear").executes(OWRCommands::clearPitLaneSurvey))))
                .then(Commands.literal("centerline")
                    .then(Commands.literal("add-here")
                        .executes(context -> addCenterlinePoint(context, 8))
                        .then(Commands.argument("width", IntegerArgumentType.integer(1, 64))
                            .executes(context -> addCenterlinePoint(context, IntegerArgumentType.getInteger(context, "width")))))
                    .then(Commands.literal("clear")
                        .executes(OWRCommands::clearCenterline))
                    .then(Commands.literal("finish")
                        .executes(OWRCommands::finishCenterline)))
                .then(Commands.literal("start-finish")
                    .then(Commands.literal("set-here")
                        .executes(context -> setStartFinishHere(context, 8))
                        .then(Commands.argument("width", IntegerArgumentType.integer(1, 64))
                            .executes(context -> setStartFinishHere(context, IntegerArgumentType.getInteger(context, "width"))))))
                .then(Commands.literal("checkpoint")
                    .then(Commands.literal("add-here")
                        .executes(context -> addCheckpointHere(context, 8))
                        .then(Commands.argument("width", IntegerArgumentType.integer(1, 64))
                            .executes(context -> addCheckpointHere(context, IntegerArgumentType.getInteger(context, "width")))))
                    .then(Commands.literal("clear")
                        .executes(OWRCommands::clearCheckpoints)))
                .then(Commands.literal("grid")
                    .then(Commands.literal("add-here")
                        .executes(OWRCommands::addGridSlotHere)
                        .then(Commands.argument("index", IntegerArgumentType.integer(1, 64))
                            .executes(context -> addGridSlotHere(context, IntegerArgumentType.getInteger(context, "index")))))
                    .then(Commands.literal("clear")
                        .executes(OWRCommands::clearGridSlots)))
                .then(Commands.literal("boundary")
                    .then(Commands.literal("left-here")
                        .executes(context -> addBoundaryHere(context, TrackDefinition.BoundarySide.LEFT)))
                    .then(Commands.literal("right-here")
                        .executes(context -> addBoundaryHere(context, TrackDefinition.BoundarySide.RIGHT)))
                    .then(Commands.literal("clear")
                        .executes(OWRCommands::clearBoundaries)))
                .then(Commands.literal("line")
                    .then(Commands.literal("set")
                        .then(Commands.argument("x1", DoubleArgumentType.doubleArg())
                            .then(Commands.argument("y1", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z1", DoubleArgumentType.doubleArg())
                                    .then(Commands.argument("x2", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y2", DoubleArgumentType.doubleArg())
                                            .then(Commands.argument("z2", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("type", StringArgumentType.word())
                                                    .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                        .executes(OWRCommands::setStewardLine))))))))))
                    .then(Commands.literal("add-here")
                        .then(Commands.argument("type", StringArgumentType.word())
                            .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(context -> addStewardLineHere(context, 8))
                                .then(Commands.argument("width", IntegerArgumentType.integer(1, 64))
                                    .executes(context -> addStewardLineHere(context, IntegerArgumentType.getInteger(context, "width")))))))
                    .then(Commands.literal("list")
                        .executes(OWRCommands::listStewardLines))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("type", StringArgumentType.word())
                            .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(OWRCommands::removeStewardLine))))
                    .then(Commands.literal("clear")
                        .executes(OWRCommands::clearStewardLines))
                    .then(Commands.literal("show")
                        .executes(context -> setStewardLineVisibility(context, true)))
                    .then(Commands.literal("hide")
                        .executes(context -> setStewardLineVisibility(context, false))))
                .then(Commands.literal("ai")
                    .then(Commands.literal("generate")
                        .executes(OWRCommands::generateAiLine)))));
    }

    private static int setVehiclePhysicsPreset(CommandContext<CommandSourceStack> context,
                                               VehiclePhysicsPreset preset) {
        VehiclePhysicsPresetState state = VehiclePhysicsPresetState.get(context.getSource().getServer());
        boolean changed = state.setPreset(preset);
        OWRNetwork.broadcastVehiclePhysicsPreset(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal(
            "Vehicle physics preset: " + preset.name().toLowerCase(java.util.Locale.ROOT)
                + (changed ? " (applied globally)" : " (already active)")), true);
        return 1;
    }

    private static int showVehiclePhysicsPreset(CommandContext<CommandSourceStack> context) {
        VehiclePhysicsPreset preset = VehiclePhysicsPresetState.get(context.getSource().getServer()).preset();
        send(context, "Vehicle physics preset: " + preset.name().toLowerCase(java.util.Locale.ROOT));
        return 1;
    }

    private static int startPhysicsLog(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        OpenwheelCarEntity car = drivenCar(context);
        try {
            Path path = car.startPhysicsTelemetry();
            send(context, "Physics logging started: " + path.toAbsolutePath());
            return 1;
        } catch (IOException exception) {
            send(context, "Physics logging failed: " + exception.getMessage());
            return 0;
        }
    }

    private static int exportPhysicsLog(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        OpenwheelCarEntity car = drivenCar(context);
        try {
            Path path = car.flushPhysicsTelemetry();
            send(context, "Physics log exported with " + car.getPhysicsTelemetrySampleCount() + " samples: " + path.toAbsolutePath());
            return 1;
        } catch (IOException exception) {
            send(context, "Physics log export failed: " + exception.getMessage());
            return 0;
        }
    }

    private static int stopPhysicsLog(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        OpenwheelCarEntity car = drivenCar(context);
        long samples = car.getPhysicsTelemetrySampleCount();
        try {
            Path path = car.stopPhysicsTelemetry();
            if (path == null) {
                send(context, "Physics logging is not active for this car.");
                return 0;
            }
            send(context, "Physics logging stopped with " + samples + " samples: " + path.toAbsolutePath());
            return 1;
        } catch (IOException exception) {
            send(context, "Physics logging stop failed: " + exception.getMessage());
            return 0;
        }
    }

    private static int showPhysicsLogStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        OpenwheelCarEntity car = drivenCar(context);
        send(context, car.isPhysicsTelemetryActive()
            ? "Physics logging active; buffered samples=" + car.getPhysicsTelemetrySampleCount() + "."
            : "Physics logging is not active for this car.");
        return car.isPhysicsTelemetryActive() ? 1 : 0;
    }

    private static OpenwheelCarEntity drivenCar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (player.getVehicle() instanceof OpenwheelCarEntity car) {
            return car;
        }
        throw EntityArgument.NO_ENTITIES_FOUND.create();
    }

    private static int resumeRaceTiming(CommandContext<CommandSourceStack> context) {
        boolean resumed = LiveRaceTimingService.resume(context.getSource().getLevel());
        send(context, resumed ? "Live race timing resumed." : "No suspended live timing session to resume.");
        return resumed ? 1 : 0;
    }

    private static int registerGrandPrixEntry(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String gpName = StringArgumentType.getString(context, "gp");
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String code = StringArgumentType.getString(context, "code");
        try {
            OWRGrandPrixRegistry.RegistrationResult result = OWRGrandPrixRegistry.get(context.getSource().getServer()).register(
                gpName, player.getUUID(), player.getScoreboardName(), code, context.getSource().getLevel().getGameTime());
            if (!result.registered()) {
                OWRGrandPrixRegistry.Entry collision = result.entry();
                send(context, "GP registration refused: code " + OWRGrandPrixRegistry.sanitizeDisplayCode(code)
                    + " is already assigned to " + collision.playerName() + " in " + collision.gpName() + ".");
                return 0;
            }
            OWRGrandPrixRegistry.Entry entry = result.entry();
            send(context, (result.updated() ? "Updated" : "Registered") + " " + entry.playerName() + " as "
                + entry.displayCode() + " in " + entry.gpName() + ".");
            return 1;
        } catch (IllegalArgumentException exception) {
            send(context, "GP registration refused: " + exception.getMessage() + ".");
            return 0;
        }
    }

    private static int unregisterGrandPrixEntry(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String gpName = StringArgumentType.getString(context, "gp");
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        try {
            boolean removed = OWRGrandPrixRegistry.get(context.getSource().getServer()).unregister(gpName, player.getUUID());
            send(context, removed
                ? "Unregistered " + player.getScoreboardName() + " from " + OWRGrandPrixRegistry.sanitizeGpName(gpName) + "."
                : player.getScoreboardName() + " is not registered in " + OWRGrandPrixRegistry.sanitizeGpName(gpName) + ".");
            return removed ? 1 : 0;
        } catch (IllegalArgumentException exception) {
            send(context, "GP unregister refused: " + exception.getMessage() + ".");
            return 0;
        }
    }

    private static int listGrandPrixWeekends(CommandContext<CommandSourceStack> context) {
        List<String> names = OWRGrandPrixRegistry.get(context.getSource().getServer()).grandPrixNames();
        if (names.isEmpty()) {
            send(context, "No GP weekends have registered entries.");
            return 0;
        }
        send(context, "GP weekends: " + String.join(", ", names) + ".");
        return names.size();
    }

    private static int listGrandPrixEntries(CommandContext<CommandSourceStack> context) {
        String gpName = StringArgumentType.getString(context, "gp");
        try {
            List<OWRGrandPrixRegistry.Entry> entries = OWRGrandPrixRegistry.get(context.getSource().getServer()).entries(gpName);
            if (entries.isEmpty()) {
                send(context, "No entries registered in " + OWRGrandPrixRegistry.sanitizeGpName(gpName) + ".");
                return 0;
            }
            send(context, entries.getFirst().gpName() + ": " + entries.stream()
                .map(entry -> entry.displayCode() + " " + entry.playerName())
                .collect(java.util.stream.Collectors.joining(", ")) + ".");
            return entries.size();
        } catch (IllegalArgumentException exception) {
            send(context, "GP list refused: " + exception.getMessage() + ".");
            return 0;
        }
    }

    private static int stopRaceTiming(CommandContext<CommandSourceStack> context) {
        boolean stopped = LiveRaceTimingService.stop(context.getSource().getLevel(), "DIRECTOR");
        send(context, stopped ? "Live race timing suspended." : "No live timing session is configured.");
        return stopped ? 1 : 0;
    }

    private static int showRaceTimingStatus(CommandContext<CommandSourceStack> context) {
        var snapshot = LiveRaceTimingService.latestSnapshot(context.getSource().getLevel());
        if (snapshot.isEmpty()) {
            send(context, "Live race timing is not configured.");
            return 0;
        }
        var timing = snapshot.get();
        send(context, "Live race timing: " + (timing.active() ? "active" : "suspended (" + timing.suspensionReason() + ")")
            + ", session=" + timing.sessionName() + ", cars=" + timing.rows().size() + ", revision=" + timing.revision() + ".");
        return timing.rows().size();
    }

    private static int spawnAiFleet(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        Optional<TrackDefinition> activeTrack = activeTrack(context);
        if (activeTrack.isEmpty()) {
            send(context, "AI fleet spawn refused: no active track in this dimension.");
            return 0;
        }
        TrackDefinition track = activeTrack.get();
        Optional<SurveyRoute> survey = TrackSurveyData.get(level).get(track.trackId());
        if (survey.isEmpty() || survey.get().nodes().size() < 2 || !(survey.get().length() > 0.0)) {
            send(context, "AI fleet spawn refused: no usable consolidated survey for " + track.name() + ".");
            return 0;
        }
        int count = IntegerArgumentType.getInteger(context, "count");
        if (!BasicAiFleetManager.ownedCars(level, track.trackId()).isEmpty()) {
            send(context, "AI fleet spawn refused: despawn the existing AI fleet for " + track.name() + " first.");
            return 0;
        }
        List<TrackDefinition.GridSlot> slots = track.gridSlots().stream().sorted(Comparator.comparingInt(TrackDefinition.GridSlot::index)).toList();
        HashSet<Integer> indices = new HashSet<>();
        for (TrackDefinition.GridSlot slot : slots) {
            if (!indices.add(slot.index())) {
                send(context, "AI fleet spawn refused: duplicate grid index " + slot.index() + ".");
                return 0;
            }
        }
        if (slots.size() < count) {
            send(context, "AI fleet spawn refused: requested " + count + " cars but only " + slots.size() + " grid slots are authored.");
            return 0;
        }

        UUID fleetId = UUID.randomUUID();
        List<OpenwheelCarEntity> spawned = new ArrayList<>();
        for (int ordinal = 0; ordinal < count; ordinal++) {
            TrackDefinition.GridSlot slot = slots.get(ordinal);
            OpenwheelCarEntity car = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level);
            TrackDefinition.Point3 position = slot.position();
            car.setPos(position.x(), position.y() + 0.02, position.z());
            car.setYRot((float) Math.toDegrees(slot.headingRadians()) - 90.0f);
            car.setDeltaMovement(Vec3.ZERO);
            car.setBasicAiIdentity(BasicAiDriverIdentity.create(fleetId, track.trackId(), slot.index(), ordinal + 1));
            int liveryIndex = Math.floorMod(fleetId.hashCode() * 31 + slot.index(), CarLivery.count());
            CarLivery livery = CarLivery.fromIndex(liveryIndex);
            car.setLivery(liveryIndex);
            car.setLiveryColors(CarLiveryColors.fromPreset(livery));
            BasicAiFleetManager.prepareAiCarDefaults(car);
            if (!level.addFreshEntity(car)) {
                spawned.forEach(OpenwheelCarEntity::discard);
                send(context, "AI fleet spawn failed at grid slot " + slot.index() + "; rolled back " + spawned.size() + " cars.");
                return 0;
            }
            spawned.add(car);
        }
        send(context, "Spawned stopped AI fleet " + fleetId + " with " + spawned.size() + " cars for " + track.name() + ".");
        return spawned.size();
    }

    private static int startAiFleet(CommandContext<CommandSourceStack> context) {
        Optional<TrackDefinition> track = activeTrack(context);
        if (track.isEmpty()) {
            send(context, "AI fleet start refused: no active track in this dimension.");
            return 0;
        }
        int count = BasicAiFleetManager.start(context.getSource().getLevel(), track.get().trackId());
        send(context, count == 0 ? "No stopped AI cars found for " + track.get().name() + "." : "Started " + count + " AI cars for " + track.get().name() + ".");
        return count;
    }

    private static int stopAiFleet(CommandContext<CommandSourceStack> context) {
        Optional<TrackDefinition> track = activeTrack(context);
        if (track.isEmpty()) {
            send(context, "AI fleet stop refused: no active track in this dimension.");
            return 0;
        }
        int count = BasicAiFleetManager.stop(context.getSource().getLevel(), track.get().trackId());
        send(context, count == 0 ? "No running AI cars found for " + track.get().name() + "." : "Stopping " + count + " AI cars for " + track.get().name() + ".");
        return count;
    }

    private static int despawnAiFleet(CommandContext<CommandSourceStack> context) {
        Optional<TrackDefinition> track = activeTrack(context);
        if (track.isEmpty()) {
            send(context, "AI fleet despawn refused: no active track in this dimension.");
            return 0;
        }
        int count = BasicAiFleetManager.despawn(context.getSource().getLevel(), track.get().trackId());
        send(context, count == 0 ? "No AI-owned cars found for " + track.get().name() + "." : "Despawned " + count + " AI-owned cars for " + track.get().name() + ".");
        return count;
    }

    private static int setAiFleetMode(CommandContext<CommandSourceStack> context) {
        String value = StringArgumentType.getString(context, "mode");
        Optional<BasicAiTrafficMode> mode = BasicAiTrafficMode.parse(value);
        if (mode.isEmpty()) {
            send(context, "Unknown AI mode " + value + ". Use auto, race, formation, vsc, safety_car, or hold.");
            return 0;
        }
        BasicAiFleetManager.setModeOverride(mode.get());
        send(context, "AI fleet mode override set to " + mode.get().name().toLowerCase(java.util.Locale.ROOT) + ".");
        return 1;
    }

    private static int showAiTrainingStatus(CommandContext<CommandSourceStack> context) {
        Optional<TrackDefinition> track = activeTrack(context);
        if (track.isEmpty()) {
            send(context, "AI training unavailable: no active track.");
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        Optional<SurveyRoute> survey = TrackSurveyData.get(level).get(track.get().trackId());
        if (survey.isEmpty()) {
            send(context, "AI training unavailable: no active survey.");
            return 0;
        }
        OWRAiTrainingData.Record record = OWRAiTrainingData.get(level).getOrCreate(track.get().trackId(), survey.get().routeId(), "prototype_default");
        send(context, "AI training track=" + track.get().name() + " enabled=" + record.enabled() + " laps=" + record.validLaps()
            + " rejected=" + record.rejectedLaps() + " recoveries=" + record.recoveries() + " best=" + record.bestLapMillis()
            + " targetScale=" + String.format(java.util.Locale.ROOT, "%.3f", record.targetScale())
            + " brakingScale=" + String.format(java.util.Locale.ROOT, "%.3f", record.brakingScale()) + ".");
        return 1;
    }

    private static int showAiCalibrationStatus(CommandContext<CommandSourceStack> context) {
        Optional<TrackDefinition> track = activeTrack(context);
        if (track.isEmpty()) {
            send(context, "AI calibration unavailable: no active track.");
            return 0;
        }
        String status = BasicAiFleetManager.calibrationStatus(context.getSource().getLevel(), track.get().trackId());
        send(context, "AI calibration " + status + ".");
        return 1;
    }

    private static int resetAiCalibration(CommandContext<CommandSourceStack> context) {
        Optional<TrackDefinition> track = activeTrack(context);
        if (track.isEmpty()) return 0;
        BasicAiFleetManager.resetCalibration(track.get().trackId());
        send(context, "Reset deterministic AI calibration cache for " + track.get().name() + ".");
        return 1;
    }

    private static int setAiCalibrationStep(CommandContext<CommandSourceStack> context) {
        double percent = DoubleArgumentType.getDouble(context, "percent");
        BasicAiFleetManager.setCalibrationStepPercent(percent);
        send(context, String.format(java.util.Locale.ROOT,
            "AI calibration maximum promotion step set to %.3f%%; active trials reset.", percent));
        return 1;
    }

    private static int setAiCalibrationRandom(CommandContext<CommandSourceStack> context, boolean enabled) {
        BasicAiFleetManager.setCalibrationRandomSteps(enabled);
        send(context, "AI calibration random-step mode " + (enabled ? "enabled" : "disabled") + "; active trials reset.");
        return 1;
    }

    private static int showAiTrainingTime(CommandContext<CommandSourceStack> context) {
        return showAiTrainingStatus(context);
    }

    private static int clearAiTraining(CommandContext<CommandSourceStack> context) {
        Optional<TrackDefinition> track = activeTrack(context);
        if (track.isEmpty()) {
            send(context, "AI training clear refused: no active track.");
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        Optional<SurveyRoute> survey = TrackSurveyData.get(level).get(track.get().trackId());
        int removed = survey.map(route -> OWRAiTrainingData.get(level).clear(track.get().trackId(), route.routeId())).orElse(0);
        BasicAiFleetManager.clearTrainingRuntime();
        send(context, "Cleared " + removed + " AI training record(s).");
        return 1;
    }

    private static int setAiTraining(CommandContext<CommandSourceStack> context, boolean enabled) {
        Optional<TrackDefinition> track = activeTrack(context);
        if (track.isEmpty()) return 0;
        ServerLevel level = context.getSource().getLevel();
        Optional<SurveyRoute> survey = TrackSurveyData.get(level).get(track.get().trackId());
        if (survey.isEmpty()) return 0;
        OWRAiTrainingData data = OWRAiTrainingData.get(level);
        OWRAiTrainingData.Record record = data.getOrCreate(track.get().trackId(), survey.get().routeId(), "prototype_default").withEnabled(enabled);
        data.save(record);
        send(context, "AI training " + (enabled ? "enabled" : "disabled") + ".");
        return 1;
    }

    private static int hideAiLine(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        OWRNetwork.sendAiRacingLineOverlay(player, false, "", new UUID(0, 0), "planned", List.of());
        return 1;
    }

    private static int showAiLine(CommandContext<CommandSourceStack> context, String source) {
        ServerPlayer player = context.getSource().getPlayer();
        Optional<TrackDefinition> track = activeTrack(context);
        if (player == null || track.isEmpty()) return 0;
        ServerLevel level = context.getSource().getLevel();
        List<com.openwheelracing.content.ai.BasicAiFleetManager.RacingLineTrace> traces = switch (source) {
            case "current" -> BasicAiFleetManager.currentLineTraces(level, track.get().trackId());
            case "player" -> BasicAiFleetManager.playerBestLineTrace(level, track.get().trackId(), player.getUUID());
            default -> BasicAiFleetManager.plannedLineTraces(level, track.get().trackId());
        };
        List<OWRNetwork.AiRacingLineStrip> strips = new java.util.ArrayList<>();
        for (int traceIndex = 0; traceIndex < traces.size(); traceIndex++) {
            com.openwheelracing.content.ai.BasicAiFleetManager.RacingLineTrace trace = traces.get(traceIndex);
            double[] x = trace.points().stream().mapToDouble(com.openwheelracing.content.race.LapProfileCollector.TracePoint::x).toArray();
            double[] y = trace.points().stream().mapToDouble(com.openwheelracing.content.race.LapProfileCollector.TracePoint::y).toArray();
            double[] z = trace.points().stream().mapToDouble(com.openwheelracing.content.race.LapProfileCollector.TracePoint::z).toArray();
            int color = source.equals("current") ? currentLineColor(traceIndex) : 0xFFFF8A3D;
            strips.add(new OWRNetwork.AiRacingLineStrip(trace.id(), trace.label(), trace.closed(), color, x, y, z));
        }
        OWRNetwork.sendAiRacingLineOverlay(player, true, level.dimension().identifier().toString(), track.get().trackId(), source, strips);
        if (source.equals("player") && strips.isEmpty()) {
            send(context, "No saved best player racing line for the active survey route. Complete a valid lap with at least 97% route coverage.");
            return 0;
        }
        send(context, "Showing " + strips.size() + " " + source + " racing line" + (strips.size() == 1 ? "" : "s") + ".");
        return strips.size();
    }

    private static int currentLineColor(int index) {
        int[] colors = {0xFF42D4F4, 0xFFF032E6, 0xFFBFEF45, 0xFF4363D8, 0xFFF58231, 0xFF911EB4};
        return colors[Math.floorMod(index, colors.length)];
    }

    private static int showAiFleetStatus(CommandContext<CommandSourceStack> context) {
        Optional<TrackDefinition> track = activeTrack(context);
        if (track.isEmpty()) {
            send(context, "AI fleet status unavailable: no active track in this dimension.");
            return 0;
        }
        List<BasicAiStatus> statuses = BasicAiFleetManager.statuses(context.getSource().getLevel(), track.get().trackId());
        if (statuses.isEmpty()) {
            send(context, "No AI-owned cars found for " + track.get().name() + ".");
            return 0;
        }
        UUID fleetId = statuses.getFirst().fleetId();
        send(context, "AI fleet " + fleetId + " cars=" + statuses.size() + " track=" + track.get().name()
            + " mode=" + BasicAiFleetManager.mode(context.getSource().getLevel()).name().toLowerCase(java.util.Locale.ROOT)
            + " override=" + BasicAiFleetManager.modeOverride().name().toLowerCase(java.util.Locale.ROOT)
            + " forcedChunks=" + BasicAiFleetChunkTickets.totalTicketCount() + "/" + BasicAiFleetChunkTickets.MAX_TOTAL_CHUNKS
            + " ticketDenials=" + BasicAiFleetChunkTickets.deniedAcquisitions() + ".");
        for (BasicAiStatus status : statuses) {
            String gap = Double.isFinite(status.nearestAheadGap()) ? String.format(java.util.Locale.ROOT, "%.1fm", status.nearestAheadGap()) : "none";
            send(context, "%s entity=%d grid=%d %s loc=%s conf=%.2f route=%.1fm laps=%d speed=%.1fkm/h gap=%s reason=%s".formatted(
                status.displayName(), status.entityId(), status.gridIndex(), status.running() ? "running" : "stopped", status.localizationStatus(),
                status.confidence(), status.routeDistance(), status.routeLaps(), status.speedKmh(), gap, status.reason()));
        }
        return statuses.size();
    }

    private static Optional<TrackDefinition> activeTrack(CommandContext<CommandSourceStack> context) {
        return trackData(context).activeTrack(dimensionId(context));
    }

    private static int startSurvey(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TrackDefinition track = activeOrDefaultTrack(context);
        if (!(player.getVehicle() instanceof OpenwheelCarEntity car) || car.getControllingPassenger() != player) {
            send(context, "Drive an open-wheel car before starting a survey.");
            return 0;
        }
        if (!SurveyRouteRuntime.start(player, car, track)) {
            send(context, "A survey recording is already active.");
            return 0;
        }
        send(context, "Survey armed for " + track.name() + ". Recording begins when this car next starts a lap.");
        return 1;
    }

    private static int finishSurvey(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        SurveyRouteRuntime.FinishResult result = SurveyRouteRuntime.finish(player);
        if (result instanceof SurveyRouteRuntime.FinishFailure failure) {
            send(context, "Survey not saved: " + failure.reason() + ". Survey remains active.");
            return 0;
        }
        SurveyRoute route = ((SurveyRouteRuntime.FinishSuccess) result).route();
        send(context, "Survey saved: raw=" + route.rawSamples().size() + " nodes=" + route.nodes().size() + " length=" + Math.round(route.length()) + "m.");
        return route.nodes().size();
    }

    private static int cancelSurvey(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean cancelled = SurveyRouteRuntime.cancel(player);
        send(context, cancelled ? "Survey recording cancelled." : "No active survey recording.");
        return cancelled ? 1 : 0;
    }

    private static int showSurveyStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<SurveyRouteRuntime.Status> recording = SurveyRouteRuntime.status(player);
        if (recording.isPresent()) {
            SurveyRouteRuntime.Status status = recording.get();
            if (!status.recording()) {
                send(context, "Survey armed for " + status.trackName() + ". Cross start/finish to begin recording.");
                return 1;
            }
            send(context, "Survey recording " + status.trackName() + ": samples=" + status.samples() + " distance=" + Math.round(status.distance()) + "m closure=" + Math.round(status.closureGap()) + "m.");
            return status.samples();
        }
        TrackDefinition track = activeOrDefaultTrack(context);
        Optional<SurveyRoute> route = TrackSurveyData.get(context.getSource().getLevel()).get(track.trackId());
        if (route.isEmpty()) {
            send(context, "No survey route saved for " + track.name() + ".");
            return 0;
        }
        send(context, "Survey " + track.name() + ": raw=" + route.get().rawSamples().size() + " nodes=" + route.get().nodes().size() + " length=" + Math.round(route.get().length()) + "m.");
        return route.get().nodes().size();
    }

    private static int clearSurvey(CommandContext<CommandSourceStack> context) {
        TrackDefinition track = activeOrDefaultTrack(context);
        boolean cleared = TrackSurveyData.get(context.getSource().getLevel()).clear(track.trackId());
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            OWRNetwork.sendSurveyRouteOverlay(player, false, "", new UUID(0L, 0L), "", false, null);
        }
        send(context, cleared ? "Cleared saved survey route for " + track.name() + "." : "No saved survey route for " + track.name() + ".");
        return cleared ? 1 : 0;
    }

    private static int showSurvey(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<SurveyRouteRuntime.RecorderView> recording = SurveyRouteRuntime.recorder(player);
        if (recording.isPresent()) {
            SurveyRouteRuntime.RecorderView view = recording.get();
            SurveyRoute live = SurveyRoute.fromModel(new com.openwheelracing.content.track.survey.SurveyRouteModel(UUID.randomUUID(), view.trackId(), view.samples(), List.of(), view.distance(), 2.0));
            OWRNetwork.sendSurveyRouteOverlay(player, true, view.dimensionId(), view.trackId(), view.trackName(), view.recording(), live);
            send(context, "Showing active survey recording.");
            return view.samples().size();
        }
        TrackDefinition track = activeOrDefaultTrack(context);
        Optional<SurveyRoute> route = TrackSurveyData.get(context.getSource().getLevel()).get(track.trackId());
        if (route.isEmpty()) {
            send(context, "No survey route saved for " + track.name() + ".");
            return 0;
        }
        OWRNetwork.sendSurveyRouteOverlay(player, true, dimensionId(context), track.trackId(), track.name(), false, route.get());
        send(context, "Showing survey route for " + track.name() + ".");
        return route.get().nodes().size();
    }

    private static int hideSurvey(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        OWRNetwork.sendSurveyRouteOverlay(context.getSource().getPlayerOrException(), false, "", new UUID(0, 0), "", false, null);
        send(context, "Survey route overlay hidden.");
        return 1;
    }

    private static int setPitLimitHere(CommandContext<CommandSourceStack> context, TrackDefinition.StewardLineType type, int width) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TrackDefinition.StartFinishLine line = lineAtPlayer(player, width);
        TrackDefinition.Point3 left = raiseStewardLinePoint(line.left());
        TrackDefinition.Point3 right = raiseStewardLinePoint(line.right());
        TrackDefinition track = activeOrDefaultTrack(context);
        Vec3 center = new Vec3((left.x() + right.x()) * 0.5, (left.y() + right.y()) * 0.5, (left.z() + right.z()) * 0.5);
        double distance = TrackGeometry.sample(track, center).map(TrackGeometry.ProgressSample::distanceAlongTrack).orElse(0.0);
        return upsertStewardLine(context, new TrackDefinition.StewardLine(type, 1, type.displayName() + " 1",
            left, right, line.headingRadians(), distance));
    }

    private static int startPitLaneSurvey(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!(player.getVehicle() instanceof OpenwheelCarEntity car) || car.getControllingPassenger() != player) {
            send(context, "Drive an open-wheel car before starting a pit-lane survey.");
            return 0;
        }
        TrackDefinition track = activeOrDefaultTrack(context);
        if (!PitLaneSurveyRuntime.start(player, car, track)) {
            send(context, "A pit-lane survey is already active.");
            return 0;
        }
        send(context, "Pit-lane survey armed. Recording starts at pit entry and saves automatically at pit exit.");
        return 1;
    }

    private static int finishPitLaneSurvey(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PitLaneSurveyRuntime.Finish finish = PitLaneSurveyRuntime.finish(context.getSource().getPlayerOrException());
        send(context, finish.message() + " (samples=" + finish.samples() + ").");
        return finish.success() ? finish.samples() : 0;
    }

    private static int cancelPitLaneSurvey(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        boolean cancelled = PitLaneSurveyRuntime.cancel(context.getSource().getPlayerOrException());
        send(context, cancelled ? "Pit-lane survey cancelled." : "No active pit-lane survey.");
        return cancelled ? 1 : 0;
    }

    private static int showPitLaneSurveyStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Optional<PitLaneSurveyRuntime.Status> recording = PitLaneSurveyRuntime.status(context.getSource().getPlayerOrException());
        if (recording.isPresent()) {
            send(context, "Pit-lane survey " + (recording.get().recording() ? "recording " : "armed ") + recording.get().trackName() + ": samples=" + recording.get().samples() + ".");
            return recording.get().samples();
        }
        TrackDefinition track = activeOrDefaultTrack(context);
        Optional<PitLaneSurveyData.Route> route = PitLaneSurveyData.get(context.getSource().getLevel()).get(track.trackId());
        send(context, route.map(value -> "Saved pit-lane survey: samples=" + value.points().size() + ".").orElse("No saved pit-lane survey."));
        return route.map(value -> value.points().size()).orElse(0);
    }

    private static int clearPitLaneSurvey(CommandContext<CommandSourceStack> context) {
        TrackDefinition track = activeOrDefaultTrack(context);
        boolean cleared = PitLaneSurveyData.get(context.getSource().getLevel()).clear(track.trackId());
        send(context, cleared ? "Pit-lane survey cleared." : "No saved pit-lane survey.");
        return cleared ? 1 : 0;
    }

    private static int setWheelInputAllowed(CommandContext<CommandSourceStack> context, boolean allowed) {
        OWRRaceControlState state = raceControl(context);
        state.setWheelInputAllowed(allowed);
        send(context, "Wheel and joystick input is now " + (allowed ? "allowed" : "forbidden") + ".");
        return allowed ? 1 : 0;
    }

    private static int showWheelInputStatus(CommandContext<CommandSourceStack> context) {
        boolean allowed = raceControl(context).isWheelInputAllowed();
        send(context, "Wheel and joystick input is " + (allowed ? "allowed" : "forbidden") + ".");
        return allowed ? 1 : 0;
    }

    private static int listTracks(CommandContext<CommandSourceStack> context) {
        TrackDefinitionsData data = trackData(context);
        List<TrackDefinition> tracks = data.tracksInDimension(dimensionId(context));
        if (tracks.isEmpty()) {
            send(context, "No stewarding tracks defined. Use /owr steward create <name>.");
            return 0;
        }
        send(context, "Stewarding tracks:");
        for (TrackDefinition track : tracks) {
            String active = data.activeTrack(dimensionId(context)).filter(track::equals).isPresent() ? " *" : "";
            Optional<SurveyRoute> survey = TrackSurveyData.get(context.getSource().getLevel()).get(track.trackId());
            String surveyStatus = survey.map(route -> " survey=" + Math.round(route.length()) + "m/" + route.nodes().size() + " nodes").orElse(" survey=none");
            send(context, "- " + track.name() + " [" + track.trackId() + "]" + active + " centerline=" + Math.round(track.length()) + "m/" + track.centerline().size() + " nodes" + surveyStatus);
        }
        return tracks.size();
    }

    private static int showActiveTrack(CommandContext<CommandSourceStack> context) {
        TrackDefinition track = activeOrDefaultTrack(context);
        Optional<SurveyRoute> survey = TrackSurveyData.get(context.getSource().getLevel()).get(track.trackId());
        String surveyStatus = survey.map(route -> Math.round(route.length()) + "m/" + route.nodes().size() + " nodes").orElse("none");
        send(context, "Active stewarding track: " + track.name() + " [" + track.trackId() + "] centerline=" + Math.round(track.length()) + "m/" + track.centerline().size() + " nodes survey=" + surveyStatus);
        return 1;
    }

    private static int createTrack(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name").trim();
        if (name.isEmpty()) {
            send(context, "Track name cannot be blank.");
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        TrackDefinition track = trackData(context).createEmpty(name, level.dimension().identifier().toString());
        send(context, "Created stewarding track " + track.name() + " [" + track.trackId() + "] and selected it.");
        return 1;
    }

    private static int selectTrack(CommandContext<CommandSourceStack> context) {
        UUID trackId = parseTrackId(context, "trackId");
        if (trackId == null) {
            return 0;
        }
        if (!trackData(context).setActiveTrack(trackId, dimensionId(context))) {
            send(context, "Unknown stewarding track in this dimension: " + trackId);
            return 0;
        }
        send(context, "Selected stewarding track " + trackId + ".");
        return 1;
    }

    private static int removeTrack(CommandContext<CommandSourceStack> context) {
        UUID trackId = parseTrackId(context, "trackId");
        if (trackId == null) {
            return 0;
        }
        if (!trackData(context).remove(trackId, dimensionId(context))) {
            send(context, "Unknown stewarding track in this dimension: " + trackId);
            return 0;
        }
        send(context, "Removed stewarding track " + trackId + ".");
        return 1;
    }

    private static int addCenterlinePoint(CommandContext<CommandSourceStack> context, int width) throws CommandSyntaxException {
        TrackDefinitionsData data = trackData(context);
        Optional<TrackDefinition> active = data.activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track. Use /owr steward create <name> first.");
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos pos = BlockPos.containing(player.position());
        TrackDefinition track = active.get();
        List<BlockPos> points = centerlineAsBlockPoints(track);
        points.add(pos);
        TrackDefinition updated = track.withCenterline(TrackStewardingGeometryBuilder.centerlineFromPath(points, width));
        if (updated.centerline().size() >= 2) {
            updated = updated.withStartFinish(TrackStewardingGeometryBuilder.startFinishFromFirstSegment(updated.centerline()));
        }
        data.upsert(updated.withAiLine(TrackStewardingGeometryBuilder.aiLineFromCenterline(updated.centerline())));
        send(context, "Added centerline point " + pos.toShortString() + " to " + updated.name() + " nodes=" + updated.centerline().size() + ".");
        return updated.centerline().size();
    }

    private static int clearCenterline(CommandContext<CommandSourceStack> context) {
        TrackDefinitionsData data = trackData(context);
        Optional<TrackDefinition> active = data.activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        TrackDefinition track = active.get();
        data.upsert(track.withCenterline(List.of()).withAiLine(List.of()));
        send(context, "Cleared centerline and generated AI line for " + track.name() + ".");
        return 1;
    }

    private static int finishCenterline(CommandContext<CommandSourceStack> context) {
        TrackDefinitionsData data = trackData(context);
        Optional<TrackDefinition> active = data.activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        TrackDefinition track = active.get();
        if (track.centerline().size() < 2) {
            send(context, "Centerline needs at least two points before it can be finished.");
            return 0;
        }
        TrackDefinition updated = track.withStartFinish(TrackStewardingGeometryBuilder.startFinishFromFirstSegment(track.centerline()))
            .withAiLine(TrackStewardingGeometryBuilder.aiLineFromCenterline(track.centerline()));
        data.upsert(updated);
        send(context, "Finished stewarding centerline for " + updated.name() + " length=" + Math.round(updated.length()) + "m nodes=" + updated.centerline().size() + ".");
        return updated.centerline().size();
    }

    private static int setStartFinishHere(CommandContext<CommandSourceStack> context, int width) throws CommandSyntaxException {
        TrackDefinitionsData data = trackData(context);
        TrackDefinition track = activeOrDefaultTrack(context);
        ServerPlayer player = context.getSource().getPlayerOrException();
        TrackDefinition updated = track.withStartFinish(lineAtPlayer(player, width));
        data.upsert(updated);
        send(context, "Set start/finish for " + updated.name() + " at " + BlockPos.containing(player.position()).toShortString() + ".");
        return 1;
    }

    private static int addCheckpointHere(CommandContext<CommandSourceStack> context, int width) throws CommandSyntaxException {
        TrackDefinitionsData data = trackData(context);
        TrackDefinition track = activeOrDefaultTrack(context);
        ServerPlayer player = context.getSource().getPlayerOrException();
        int index = track.checkpoints().stream().mapToInt(TrackDefinition.Checkpoint::index).max().orElse(0) + 1;
        TrackDefinition.StartFinishLine line = lineAtPlayer(player, width);
        List<TrackDefinition.Checkpoint> checkpoints = new ArrayList<>(track.checkpoints());
        checkpoints.add(new TrackDefinition.Checkpoint(index, "CP " + index, line.left(), line.right(), true));
        TrackDefinition updated = track.withCheckpoints(checkpoints);
        data.upsert(updated);
        send(context, "Added checkpoint " + index + " to " + updated.name() + " at " + BlockPos.containing(player.position()).toShortString() + ".");
        return index;
    }

    private static int clearCheckpoints(CommandContext<CommandSourceStack> context) {
        TrackDefinitionsData data = trackData(context);
        Optional<TrackDefinition> active = data.activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        TrackDefinition track = active.get();
        data.upsert(track.withCheckpoints(List.of()));
        send(context, "Cleared checkpoints for " + track.name() + ".");
        return 1;
    }

    private static int addGridSlotHere(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Optional<TrackDefinition> active = trackData(context).activeTrack(dimensionId(context));
        int index = active.map(track -> track.gridSlots().stream().mapToInt(TrackDefinition.GridSlot::index).max().orElse(0) + 1).orElse(1);
        return addGridSlotHere(context, index);
    }

    private static int addGridSlotHere(CommandContext<CommandSourceStack> context, int index) throws CommandSyntaxException {
        TrackDefinitionsData data = trackData(context);
        Optional<TrackDefinition> active = data.activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayerOrException();
        TrackDefinition track = active.get();
        List<TrackDefinition.GridSlot> gridSlots = new ArrayList<>(track.gridSlots().stream().filter(slot -> slot.index() != index).toList());
        gridSlots.add(new TrackDefinition.GridSlot(index, pointAtPlayer(player), headingFromPlayer(player)));
        gridSlots.sort(java.util.Comparator.comparingInt(TrackDefinition.GridSlot::index));
        TrackDefinition updated = track.withGridSlots(gridSlots);
        data.upsert(updated);
        send(context, "Set grid slot " + index + " for " + updated.name() + " at " + BlockPos.containing(player.position()).toShortString() + ".");
        return index;
    }

    private static int clearGridSlots(CommandContext<CommandSourceStack> context) {
        TrackDefinitionsData data = trackData(context);
        Optional<TrackDefinition> active = data.activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        TrackDefinition track = active.get();
        data.upsert(track.withGridSlots(List.of()));
        send(context, "Cleared grid slots for " + track.name() + ".");
        return 1;
    }

    private static int addBoundaryHere(CommandContext<CommandSourceStack> context, TrackDefinition.BoundarySide side) throws CommandSyntaxException {
        TrackDefinitionsData data = trackData(context);
        Optional<TrackDefinition> active = data.activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayerOrException();
        TrackDefinition track = active.get();
        double distance = TrackGeometry.sample(track, player.position()).map(TrackGeometry.ProgressSample::distanceAlongTrack).orElse(0.0);
        List<TrackDefinition.BoundarySample> boundaries = new ArrayList<>(track.boundaries());
        boundaries.add(new TrackDefinition.BoundarySample(side, pointAtPlayer(player), distance));
        TrackDefinition updated = track.withBoundaries(boundaries);
        data.upsert(updated);
        send(context, "Added " + side.name().toLowerCase(java.util.Locale.ROOT) + " boundary sample to " + updated.name() + " at " + BlockPos.containing(player.position()).toShortString() + ".");
        return boundaries.size();
    }

    private static int clearBoundaries(CommandContext<CommandSourceStack> context) {
        TrackDefinitionsData data = trackData(context);
        Optional<TrackDefinition> active = data.activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        TrackDefinition track = active.get();
        data.upsert(track.withBoundaries(List.of()));
        send(context, "Cleared boundary samples for " + track.name() + ".");
        return 1;
    }

    private static int setStewardLine(CommandContext<CommandSourceStack> context) {
        TrackDefinition.Point3 left = new TrackDefinition.Point3(
            DoubleArgumentType.getDouble(context, "x1"),
            DoubleArgumentType.getDouble(context, "y1"),
            DoubleArgumentType.getDouble(context, "z1")
        );
        TrackDefinition.Point3 right = new TrackDefinition.Point3(
            DoubleArgumentType.getDouble(context, "x2"),
            DoubleArgumentType.getDouble(context, "y2"),
            DoubleArgumentType.getDouble(context, "z2")
        );
        TrackDefinition.StewardLineType type = parseStewardLineType(context);
        if (type == null) {
            return 0;
        }
        return upsertStewardLine(context, createStewardLine(context, type, IntegerArgumentType.getInteger(context, "index"), left, right));
    }

    private static int addStewardLineHere(CommandContext<CommandSourceStack> context, int width) throws CommandSyntaxException {
        TrackDefinition.StewardLineType type = parseStewardLineType(context);
        if (type == null) {
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayerOrException();
        TrackDefinition.StartFinishLine line = lineAtPlayer(player, width);
        return upsertStewardLine(context, createStewardLine(context, type, IntegerArgumentType.getInteger(context, "index"), line.left(), line.right()));
    }

    private static TrackDefinition.StewardLine createStewardLine(CommandContext<CommandSourceStack> context, TrackDefinition.StewardLineType type, int index, TrackDefinition.Point3 left, TrackDefinition.Point3 right) {
        TrackDefinition.Point3 raisedLeft = raiseStewardLinePoint(left);
        TrackDefinition.Point3 raisedRight = raiseStewardLinePoint(right);
        double centerX = (raisedLeft.x() + raisedRight.x()) * 0.5;
        double centerY = (raisedLeft.y() + raisedRight.y()) * 0.5;
        double centerZ = (raisedLeft.z() + raisedRight.z()) * 0.5;
        Vec3 center = new Vec3(centerX, centerY, centerZ);
        TrackDefinition track = activeOrDefaultTrack(context);
        double distance = track == null ? 0.0 : TrackGeometry.sample(track, center).map(TrackGeometry.ProgressSample::distanceAlongTrack).orElse(0.0);
        double lineAngle = Math.atan2(raisedRight.z() - raisedLeft.z(), raisedRight.x() - raisedLeft.x());
        double heading = lineAngle - Math.PI * 0.5;
        return new TrackDefinition.StewardLine(type, index, type.displayName() + " " + index, raisedLeft, raisedRight, heading, distance);
    }

    private static TrackDefinition.Point3 raiseStewardLinePoint(TrackDefinition.Point3 point) {
        return new TrackDefinition.Point3(point.x(), point.y() + 0.5, point.z());
    }

    private static int upsertStewardLine(CommandContext<CommandSourceStack> context, TrackDefinition.StewardLine line) {
        TrackDefinitionsData data = trackData(context);
        TrackDefinition track = activeOrDefaultTrack(context);
        List<TrackDefinition.StewardLine> lines = new ArrayList<>(track.stewardLines().stream()
            .filter(existing -> existing.type() != line.type() || existing.index() != line.index())
            .toList());
        lines.add(line);
        lines.sort(java.util.Comparator.comparing(TrackDefinition.StewardLine::type).thenComparingInt(TrackDefinition.StewardLine::index));
        data.upsert(track.withStewardLines(lines));
        send(context, "Set " + line.type().serializedName() + " " + line.index() + " for " + track.name() + ".");
        return line.index();
    }

    private static int listStewardLines(CommandContext<CommandSourceStack> context) {
        TrackDefinition track = activeOrDefaultTrack(context);
        List<TrackDefinition.StewardLine> lines = track.stewardLines();
        if (lines.isEmpty()) {
            send(context, "No stewarding lines defined for " + track.name() + ".");
            return 0;
        }
        send(context, "Stewarding lines for " + track.name() + ":");
        for (TrackDefinition.StewardLine line : lines) {
            send(context, "- " + line.type().serializedName() + " " + line.index() + " [" + formatPoint(line.left()) + " -> " + formatPoint(line.right()) + "]");
        }
        return lines.size();
    }

    private static int removeStewardLine(CommandContext<CommandSourceStack> context) {
        TrackDefinition.StewardLineType type = parseStewardLineType(context);
        if (type == null) {
            return 0;
        }
        TrackDefinitionsData data = trackData(context);
        Optional<TrackDefinition> active = data.activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        int index = IntegerArgumentType.getInteger(context, "index");
        TrackDefinition track = active.get();
        List<TrackDefinition.StewardLine> lines = track.stewardLines().stream()
            .filter(line -> line.type() != type || line.index() != index)
            .toList();
        if (lines.size() == track.stewardLines().size()) {
            send(context, "No " + type.serializedName() + " " + index + " exists.");
            return 0;
        }
        data.upsert(track.withStewardLines(lines));
        send(context, "Removed " + type.serializedName() + " " + index + ".");
        return 1;
    }

    private static int clearStewardLines(CommandContext<CommandSourceStack> context) {
        TrackDefinitionsData data = trackData(context);
        Optional<TrackDefinition> active = data.activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        data.upsert(active.get().withStewardLines(List.of()));
        send(context, "Cleared stewarding lines for " + active.get().name() + ".");
        return 1;
    }

    private static int setStewardLineVisibility(CommandContext<CommandSourceStack> context, boolean visible) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!visible) {
            OWRNetwork.sendStewardLineOverlay(player, false, null, 0);
            send(context, "Stewarding lines hidden for your client.");
            return 1;
        }
        TrackDefinitionsData data = trackData(context);
        TrackDefinition track = activeOrDefaultTrack(context);
        OWRNetwork.sendStewardLineOverlay(player, true, track, data.getRevision());
        send(context, "Showing " + track.stewardLines().size() + " stewarding lines for your client.");
        return track.stewardLines().size();
    }

    private static TrackDefinition.StewardLineType parseStewardLineType(CommandContext<CommandSourceStack> context) {
        String value = StringArgumentType.getString(context, "type");
        try {
            return TrackDefinition.StewardLineType.fromSerializedName(value);
        } catch (IllegalArgumentException ignored) {
            send(context, "Unknown line type: " + value + ". Use checkpoint, sector_split, pit_limit_start, pit_limit_end, safety_car_line, drs_detection, or drs_activation.");
            return null;
        }
    }

    private static String formatPoint(TrackDefinition.Point3 point) {
        return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", point.x(), point.y(), point.z());
    }

    private static int generateAiLine(CommandContext<CommandSourceStack> context) {
        TrackDefinitionsData data = trackData(context);
        Optional<TrackDefinition> active = data.activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        TrackDefinition track = active.get();
        if (track.centerline().size() < 2) {
            send(context, "Centerline needs at least two points before AI waypoints can be generated.");
            return 0;
        }
        TrackDefinition updated = track.withAiLine(TrackStewardingGeometryBuilder.aiLineFromCenterline(track.centerline()));
        data.upsert(updated);
        send(context, "Generated " + updated.aiLine().size() + " AI waypoints for " + updated.name() + ".");
        return updated.aiLine().size();
    }

    private static TrackDefinition.StartFinishLine lineAtPlayer(ServerPlayer player, int width) {
        TrackDefinition.Point3 center = pointAtPlayer(player);
        double heading = headingFromPlayer(player);
        double halfWidth = Math.max(1.0, width * 0.5);
        TrackDefinition.Point3 left = new TrackDefinition.Point3(center.x() - Math.sin(heading) * halfWidth, center.y(), center.z() + Math.cos(heading) * halfWidth);
        TrackDefinition.Point3 right = new TrackDefinition.Point3(center.x() + Math.sin(heading) * halfWidth, center.y(), center.z() - Math.cos(heading) * halfWidth);
        return new TrackDefinition.StartFinishLine(left, right, heading);
    }

    private static TrackDefinition.Point3 pointAtPlayer(ServerPlayer player) {
        Vec3 position = player.position();
        return new TrackDefinition.Point3(position.x, position.y, position.z);
    }

    private static double headingFromPlayer(ServerPlayer player) {
        return Math.toRadians(player.getYRot() + 90.0F);
    }

    private static List<BlockPos> centerlineAsBlockPoints(TrackDefinition track) {
        List<BlockPos> points = new ArrayList<>();
        for (TrackDefinition.CenterlineNode node : track.centerline()) {
            Vec3 pos = node.position().asVec3();
            points.add(BlockPos.containing(pos));
        }
        return points;
    }

    private static UUID parseTrackId(CommandContext<CommandSourceStack> context, String argument) {
        String value = StringArgumentType.getString(context, argument);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            send(context, "Invalid track id: " + value);
            return null;
        }
    }

    private static TrackDefinition activeOrDefaultTrack(CommandContext<CommandSourceStack> context) {
        TrackDefinitionsData data = trackData(context);
        String dimensionId = dimensionId(context);
        Optional<TrackDefinition> active = data.activeTrack(dimensionId);
        if (active.isPresent()) {
            return active.get();
        }
        List<TrackDefinition> tracks = data.tracksInDimension(dimensionId);
        if (!tracks.isEmpty()) {
            TrackDefinition selected = tracks.getFirst();
            data.setActiveTrack(selected.trackId(), dimensionId);
            send(context, "Selected stewarding track " + selected.name() + " [" + selected.trackId() + "].");
            return selected;
        }
        TrackDefinition created = data.createEmpty("Default Track", dimensionId);
        send(context, "Created and selected stewarding track " + created.name() + " [" + created.trackId() + "].");
        return created;
    }

    private static TrackDefinitionsData trackData(CommandContext<CommandSourceStack> context) {
        return TrackDefinitionsData.get(context.getSource().getLevel());
    }

    private static OWRRaceControlState raceControl(CommandContext<CommandSourceStack> context) {
        return OWRRaceControlState.get(context.getSource().getLevel());
    }

    private static String dimensionId(CommandContext<CommandSourceStack> context) {
        return context.getSource().getLevel().dimension().identifier().toString();
    }

    private static void send(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            OWRNetwork.sendCommandFeedback(player, message);
        }
    }
}
