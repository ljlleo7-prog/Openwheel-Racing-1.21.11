package com.openwheelracing.content.race;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackDefinitionsData;
import com.openwheelracing.content.track.survey.SurveyRoute;
import com.openwheelracing.content.track.survey.SurveyRouteGeometry;
import com.openwheelracing.content.track.survey.SurveyRouteModel;
import com.openwheelracing.content.track.survey.TrackSurveyData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-authoritative local-yellow detection based on route distance, never sector numbers. */
public final class RaceAutoFlagService {
    static final double TRACK_CONTACT_HORIZONTAL = 7.0;
    static final double TRACK_CONTACT_VERTICAL = 4.0;
    static final double NEAR_TRACK_HAZARD_HORIZONTAL = 16.0;
    static final double MAX_DEPARTURE_RANGE = 64.0;
    static final double PREDICTION_TICKS = 20.0;
    static final int TRACK_STOPPED_TICKS = 20;
    static final int RUNOFF_STOPPED_TICKS = 60;
    static final double LIGHT_WARNING_DISTANCE = 500.0;

    private static final Map<ServerLevel, Map<UUID, CarState>> CARS = new IdentityHashMap<>();
    private static final Map<ServerLevel, Map<UUID, Hazard>> HAZARDS = new IdentityHashMap<>();
    private static final Map<ServerLevel, Long> START_DUE = new IdentityHashMap<>();

    private RaceAutoFlagService() { }

