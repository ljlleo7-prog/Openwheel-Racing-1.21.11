package com.openwheelracing.content.race.weekend;

import com.openwheelracing.content.race.OWRGrandPrixRegistry;
import com.openwheelracing.content.race.OWRLapRecords;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.content.race.session.RaceSessionState;
import com.openwheelracing.content.race.session.RaceSessionSuspensionReason;
import com.openwheelracing.content.race.timing.LiveRaceTimingService;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackDefinitionsData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Keeps persisted weekend clocks, empty-server safety, world time, and live timing in sync. */
public final class GrandPrixWeekendService {
    private GrandPrixWeekendService() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!event.hasTime()) {
            return;
        }
        MinecraftServer server = event.getServer();
        OWRGrandPrixRegistry registry = OWRGrandPrixRegistry.get(server);
        int connectedPlayers = server.getPlayerList().getPlayerCount();
        Set<String> openDimensions = registry.weekends().stream()
            .filter(weekend -> weekend.state() == GrandPrixWeekend.EventState.OPEN)
            .map(GrandPrixWeekend::dimensionId).collect(Collectors.toUnmodifiableSet());
        for (GrandPrixWeekend weekend : registry.weekends()) {
            if (weekend.state() != GrandPrixWeekend.EventState.OPEN) {
                continue;
            }
            ServerLevel level = level(server, weekend.dimensionId());
            if (level == null) {
                continue;
            }
            long tick = level.getGameTime();
            var active = weekend.activeSession(tick);
            if (active.isEmpty()) {
                continue;
            }
            GrandPrixWeekend.SessionView session = active.get();
            if (session.state().isActive() && OWRRaceControlState.get(level).getGlobalFlag() == com.openwheelracing.content.race.RaceFlagMode.RED) {
                weekend.suspend(RaceSessionSuspensionReason.DIRECTOR, tick, null, "RED_FLAG");
                LiveRaceTimingService.stop(level, "RED_FLAG");
                resetCarTiming(level);
                registry.markDirty();
                session = weekend.activeSession(tick).orElseThrow();
            }
            if (session.state().isActive()) {
                weekend.observeServerTick(tick);
                if (connectedPlayers == 0 && weekend.onConnectedPlayerCountChanged(0, tick)) {
                    LiveRaceTimingService.stop(level, "EMPTY_SERVER");
                    registry.markDirty();
                } else {
                    // SavedData writes on the normal server save cadence; keeping it dirty makes that checkpoint exact.
                    registry.markDirty();
                }
                session = weekend.activeSession(tick).orElse(session);
                if (session.state() == RaceSessionState.STAGING || session.state() == RaceSessionState.FORMATION_COUNTDOWN
                    || session.state() == RaceSessionState.COUNTDOWN) {
                    holdGridCars(level, weekend, session);
                }
                if (session.state() == RaceSessionState.RUNNING && shouldFinishTimedSession(level, weekend, session)) {
                    weekend.finish(tick, null, "SERVER");
                    LiveRaceTimingService.stop(level, "TIME_EXPIRED");
                    registry.markDirty();
                    session = weekend.activeSession(tick).orElse(session);
                }
            }
            Set<UUID> eligible = weekend.entries().stream()
                .filter(entry -> entry.status() == GrandPrixWeekend.EntryStatus.ENTERED)
                .map(GrandPrixWeekend.Entry::driverId)
                .collect(Collectors.toUnmodifiableSet());
            if (session.state() == RaceSessionState.FORMATION_COUNTDOWN) {
                OWRRaceControlState.get(level).setStartPhase(FormationLapProcedure.redLightCount(session.countdownRemainingTicks()));
                if (session.countdownRemainingTicks() == 0L) {
                    weekend.releaseFormation(tick);
                    OWRRaceControlState.get(level).setStartPhase(FormationLapProcedure.GREEN_LIGHT_PHASE);
                    registry.markDirty();
                    session = weekend.activeSession(tick).orElseThrow();
                }
            }
            if (session.state() == RaceSessionState.FORMATION_LAP) {
                OWRRaceControlState.get(level).setStartPhase(FormationLapProcedure.GREEN_LIGHT_PHASE);
                var timing = LiveRaceTimingService.latestSnapshot(level);
                if (timing.isPresent() && timing.get().active() && timing.get().sessionId() == session.config().sessionId()
                    && !timing.get().rows().isEmpty()) {
                    var leader = timing.get().rows().getFirst();
                    if (leader.confidence().canInitiatePositionChange()
                        && FormationLapProcedure.leaderReturning(leader.absoluteProgressMeters(), timing.get().routeLengthMeters())) {
                        weekend.awaitRaceStart(tick);
                        OWRRaceControlState.get(level).setStartPhase(0);
                        registry.markDirty();
                        session = weekend.activeSession(tick).orElseThrow();
                    }
                }
            }
            if (session.state() == RaceSessionState.COUNTDOWN) {
                long total = weekend.countdownDurationTicks();
                long elapsed = Math.max(0L, total - session.countdownRemainingTicks());
                int phase = RaceStartProcedure.redLightCount(elapsed);
                OWRRaceControlState.get(level).setStartPhase(phase);
                if (session.countdownRemainingTicks() == 0L) {
                    LiveRaceTimingService.StartResult timing = startTiming(level, weekend, session, false);
                    if (timing.started()) {
                        weekend.start(tick, null, "SERVER");
                        OWRRaceControlState.get(level).setStartPhase(6);
                    } else {
                        weekend.suspend(RaceSessionSuspensionReason.DIRECTOR, tick, null, "SERVER");
                    }
                    registry.markDirty();
                    session = weekend.activeSession(tick).orElseThrow();
                }
            }
            if (session.state() == RaceSessionState.RUNNING) {
                OWRRaceControlState.get(level).setStartPhase(6);
            }
            if (session.state() == RaceSessionState.SUSPENDED) {
                OWRRaceControlState.get(level).setStartPhase(0);
            }
            long remaining = session.config().durationTicks() > 0L ? session.remainingTicks() : -1L;
            LiveRaceTimingService.updateWeekendContext(level, session.config().sessionId(), weekend.name(),
                hudPhase(session), eligible, remaining);
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (!openDimensions.contains(level.dimension().identifier().toString())) {
                LiveRaceTimingService.clearWeekendContext(level);
            }
        }
    }

    public static Optional<GrandPrixWeekend> gridWeekend(ServerLevel level) {
        return OWRGrandPrixRegistry.get(level.getServer()).weekends().stream()
            .filter(weekend -> weekend.state() == GrandPrixWeekend.EventState.OPEN
                && weekend.dimensionId().equals(level.dimension().identifier().toString()))
            .filter(weekend -> weekend.activeSession(level.getGameTime())
                .map(session -> session.config().type().usesGrid() && !session.state().isTerminal()).orElse(false))
            .findFirst();
    }

    public static boolean isGridSession(ServerLevel level, long sessionId) {
        return gridWeekend(level).flatMap(weekend -> weekend.activeSession(level.getGameTime()))
            .map(session -> session.config().sessionId() == sessionId).orElse(false);
    }

    public static boolean ownsStartLights(ServerLevel level) {
        return gridWeekend(level).isPresent();
    }

    public static boolean allowsLapRecording(ServerLevel level) {
        return gridWeekend(level).flatMap(weekend -> weekend.activeSession(level.getGameTime()))
            .map(session -> session.state() == RaceSessionState.RUNNING).orElse(true);
    }

    public static String hudPhase(GrandPrixWeekend.SessionView session) {
        RaceSessionState phase = session.state() == RaceSessionState.SUSPENDED ? session.suspendedFrom() : session.state();
        return switch (phase) {
            case FORMATION_COUNTDOWN, FORMATION_LAP -> "FORMATION_LAP";
            case AWAITING_START -> "AWAITING_START";
            case COUNTDOWN -> "RACE_COUNTDOWN";
            default -> session.config().type().name();
        };
    }

    /** One initialization path for both automatic lights-out and an explicit director start. */
    public static LiveRaceTimingService.StartResult startTiming(ServerLevel level, GrandPrixWeekend weekend,
                                                                GrandPrixWeekend.SessionView session, boolean formation) {
        Set<UUID> eligible = weekend.entries().stream()
            .filter(entry -> entry.status() == GrandPrixWeekend.EntryStatus.ENTERED)
            .map(GrandPrixWeekend.Entry::driverId).collect(Collectors.toUnmodifiableSet());
        OWRLapRecords.get(level).activateSession(session.config().sessionId(), session.config().name());
        int laps = !formation && session.config().format() == GrandPrixWeekend.SessionFormat.LAP_COUNT_RACE
            ? session.config().lapLimit() : 0;
        LiveRaceTimingService.StartResult result = LiveRaceTimingService.start(level, session.config().sessionId(),
            session.config().name(), laps, eligible,
            !formation && session.config().durationTicks() > 0L ? session.config().durationTicks() : -1L);
        if (result.started()) {
            resetCarTiming(level);
            if (!formation && session.config().type().usesGrid()) {
                LiveRaceTimingService.lockParticipantCars(level, session.config().sessionId());
            }
            LiveRaceTimingService.updateWeekendContext(level, session.config().sessionId(), weekend.name(),
                formation ? "FORMATION_LAP" : session.config().type().name(), eligible, -1L);
        }
        return result;
    }

    public static void resetCarTiming(ServerLevel level) {
        for (var entity : level.getAllEntities()) {
            if (entity instanceof OpenwheelCarEntity car && car.participatesInRaceTiming()) car.resetSessionLapTiming();
        }
    }

    public static void stageGridCars(ServerLevel level, GrandPrixWeekend weekend) {
        positionGridCars(level, weekend, weekend.activeSession(level.getGameTime()).orElseThrow(), true);
    }

    private static void holdGridCars(ServerLevel level, GrandPrixWeekend weekend, GrandPrixWeekend.SessionView session) {
        positionGridCars(level, weekend, session, false);
    }

    private static boolean atSlot(OpenwheelCarEntity car, TrackDefinition.GridSlot slot) {
        return com.openwheelracing.content.ai.AiGridApproach.evaluate(
            new com.openwheelracing.content.ai.AiGridApproach.Target(slot.position().x(), slot.position().y(), slot.position().z(), slot.headingRadians()),
            car.getX(), car.getY(), car.getZ(), Math.toRadians(car.getYRot() + 90.0f), car.getSpeedKmh() / 3.6).ready();
    }

    private static Map<UUID, OpenwheelCarEntity> gridCars(ServerLevel level) {
        Map<UUID, OpenwheelCarEntity> cars = new java.util.HashMap<>();
        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof OpenwheelCarEntity car) || !car.participatesInRaceTiming()) continue;
            UUID driver = car.getControllingPassenger() instanceof ServerPlayer player ? player.getUUID()
                : car.getBasicAiIdentity().map(com.openwheelracing.content.ai.BasicAiDriverIdentity::driverId).orElse(null);
            if (driver != null) cars.putIfAbsent(driver, car);
        }
        return cars;
    }

    private static void positionGridCars(ServerLevel level, GrandPrixWeekend weekend,
                                         GrandPrixWeekend.SessionView session, boolean initialPlacement) {
        TrackDefinition track = TrackDefinitionsData.get(level).get(weekend.trackId()).orElse(null);
        if (track == null || session.grid().isEmpty()) {
            return;
        }
        List<TrackDefinition.GridSlot> slots = track.gridSlots().stream()
            .sorted(Comparator.comparingInt(TrackDefinition.GridSlot::index)).toList();
        int count = Math.min(slots.size(), session.grid().size());
        Map<UUID, OpenwheelCarEntity> cars = gridCars(level);
        for (int index = 0; index < count; index++) {
            OpenwheelCarEntity car = cars.get(session.grid().get(index));
            if (car == null) continue;
            TrackDefinition.GridSlot slot = slots.get(index);
            if (!initialPlacement && !atSlot(car, slot)) continue;
            car.setPos(slot.position().x(), slot.position().y() + 0.02, slot.position().z());
            car.setYRot((float) Math.toDegrees(slot.headingRadians()) - 90.0f);
            car.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static boolean shouldFinishTimedSession(ServerLevel level, GrandPrixWeekend weekend,
                                                     GrandPrixWeekend.SessionView session) {
        GrandPrixWeekend.SessionConfig config = session.config();
        if (config.type() == GrandPrixWeekend.SessionType.PRACTICE) {
            return session.elapsedTicks() >= config.durationTicks();
        }
        if (config.type() != GrandPrixWeekend.SessionType.QUALIFYING) {
            // A timed race needs its leader-next-crossing chequered procedure and remains director-controlled here.
            return false;
        }
        int allowance = config.qualifyingAllowance();
        if (allowance > 0) {
            Map<UUID, Long> validLaps = OWRLapRecords.get(level).getValidSessionLaps(config.sessionId()).stream()
                .collect(Collectors.groupingBy(OWRLapRecords.LapRecord::driverId, Collectors.counting()));
            boolean everyoneDone = weekend.entries().stream()
                .filter(entry -> entry.status() == GrandPrixWeekend.EntryStatus.ENTERED)
                .allMatch(entry -> validLaps.getOrDefault(entry.driverId(), 0L) >= allowance);
            if (everyoneDone) {
                return true;
            }
        }
        return config.durationTicks() > 0L
            && session.elapsedTicks() >= config.durationTicks() + config.graceTicks();
    }

    private static ServerLevel level(MinecraftServer server, String dimensionId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimensionId)) {
                return level;
            }
        }
        return null;
    }

}
