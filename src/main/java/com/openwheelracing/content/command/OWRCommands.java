package com.openwheelracing.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.network.OWRNetwork;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackDefinitionsData;
import com.openwheelracing.content.track.TrackGeometry;
import com.openwheelracing.content.track.TrackStewardingGeometryBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
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
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.literal("regulation")
                .then(Commands.literal("wheel")
                    .then(Commands.literal("allow")
                        .executes(context -> setWheelInputAllowed(context, true)))
                    .then(Commands.literal("forbid")
                        .executes(context -> setWheelInputAllowed(context, false)))
                    .then(Commands.literal("status")
                        .executes(OWRCommands::showWheelInputStatus))))
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
            send(context, "- " + track.name() + " [" + track.trackId() + "]" + active + " nodes=" + track.centerline().size());
        }
        return tracks.size();
    }

    private static int showActiveTrack(CommandContext<CommandSourceStack> context) {
        Optional<TrackDefinition> active = trackData(context).activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        TrackDefinition track = active.get();
        send(context, "Active stewarding track: " + track.name() + " [" + track.trackId() + "] nodes=" + track.centerline().size() + " length=" + Math.round(track.length()) + "m");
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
        Optional<TrackDefinition> active = data.activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayerOrException();
        TrackDefinition track = active.get();
        TrackDefinition updated = track.withStartFinish(lineAtPlayer(player, width));
        data.upsert(updated);
        send(context, "Set start/finish for " + updated.name() + " at " + BlockPos.containing(player.position()).toShortString() + ".");
        return 1;
    }

    private static int addCheckpointHere(CommandContext<CommandSourceStack> context, int width) throws CommandSyntaxException {
        TrackDefinitionsData data = trackData(context);
        Optional<TrackDefinition> active = data.activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayerOrException();
        TrackDefinition track = active.get();
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
        TrackDefinition track = trackData(context).activeTrack(dimensionId(context)).orElse(null);
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
        Optional<TrackDefinition> active = data.activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        TrackDefinition track = active.get();
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
        Optional<TrackDefinition> active = trackData(context).activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        List<TrackDefinition.StewardLine> lines = active.get().stewardLines();
        if (lines.isEmpty()) {
            send(context, "No stewarding lines defined for " + active.get().name() + ".");
            return 0;
        }
        send(context, "Stewarding lines for " + active.get().name() + ":");
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
        Optional<TrackDefinition> active = data.activeTrack(dimensionId(context));
        if (active.isEmpty()) {
            send(context, "No active stewarding track selected.");
            return 0;
        }
        OWRNetwork.sendStewardLineOverlay(player, true, active.get(), data.getRevision());
        send(context, "Showing " + active.get().stewardLines().size() + " stewarding lines for your client.");
        return active.get().stewardLines().size();
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