    public static void onServerTick(ServerTickEvent.Post event) {
        boolean autoFlagTick = event.getServer().getTickCount() % 20 == 0;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            OWRRaceControlState state = OWRRaceControlState.get(level);
            tickStartSequence(level, state);
            if (autoFlagTick) tickAutoFlags(level, state);
        }
    }

    private static void tickAutoFlags(ServerLevel level, OWRRaceControlState control) {
        if (!control.isAutoFlagging()) {
            CARS.remove(level);
            HAZARDS.remove(level);
            return;
        }
        RouteContext context = activeRoute(level);
        if (context == null) {
            HAZARDS.remove(level);
            return;
        }

        Map<UUID, CarState> states = CARS.computeIfAbsent(level, ignored -> new HashMap<>());
        Map<UUID, Hazard> hazards = HAZARDS.computeIfAbsent(level, ignored -> new HashMap<>());
        Set<UUID> seen = new HashSet<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof OpenwheelCarEntity car) || !car.isVehicle()) continue;
            UUID id = car.getUUID();
            seen.add(id);
            CarState state = states.computeIfAbsent(id, ignored -> new CarState());
            Hazard hazard = evaluate(level, car, context.route(), state);
            if (hazard == null) hazards.remove(id); else hazards.put(id, hazard);
        }
        states.keySet().retainAll(seen);
        hazards.keySet().retainAll(seen);
    }

    private static Hazard evaluate(ServerLevel level, OpenwheelCarEntity car, SurveyRouteModel route, CarState state) {
        SurveyRouteGeometry.Candidate current = nearestPhysical(route, car.position());
        boolean touchingTrack = isTrackContact(current) && hasClearPath(level, car, projectedPosition(current));
        boolean stopped = car.getSpeedKmh() < 3.0F;
        if (car.getSpeedKmh() >= 15.0F) state.hasMovedAtSpeed = true;

        if (touchingTrack) {
            state.anchorDistance = current.distanceAlongRoute();
            state.anchorPosition = car.position();
            state.hasAnchor = true;
            state.stoppedTicks = stopped ? state.stoppedTicks + 20 : 0;
            return state.hasMovedAtSpeed && state.stoppedTicks >= TRACK_STOPPED_TICKS
                ? new Hazard(current.distanceAlongRoute(), route.length(), HazardReason.TRACK_OBSTRUCTION)
                : null;
        }

        state.stoppedTicks = stopped ? state.stoppedTicks + 20 : 0;
        Vec3 velocity = car.getDeltaMovement();
        if (state.hasMovedAtSpeed && !stopped && velocity.horizontalDistanceSqr() > 0.0025) {
            Vec3 predictedPosition = car.position().add(velocity.x * PREDICTION_TICKS, velocity.y * PREDICTION_TICKS, velocity.z * PREDICTION_TICKS);
            SurveyRouteGeometry.Candidate predicted = nearestPhysical(route, predictedPosition);
            if (isTrackContact(predicted) && hasClearPath(level, car, predictedPosition)
                    && (current == null || predicted.horizontalDistance() + 2.0 < current.horizontalDistance())) {
                return new Hazard(predicted.distanceAlongRoute(), route.length(), HazardReason.PREDICTED_TRACK_ENTRY);
            }
        }

        if (state.hasMovedAtSpeed && stopped && state.stoppedTicks >= RUNOFF_STOPPED_TICKS
                && isNearTrack(current) && hasClearPath(level, car, projectedPosition(current))) {
            return new Hazard(current.distanceAlongRoute(), route.length(), HazardReason.STOPPED_NEAR_TRACK);
        }

        if (state.hasMovedAtSpeed && state.hasAnchor && state.stoppedTicks >= RUNOFF_STOPPED_TICKS
                && state.anchorPosition.distanceToSqr(car.position()) <= MAX_DEPARTURE_RANGE * MAX_DEPARTURE_RANGE) {
            return new Hazard(state.anchorDistance, route.length(), HazardReason.STRANDED_AFTER_DEPARTURE);
        }
        return null;
    }

    private static boolean hasClearPath(ServerLevel level, OpenwheelCarEntity car, Vec3 target) {
        Vec3 start = car.position().add(0.0, car.getBbHeight() * 0.5, 0.0);
        Vec3 end = target.add(0.0, car.getBbHeight() * 0.5, 0.0);
        return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, car)).getType() == HitResult.Type.MISS;
    }

    private static SurveyRouteGeometry.Candidate nearestPhysical(SurveyRouteModel route, Vec3 position) {
        if (route.nodes().size() < 2) return null;
        SurveyRouteModel.Point point = new SurveyRouteModel.Point(position.x, position.y, position.z);
        SurveyRouteGeometry.Candidate best = null;
        double bestScore = Double.MAX_VALUE;
        for (int segment = 0; segment < route.nodes().size(); segment++) {
            SurveyRouteGeometry.Candidate candidate = SurveyRouteGeometry.project(route, point, 0.0, segment);
            double score = RaceAutoFlagLogic.physicalScore(candidate.horizontalDistance(), candidate.verticalDelta());
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean isTrackContact(SurveyRouteGeometry.Candidate candidate) {
        return candidate != null && RaceAutoFlagLogic.isWithinRouteEnvelope(candidate.horizontalDistance(), candidate.verticalDelta(),
            TRACK_CONTACT_HORIZONTAL, TRACK_CONTACT_VERTICAL);
    }

    private static boolean isNearTrack(SurveyRouteGeometry.Candidate candidate) {
        return candidate != null && RaceAutoFlagLogic.isWithinRouteEnvelope(candidate.horizontalDistance(), candidate.verticalDelta(),
            NEAR_TRACK_HAZARD_HORIZONTAL, TRACK_CONTACT_VERTICAL);
    }

    private static Vec3 projectedPosition(SurveyRouteGeometry.Candidate candidate) {
        SurveyRouteModel.Point position = candidate.projectedPosition();
        return new Vec3(position.x(), position.y(), position.z());
    }

    /** Resolves whether a route-assigned light is upstream of any current automatic hazard. */
    public static RaceSignal signalForLight(ServerLevel level, double lightDistance, double routeLength) {
        if (!(routeLength > 0.0)) return RaceSignal.OFF;
        Map<UUID, Hazard> hazards = HAZARDS.get(level);
        if (hazards == null) return RaceSignal.OFF;
        for (Hazard hazard : hazards.values()) {
            if (RaceAutoFlagLogic.isUpstreamWithin(lightDistance, hazard.routeDistance(), routeLength, LIGHT_WARNING_DISTANCE)) return RaceSignal.YELLOW;
        }
        return RaceSignal.OFF;
    }

    public static int activeHazardCount(ServerLevel level) {
        Map<UUID, Hazard> hazards = HAZARDS.get(level);
        return hazards == null ? 0 : hazards.size();
    }

    private static RouteContext activeRoute(ServerLevel level) {
        TrackDefinition track = TrackDefinitionsData.get(level).activeTrack(level.dimension().identifier().toString()).orElse(null);
        if (track == null) return null;
        SurveyRoute survey = TrackSurveyData.get(level).get(track.trackId()).orElse(null);
        return survey == null || survey.nodes().size() < 2 ? null : new RouteContext(survey.toModel());
    }

    private static void tickStartSequence(ServerLevel level, OWRRaceControlState state) {
        int phase = state.getStartPhase();
        if (phase < 1 || phase > 5) { START_DUE.remove(level); return; }
        long now = level.getGameTime();
        long due = START_DUE.computeIfAbsent(level, ignored -> now + (phase < 5 ? 20L : 20L + level.random.nextInt(41)));
        if (now < due) return;
        state.setStartPhase(phase < 5 ? phase + 1 : 6);
        START_DUE.remove(level);
    }

    public static void requestStart(ServerLevel level) {
        START_DUE.put(level, level.getGameTime() + 20L);
    }

    public static void clearAll() {
        CARS.clear();
        HAZARDS.clear();
        START_DUE.clear();
    }

    enum HazardReason { TRACK_OBSTRUCTION, PREDICTED_TRACK_ENTRY, STOPPED_NEAR_TRACK, STRANDED_AFTER_DEPARTURE }
    record Hazard(double routeDistance, double routeLength, HazardReason reason) { }
    private record RouteContext(SurveyRouteModel route) { }
    private static final class CarState {
        private double anchorDistance;
        private Vec3 anchorPosition = Vec3.ZERO;
        private boolean hasAnchor;
        private boolean hasMovedAtSpeed;
        private int stoppedTicks;
    }
}
