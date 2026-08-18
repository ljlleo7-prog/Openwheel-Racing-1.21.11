package com.openwheelracing.content.race.timing;

import com.openwheelracing.content.ai.BasicAiDriverIdentity;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackDefinitionsData;
import com.openwheelracing.content.track.survey.SurveyRoute;
import com.openwheelracing.content.track.survey.SurveyRouteLocalizer;
import com.openwheelracing.content.track.survey.SurveyRouteModel;
import com.openwheelracing.content.track.survey.TrackSurveyData;
import com.openwheelracing.network.OWRNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class LiveRaceTimingService {
    private static final int BROADCAST_INTERVAL_TICKS = 4;
    private static final Map<ServerLevel, RuntimeState> RUNTIMES = new IdentityHashMap<>();
    private static final java.util.Set<ServerLevel> RECOVERY_CHECKED = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    private LiveRaceTimingService() {
    }

    public static StartResult start(ServerLevel level, long sessionId, String sessionName) {
        return start(level, sessionId, sessionName, 0);
    }

    public static StartResult start(ServerLevel level, long sessionId, String sessionName, int lapLimit) {
        Optional<TrackDefinition> activeTrack = TrackDefinitionsData.get(level).activeTrack(level.dimension().identifier().toString());
        if (activeTrack.isEmpty()) {
            return new StartResult(false, "No active track in this dimension");
        }
        TrackSurveyData surveys = TrackSurveyData.get(level);
        Optional<SurveyRoute> route = surveys.get(activeTrack.get().trackId());
        if (route.isEmpty() || route.get().nodes().size() < 2 || !(route.get().length() > 0.0)) {
            return new StartResult(false, "Active track has no valid survey route");
        }
        RuntimeState runtime = new RuntimeState(sessionId, sessionName, activeTrack.get().trackId(), route.get().routeId(),
            surveys.revision(), route.get().toModel(), lapLimit);
        RUNTIMES.put(level, runtime);
        RECOVERY_CHECKED.add(level);
        runtime.tick(level);
        runtime.forceBroadcast = true;
        return new StartResult(true, "Live timing started for " + activeTrack.get().name());
    }

    public static boolean stop(ServerLevel level, String reason) {
        RuntimeState runtime = RUNTIMES.get(level);
        if (runtime == null) {
            return false;
        }
        runtime.suspend(reason == null || reason.isBlank() ? "STOPPED" : reason);
        runtime.broadcast(level);
        return true;
    }

    public static boolean resume(ServerLevel level) {
        RuntimeState runtime = RUNTIMES.get(level);
        if (runtime == null || runtime.active) {
            return false;
        }
        runtime.active = true;
        runtime.suspensionReason = "";
        runtime.forceBroadcast = true;
        return true;
    }

    public static Optional<LiveRaceTimingSnapshot> latestSnapshot(ServerLevel level) {
        RuntimeState runtime = RUNTIMES.get(level);
        return runtime == null ? Optional.empty() : Optional.of(runtime.decoratedSnapshot(level.getGameTime()));
    }

    public static void sendCurrent(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        RuntimeState runtime = RUNTIMES.get(level);
        OWRNetwork.sendLiveRaceTiming(player, runtime == null
            ? LiveRaceTimingSnapshot.inactive(0L, level.getGameTime(), "INACTIVE")
            : runtime.decoratedSnapshot(level.getGameTime()));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!event.hasTime()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            if (RECOVERY_CHECKED.add(level)) {
                restoreSuspended(level);
            }
            RuntimeState runtime = RUNTIMES.get(level);
            if (runtime == null) {
                continue;
            }
            if (playersIn(level) == 0 && runtime.active) {
                runtime.suspend("EMPTY_SERVER");
            }
            runtime.tick(level);
        }
    }

    public static void clearAll() {
        RUNTIMES.clear();
        RECOVERY_CHECKED.clear();
    }

    private static void restoreSuspended(ServerLevel level) {
        LiveRaceTimingData.Checkpoint checkpoint = LiveRaceTimingData.get(level).checkpoint();
        if (!checkpoint.configured()) {
            return;
        }
        TrackSurveyData surveys = TrackSurveyData.get(level);
        Optional<SurveyRoute> route = surveys.get(checkpoint.trackId());
        if (route.isEmpty() || !route.get().routeId().equals(checkpoint.routeId())) {
            return;
        }
        RuntimeState runtime = new RuntimeState(checkpoint.sessionId(), checkpoint.sessionName(), checkpoint.trackId(), checkpoint.routeId(),
            surveys.revision(), route.get().toModel(), checkpoint.lapLimit());
        runtime.active = false;
        runtime.suspensionReason = "SERVER_RECOVERY";
        List<LiveRaceClassificationEngine.RestoredProgress> restored = checkpoint.participants().stream().map(saved ->
            new LiveRaceClassificationEngine.RestoredProgress(
                new RaceParticipantKey(saved.participantId(), enumKind(saved.participantKind())), saved.displayName(), saved.completedLaps(),
                saved.routeDistanceMeters(), saved.stablePosition())).toList();
        runtime.engine.restore(route.get().length(), restored);
        runtime.engine.ensureRevisionAfter(checkpoint.snapshotRevision());
        runtime.engineSnapshot = runtime.engine.currentSnapshot(level.getGameTime());
        runtime.forceBroadcast = true;
        RUNTIMES.put(level, runtime);
    }

    private static RaceParticipantKind enumKind(int ordinal) {
        RaceParticipantKind[] values = RaceParticipantKind.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : RaceParticipantKind.PLAYER;
    }

    private static int playersIn(ServerLevel level) {
        int count = 0;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(level.dimension())) {
                count++;
            }
        }
        return count;
    }

    public record StartResult(boolean started, String message) {
    }

    private static final class RuntimeState {
        private final long sessionId;
        private final String sessionName;
        private final UUID trackId;
        private final UUID routeId;
        private final int surveyRevision;
        private final SurveyRouteModel route;
        private final int lapLimit;
        private final LiveRaceClassificationEngine engine = new LiveRaceClassificationEngine();
        private final Map<RaceParticipantKey, LocalizedParticipant> localized = new HashMap<>();
        private boolean active = true;
        private String suspensionReason = "";
        private LiveRaceTimingSnapshot engineSnapshot;
        private long lastBroadcastTick = Long.MIN_VALUE;
        private long lastBroadcastRevision = Long.MIN_VALUE;
        private boolean forceBroadcast;

        private RuntimeState(long sessionId, String sessionName, UUID trackId, UUID routeId, int surveyRevision, SurveyRouteModel route) {
            this(sessionId, sessionName, trackId, routeId, surveyRevision, route, 0);
        }

        private RuntimeState(long sessionId, String sessionName, UUID trackId, UUID routeId, int surveyRevision, SurveyRouteModel route, int lapLimit) {
            this.sessionId = sessionId;
            this.sessionName = sessionName == null ? "" : sessionName;
            this.trackId = trackId;
            this.routeId = routeId;
            this.surveyRevision = surveyRevision;
            this.route = route;
            this.lapLimit = Math.max(0, lapLimit);
            engine.reset(route.length());
        }

        private void tick(ServerLevel level) {
            if (!validateRoute(level)) {
                suspend("ROUTE_CHANGED");
            }
            if (active) {
                List<RaceTimingObservation> observations = collectObservations(level);
                long tick = level.getGameTime();
                engineSnapshot = engine.advance(route.length(), tick, tick * 50L, observations);
            }
            long tick = level.getGameTime();
            boolean materialChange = engineSnapshot != null && engineSnapshot.revision() != lastBroadcastRevision;
            if (forceBroadcast || materialChange && tick - lastBroadcastTick >= BROADCAST_INTERVAL_TICKS) {
                broadcast(level);
            }
        }

        private boolean validateRoute(ServerLevel level) {
            Optional<TrackDefinition> activeTrack = TrackDefinitionsData.get(level).activeTrack(level.dimension().identifier().toString());
            if (activeTrack.isEmpty() || !activeTrack.get().trackId().equals(trackId)) {
                return false;
            }
            TrackSurveyData surveys = TrackSurveyData.get(level);
            Optional<SurveyRoute> stored = surveys.get(trackId);
            return stored.isPresent() && stored.get().routeId().equals(routeId) && surveys.revision() == surveyRevision;
        }

        private List<RaceTimingObservation> collectObservations(ServerLevel level) {
            List<RaceTimingObservation> observations = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof OpenwheelCarEntity car) || !car.participatesInRaceTiming()) {
                    continue;
                }
                Participant participant = participant(car);
                if (participant == null) {
                    continue;
                }
                LocalizedParticipant state = localized.computeIfAbsent(participant.key(), ignored -> new LocalizedParticipant());
                SurveyRouteLocalizer.Result result = SurveyRouteLocalizer.locate(route,
                    new SurveyRouteModel.Point(car.getX(), car.getY(), car.getZ()), Math.toRadians(car.getYRot() + 90.0F), state.localizer);
                double distance = result.best().map(candidate -> candidate.distanceAlongRoute()).orElse(state.lastRouteDistance);
                if (result.best().isPresent() && (result.status() == SurveyRouteLocalizer.Status.TRACKED || result.status() == SurveyRouteLocalizer.Status.LOW_CONFIDENCE)) {
                    state.lastRouteDistance = distance;
                }
                observations.add(new RaceTimingObservation(participant.key(), participant.name(), car.getId(), distance, car.getSpeedKmh() / 3.6,
                    confidence(result.status()), level.getGameTime(), level.getGameTime() * 50L, participant.initialOrderHint()));
            }
            return observations;
        }

        private Participant participant(OpenwheelCarEntity car) {
            if (car.getControllingPassenger() instanceof ServerPlayer player) {
                return new Participant(new RaceParticipantKey(player.getUUID(), RaceParticipantKind.PLAYER), player.getScoreboardName(), Integer.MAX_VALUE);
            }
            Optional<BasicAiDriverIdentity> identity = car.getBasicAiIdentity();
            if (identity.isPresent() && identity.get().trackId().equals(trackId)) {
                return new Participant(new RaceParticipantKey(identity.get().driverId(), RaceParticipantKind.AI), identity.get().displayName(), identity.get().gridIndex());
            }
            return null;
        }

        private void suspend(String reason) {
            active = false;
            suspensionReason = reason;
            forceBroadcast = true;
        }

        private void broadcast(ServerLevel level) {
            LiveRaceTimingSnapshot snapshot = decoratedSnapshot(level.getGameTime());
            OWRNetwork.broadcastLiveRaceTiming(level, snapshot);
            saveCheckpoint(level, snapshot);
            lastBroadcastTick = level.getGameTime();
            lastBroadcastRevision = snapshot.revision();
            forceBroadcast = false;
        }

        private void saveCheckpoint(ServerLevel level, LiveRaceTimingSnapshot snapshot) {
            List<LiveRaceTimingData.SavedParticipant> saved = snapshot.rows().stream().map(row ->
                new LiveRaceTimingData.SavedParticipant(row.participant().id(), row.participant().kind().ordinal(), row.displayName(),
                    row.completedLaps(), row.routeDistanceMeters(), row.position())).toList();
            LiveRaceTimingData.get(level).update(new LiveRaceTimingData.Checkpoint(true, snapshot.active(), snapshot.suspensionReason(),
                sessionId, sessionName, trackId, routeId, surveyRevision, lapLimit, snapshot.revision(), saved));
        }

        private LiveRaceTimingSnapshot decoratedSnapshot(long serverTick) {
            List<RaceTimingRow> rows = engineSnapshot == null ? List.of() : engineSnapshot.rows();
            List<RacePositionChange> changes = engineSnapshot == null ? List.of() : engineSnapshot.recentPositionChanges();
            long revision = engineSnapshot == null ? engine.revision() : engineSnapshot.revision();
            return new LiveRaceTimingSnapshot(active, suspensionReason, sessionId, sessionName, trackId, routeId, revision,
                serverTick, route.length(), rows, changes, lapLimit, -1L);
        }
    }

    private static RaceProgressConfidence confidence(SurveyRouteLocalizer.Status status) {
        return switch (status) {
            case TRACKED -> RaceProgressConfidence.CONFIRMED;
            case LOW_CONFIDENCE -> RaceProgressConfidence.DEGRADED;
            case AMBIGUOUS -> RaceProgressConfidence.AMBIGUOUS;
            case UNTRACKED -> RaceProgressConfidence.UNTRACKED;
        };
    }

    private record Participant(RaceParticipantKey key, String name, int initialOrderHint) {
    }

    private static final class LocalizedParticipant {
        private final SurveyRouteLocalizer.State localizer = new SurveyRouteLocalizer.State();
        private double lastRouteDistance;
    }
}
