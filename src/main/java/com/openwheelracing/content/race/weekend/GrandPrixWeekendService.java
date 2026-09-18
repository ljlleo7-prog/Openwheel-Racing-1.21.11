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
            if (session.state() != RaceSessionState.CONFIGURED && session.state() != RaceSessionState.OFFICIAL
                && session.state() != RaceSessionState.ABANDONED) {
                lockWorldTime(level, session.config().worldTime());
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
                if (session.state() == RaceSessionState.STAGING || session.state() == RaceSessionState.COUNTDOWN) {
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
            if (session.state() == RaceSessionState.COUNTDOWN) {
                long total = session.config().countdownTicks();
                long elapsed = Math.max(0L, total - session.countdownRemainingTicks());
                int phase = total == 0L ? 5 : Math.min(5, 1 + (int) (elapsed * 5L / total));
                OWRRaceControlState.get(level).setStartPhase(phase);
                if (session.countdownRemainingTicks() == 0L) {
                    OWRLapRecords.get(level).activateSession(session.config().sessionId(), session.config().name());
                    int timingLapLimit = session.config().format() == GrandPrixWeekend.SessionFormat.LAP_COUNT_RACE
                        ? session.config().lapLimit() : 0;
                    LiveRaceTimingService.StartResult timing = LiveRaceTimingService.start(level, session.config().sessionId(),
                        session.config().name(), timingLapLimit, eligible,
                        session.config().durationTicks() > 0L ? session.config().durationTicks() : -1L);
                    if (timing.started()) {
                        LiveRaceTimingService.lockParticipantCars(level, session.config().sessionId());
                        weekend.start(tick, null, "SERVER");
                        OWRRaceControlState.get(level).setStartPhase(6);
                    } else {
                        weekend.suspend(RaceSessionSuspensionReason.DIRECTOR, tick, null, "SERVER");
                    }
                    registry.markDirty();
                    session = weekend.activeSession(tick).orElse(session);
                }
            }
            long remaining = session.config().durationTicks() > 0L ? session.remainingTicks() : -1L;
            LiveRaceTimingService.updateWeekendContext(level, session.config().sessionId(), weekend.name(),
                session.config().type().name(), eligible, remaining);
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (!openDimensions.contains(level.dimension().identifier().toString())) {
                LiveRaceTimingService.clearWeekendContext(level);
            }
        }
    }

    private static void holdGridCars(ServerLevel level, GrandPrixWeekend weekend, GrandPrixWeekend.SessionView session) {
        TrackDefinition track = TrackDefinitionsData.get(level).get(weekend.trackId()).orElse(null);
        if (track == null || session.grid().isEmpty()) {
            return;
        }
        List<TrackDefinition.GridSlot> slots = track.gridSlots().stream()
            .sorted(Comparator.comparingInt(TrackDefinition.GridSlot::index)).toList();
        int count = Math.min(slots.size(), session.grid().size());
        for (int index = 0; index < count; index++) {
            ServerPlayer driver = level.getServer().getPlayerList().getPlayer(session.grid().get(index));
            if (driver == null || driver.level() != level || !(driver.getVehicle() instanceof OpenwheelCarEntity car)
                || car.getControllingPassenger() != driver) {
                continue;
            }
            TrackDefinition.GridSlot slot = slots.get(index);
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

    private static void lockWorldTime(ServerLevel level, long configuredTime) {
        long current = level.getDayTime();
        long locked = current - Math.floorMod(current, 24_000L) + configuredTime;
        if (current != locked) {
            level.setDayTime(locked);
        }
    }
}
