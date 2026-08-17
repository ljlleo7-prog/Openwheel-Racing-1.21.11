package com.openwheelracing.content.ai;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.car.CarComponentDamage;
import com.openwheelracing.content.car.PrototypeCarSetup;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.content.race.OWRLapProfiles;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackDefinitionsData;
import com.openwheelracing.content.track.survey.SurveyRoute;
import com.openwheelracing.content.track.survey.SurveyRouteGeometry;
import com.openwheelracing.content.track.survey.SurveyRouteLocalizer;
import com.openwheelracing.content.track.survey.SurveyRouteModel;
import com.openwheelracing.content.track.survey.TrackSurveyData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BasicAiFleetManager {
    private static final Map<UUID, BasicAiCarController> CONTROLLERS = new HashMap<>();
    private static final Set<UUID> STOPPING = new HashSet<>();
    private static final Map<UUID, BasicAiDriveCommand> PREPARED_COMMANDS = new HashMap<>();
    private static final Map<UUID, GripCache> GRIP_CACHE = new HashMap<>();
    private static final Map<UUID, PlanCache> PLAN_CACHE = new HashMap<>();
    private static final Map<DriverRouteKey, LineCache> LINE_CACHE = new HashMap<>();
    private static final Map<UUID, Long> LOW_SPEED_SINCE = new HashMap<>();
    private static final Map<UUID, Long> LAST_RECORDED_LAP = new HashMap<>();
    private static final Map<UUID, com.openwheelracing.content.race.LapProfileCollector> AI_LAP_COLLECTORS = new HashMap<>();
    private static final Map<UUID, Long> AI_LAP_STARTED_AT = new HashMap<>();
    private static BasicAiTrafficMode modeOverride = BasicAiTrafficMode.AUTO;
    private static UUID cachedRouteId;
    private static int cachedSurveyRevision = Integer.MIN_VALUE;
    private static SurveyRouteModel cachedRouteModel;
    private static long preparedTick = Long.MIN_VALUE;
    private static String preparedDimension = "";

    private BasicAiFleetManager() {
    }

    public static void prepareCarTick(OpenwheelCarEntity car) {
        if (!(car.level() instanceof ServerLevel level) || !car.isBasicAiOwned()) {
            return;
        }
        prepareLevel(level);
        BasicAiDriveCommand prepared = PREPARED_COMMANDS.get(car.getUUID());
        if (prepared != null) {
            car.applyAutonomousDriveInput(prepared);
        } else if (car.isAutonomousControlEnabled()) {
            car.applyAutonomousDriveInput(new BasicAiDriveCommand(0.0f, 1.0f, 0.0f));
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!event.hasTime()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            refreshTickets(level);
        }
    }

    private static void refreshTickets(ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof OpenwheelCarEntity car)) {
                continue;
            }
            if (!car.isBasicAiOwned() || !car.isAutonomousControlEnabled()) {
                continue;
            }
            Optional<TrackDefinition> active = TrackDefinitionsData.get(level).activeTrack(level.dimension().identifier().toString());
            if (active.isEmpty()) {
                continue;
            }
            Optional<SurveyRoute> route = TrackSurveyData.get(level).get(active.get().trackId());
            if (route.isEmpty()) {
                continue;
            }
            BasicAiCarController controller = CONTROLLERS.get(car.getUUID());
            BasicAiStatus status = controller == null ? null : controller.status();
            if (status != null) {
                BasicAiFleetChunkTickets.acquire(level, car, route.get().toModel(), status.routeDistance());
            } else if (!BasicAiFleetChunkTickets.hasTickets(car.getUUID())) {
                SurveyRouteModel model = route.get().toModel();
                SurveyRouteLocalizer.Result location = SurveyRouteLocalizer.locate(model, point(car), heading(car), new SurveyRouteLocalizer.State());
                double distance = location.best().map(SurveyRouteGeometry.Candidate::distanceAlongRoute).orElse(0.0);
                BasicAiFleetChunkTickets.acquire(level, car, model, distance);
            }
        }
    }
    public static void clearAll() {
        CONTROLLERS.clear();
        STOPPING.clear();
        PREPARED_COMMANDS.clear();
        LINE_CACHE.clear();
        GRIP_CACHE.clear();
        PLAN_CACHE.clear();
        LOW_SPEED_SINCE.clear();
        LAST_RECORDED_LAP.clear();
        AI_LAP_COLLECTORS.clear();
        AI_LAP_STARTED_AT.clear();
        modeOverride = BasicAiTrafficMode.AUTO;
        cachedRouteId = null;
        cachedRouteModel = null;
        BasicAiFleetChunkTickets.releaseAll();
        invalidatePreparedTick();
    }

    public static void clearTrainingRuntime() {
        LOW_SPEED_SINCE.clear();
        AI_LAP_COLLECTORS.clear();
        AI_LAP_STARTED_AT.clear();
    }

    public static BasicAiTrafficMode mode(ServerLevel level) {
        return BasicAiTrafficMode.resolve(modeOverride, OWRRaceControlState.get(level).getGlobalFlag());
    }

    public static void setModeOverride(BasicAiTrafficMode mode) {
        modeOverride = mode == null ? BasicAiTrafficMode.AUTO : mode;
        invalidatePreparedTick();
    }

    public static BasicAiTrafficMode modeOverride() {
        return modeOverride;
    }

    public static int start(ServerLevel level, UUID trackId) {
        Optional<SurveyRoute> storedRoute = TrackSurveyData.get(level).get(trackId);
        if (storedRoute.isEmpty()) {
            return 0;
        }
        SurveyRouteModel route = storedRoute.get().toModel();
        int started = 0;
        for (OpenwheelCarEntity car : ownedCars(level, trackId)) {
            if (car.getControllingPassenger() != null) {
                continue;
            }
            SurveyRouteLocalizer.Result location = SurveyRouteLocalizer.locate(route, point(car), heading(car), new SurveyRouteLocalizer.State());
            double routeDistance = location.best().map(SurveyRouteGeometry.Candidate::distanceAlongRoute).orElse(0.0);
            if (!BasicAiFleetChunkTickets.acquire(level, car, route, routeDistance)) {
                continue;
            }
            STOPPING.remove(car.getUUID());
            CONTROLLERS.computeIfAbsent(car.getUUID(), ignored -> new BasicAiCarController()).resetLocalization();
            prepareAiCarDefaults(car);
            car.resetAiDrivetrain();
            car.setAutonomousControlEnabled(true);
            started++;
        }
        invalidatePreparedTick();
        return started;
    }

    public static int stop(ServerLevel level, UUID trackId) {
        int stopped = 0;
        for (OpenwheelCarEntity car : ownedCars(level, trackId)) {
            if (car.isAutonomousControlEnabled()) {
                STOPPING.add(car.getUUID());
                BasicAiFleetChunkTickets.release(car);
                stopped++;
            }
        }
        invalidatePreparedTick();
        return stopped;
    }

    public static int despawn(ServerLevel level, UUID trackId) {
        List<OpenwheelCarEntity> cars = ownedCars(level, trackId);
        for (OpenwheelCarEntity car : cars) {
            CONTROLLERS.remove(car.getUUID());
            STOPPING.remove(car.getUUID());
            PREPARED_COMMANDS.remove(car.getUUID());
            BasicAiFleetChunkTickets.release(car);
            car.discard();
        }
        invalidatePreparedTick();
        return cars.size();
    }

    public static List<BasicAiStatus> statuses(ServerLevel level, UUID trackId) {
        prepareLevel(level);
        List<BasicAiStatus> statuses = new ArrayList<>();
        for (OpenwheelCarEntity car : ownedCars(level, trackId)) {
            BasicAiDriverIdentity identity = car.getBasicAiIdentity().orElseThrow();
            BasicAiCarController controller = CONTROLLERS.get(car.getUUID());
            BasicAiStatus status = controller == null ? null : controller.status();
            if (status == null || !car.isAutonomousControlEnabled()) {
                String reason = STOPPING.contains(car.getUUID()) ? "stopping" : "stopped";
                status = BasicAiStatus.stopped(identity, car.getId(), car.getSpeedKmh(), reason);
            }
            statuses.add(status);
        }
        return statuses;
    }

    public static List<RacingLineTrace> currentLineTraces(ServerLevel level, UUID trackId) {
        List<RacingLineTrace> traces = new ArrayList<>();
        for (OpenwheelCarEntity car : ownedCars(level, trackId)) {
            BasicAiDriverIdentity identity = car.getBasicAiIdentity().orElse(null);
            if (identity == null) continue;
            com.openwheelracing.content.race.LapProfileCollector collector = AI_LAP_COLLECTORS.get(identity.driverId());
            if (collector == null) continue;
            List<com.openwheelracing.content.race.LapProfileCollector.TracePoint> points = collector.tracePoints();
            if (points.size() >= 2) traces.add(new RacingLineTrace(identity.driverId(), identity.displayName(), false, points));
        }
        return List.copyOf(traces);
    }

    public static List<RacingLineTrace> plannedLineTraces(ServerLevel level, UUID trackId) {
        List<RacingLineTrace> traces = new ArrayList<>();
        for (OpenwheelCarEntity car : ownedCars(level, trackId)) {
            BasicAiDriverIdentity identity = car.getBasicAiIdentity().orElse(null);
            PlanCache cached = PLAN_CACHE.get(car.getUUID());
            if (identity == null || cached == null) continue;
            List<com.openwheelracing.content.race.LapProfileCollector.TracePoint> points = cached.plan().samples().stream()
                .map(sample -> new com.openwheelracing.content.race.LapProfileCollector.TracePoint(
                    sample.position().x(), sample.position().y(), sample.position().z())).toList();
            traces.add(new RacingLineTrace(identity.driverId(), identity.displayName(), true, points));
        }
        return List.copyOf(traces);
    }

    public static String calibrationStatus(ServerLevel level, UUID trackId) {
        Optional<SurveyRoute> route = TrackSurveyData.get(level).get(trackId);
        if (route.isEmpty()) return "unavailable (no survey)";
        boolean human = OWRLapProfiles.get(level).fastestValidPlayer(trackId, route.get().routeId(), route.get().length()).isPresent();
        return human ? "not required (player reference available)" : "pending survey-only bounded trials";
    }

    public static void resetCalibration(UUID trackId) {
        PLAN_CACHE.entrySet().removeIf(entry -> entry.getValue().plan().trackId().equals(trackId));
    }

    public static double learnedLineDistance(ServerLevel level, UUID trackId) {
        Optional<SurveyRoute> route = TrackSurveyData.get(level).get(trackId);
        return route.map(value -> OWRAiTrainingData.get(level).matching(trackId, value.routeId()).stream()
            .mapToDouble(record -> record.prefix().distance()).max().orElse(0.0)).orElse(0.0);
    }

    public static List<RacingLineTrace> learnedLineTraces(ServerLevel level, UUID trackId) {
        Optional<SurveyRoute> stored = TrackSurveyData.get(level).get(trackId);
        if (stored.isEmpty()) return List.of();
        SurveyRoute route = stored.get();
        List<RacingLineTrace> traces = new ArrayList<>();
        for (OWRAiTrainingData.Record record : OWRAiTrainingData.get(level).matching(trackId, route.routeId())) {
            String archetype = record.archetype();
            if (!archetype.startsWith("prototype_default:")) continue;
            UUID driverId;
            try {
                driverId = UUID.fromString(archetype.substring("prototype_default:".length()));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            BasicAiDriverIdentity identity = ownedCars(level, trackId).stream().map(OpenwheelCarEntity::getBasicAiIdentity)
                .flatMap(Optional::stream).filter(value -> value.driverId().equals(driverId)).findFirst().orElse(null);
            if (identity == null) continue;
            RacingLineTrace trace = prefixTrace(route, identity, record.prefix());
            if (trace != null) traces.add(trace);
        }
        return List.copyOf(traces);
    }

    private static RacingLineTrace prefixTrace(SurveyRoute route, BasicAiDriverIdentity identity, OWRAiTrainingData.Prefix prefix) {
        if (prefix.distance() < 12.0) return null;
        List<com.openwheelracing.content.race.LapProfileCollector.TracePoint> points = new ArrayList<>();
        int count = Math.max(2, (int) Math.ceil(prefix.distance() / Math.max(0.25, prefix.spacing())));
        for (int index = 0; index <= count; index++) {
            double distance = prefix.startDistance() + Math.min(prefix.distance(), index * prefix.spacing());
            SurveyRoute.Node node = route.nodes().get(Math.floorMod((int) Math.floor(distance / route.spacing()), route.nodes().size()));
            double relative = SurveyRouteSampler.forwardDelta(prefix.startDistance(), distance, route.length());
            int sample = Math.min(prefix.offsets().length - 1, Math.max(0, (int) Math.floor(relative / prefix.spacing())));
            if (prefix.observed().length != prefix.offsets().length || prefix.observed()[sample] <= 0) {
                if (points.size() >= 2) break;
                continue;
            }
            double offset = prefix.offsets()[sample] / 100.0;
            double sideX = -Math.sin(node.headingRadians());
            double sideZ = Math.cos(node.headingRadians());
            points.add(new com.openwheelracing.content.race.LapProfileCollector.TracePoint(node.position().x() + sideX * offset,
                node.position().y(), node.position().z() + sideZ * offset));
        }
        return new RacingLineTrace(identity.driverId(), identity.displayName(), false, points);
    }

    public static List<OpenwheelCarEntity> ownedCars(ServerLevel level, UUID trackId) {
        List<OpenwheelCarEntity> cars = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof OpenwheelCarEntity car)) {
                continue;
            }
            Optional<BasicAiDriverIdentity> identity = car.getBasicAiIdentity();
            if (identity.isPresent() && identity.get().trackId().equals(trackId)) {
                cars.add(car);
            }
        }
        cars.sort(carComparator());
        return cars;
    }

    public static Comparator<OpenwheelCarEntity> carComparator() {
        return Comparator
            .comparingInt((OpenwheelCarEntity car) -> car.getBasicAiIdentity().map(BasicAiDriverIdentity::gridIndex).orElse(Integer.MAX_VALUE))
            .thenComparing(OpenwheelCarEntity::getUUID);
    }

    private static void prepareLevel(ServerLevel level) {
        String dimensionId = level.dimension().identifier().toString();
        long tick = level.getGameTime();
        if (preparedTick == tick && preparedDimension.equals(dimensionId)) {
            return;
        }
        preparedTick = tick;
        preparedDimension = dimensionId;
        PREPARED_COMMANDS.clear();

        Optional<TrackDefinition> activeTrack = TrackDefinitionsData.get(level).activeTrack(dimensionId);
        if (activeTrack.isEmpty()) {
            prepareEmergencyStops(level, "NO_ROUTE: no active track");
            return;
        }
        TrackSurveyData surveyData = TrackSurveyData.get(level);
        Optional<SurveyRoute> storedRoute = surveyData.get(activeTrack.get().trackId());
        if (storedRoute.isEmpty()) {
            prepareEmergencyStops(level, "NO_ROUTE: no survey");
            return;
        }
        SurveyRouteModel route = routeModel(storedRoute.get(), surveyData.revision());
        if (route.nodes().size() < 2 || !(route.length() > 0.0)) {
            prepareEmergencyStops(level, "NO_ROUTE: invalid survey");
            return;
        }

        List<CarSnapshot> snapshots = new ArrayList<>();
        for (OpenwheelCarEntity car : ownedCars(level, activeTrack.get().trackId())) {
            BasicAiDriverIdentity identity = car.getBasicAiIdentity().orElseThrow();
            BasicAiCarController controller = CONTROLLERS.computeIfAbsent(car.getUUID(), ignored -> new BasicAiCarController());
            SurveyRouteLocalizer.Result localization = controller.localize(route, point(car), heading(car));
            snapshots.add(new CarSnapshot(car, identity, localization, controller));
        }

        List<BasicAiNearbyAvoidance.Car> traffic = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof OpenwheelCarEntity car && car.isBasicAiOwned() && car.getControllingPassenger() == null) {
                traffic.add(new BasicAiNearbyAvoidance.Car(car.getId(), car.getX(), car.getZ(), heading(car), car.getDeltaMovement().x, car.getDeltaMovement().z));
            }
        }

        BasicAiTrafficMode trafficMode = mode(level);
        for (CarSnapshot snapshot : snapshots) {
            OpenwheelCarEntity car = snapshot.car();
            if (STOPPING.contains(car.getUUID())) {
                prepareStop(car, snapshot.identity());
                continue;
            }
            if (!car.isAutonomousControlEnabled()) {
                continue;
            }
            BasicAiNearbyAvoidance.Car subject = new BasicAiNearbyAvoidance.Car(car.getId(), car.getX(), car.getZ(), heading(car),
                car.getDeltaMovement().x, car.getDeltaMovement().z);
            BasicAiNearbyAvoidance.Decision avoidance = BasicAiNearbyAvoidance.choose(subject, traffic);
            BasicAiGripModel.State grip = gripState(car, tick);
            AiTrackPlan plan = trackPlan(level, route, car, grip);
            captureActualPath(level, storedRoute.get(), car, tick);
            double queueGap = trafficMode.queueing() ? nearestOrderedGap(snapshot, snapshots, route.length()) : Double.POSITIVE_INFINITY;
            BasicAiCarController controller = snapshot.controller();
            BasicAiDriveCommand command = controller.tick(new BasicAiCarController.Input(snapshot.identity(), car.getId(), route, point(car), heading(car),
                car.getSpeedKmh() / 3.6, car.getSpeedKmh() / 3.6, car.getYawRateRadiansPerSecond(), snapshot.localization(),
                avoidance, trafficMode, grip, plan, queueGap));
            PREPARED_COMMANDS.put(car.getUUID(), command);
            updateLowSpeedTimer(car, tick);
            if (shouldRecover(car, tick)) {
                recover(level, car, activeTrack.get(), tick);
            }
        }
    }

    private static void prepareStop(OpenwheelCarEntity car, BasicAiDriverIdentity identity) {
        if (car.getSpeedKmh() <= 2.0f) {
            car.clearAutonomousDriveInput();
            car.setAutonomousControlEnabled(false);
            STOPPING.remove(car.getUUID());
            BasicAiCarController controller = CONTROLLERS.get(car.getUUID());
            if (controller != null) {
                controller.stop(identity, car.getId(), car.getSpeedKmh(), "stopped");
            }
            return;
        }
        PREPARED_COMMANDS.put(car.getUUID(), BasicAiDriveCommand.stopped(0.0f));
    }

    private static double nearestOrderedGap(CarSnapshot subject, List<CarSnapshot> snapshots, double routeLength) {
        if (subject.localization().best().isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double from = subject.localization().best().orElseThrow().distanceAlongRoute();
        double nearest = Double.POSITIVE_INFINITY;
        for (CarSnapshot candidate : snapshots) {
            if (candidate == subject || !candidate.identity().fleetId().equals(subject.identity().fleetId()) || candidate.localization().best().isEmpty()) {
                continue;
            }
            double gap = SurveyRouteSampler.forwardDelta(from, candidate.localization().best().orElseThrow().distanceAlongRoute(), routeLength);
            if (gap > 0.1) {
                nearest = Math.min(nearest, gap);
            }
        }
        return nearest;
    }

    private static BasicAiRacingLineProfile racingLine(ServerLevel level, SurveyRouteModel route, BasicAiTrafficMode mode, UUID driverId) {
        if (mode != BasicAiTrafficMode.RACE) return BasicAiRacingLineProfile.empty(route.length(), 1000);
        OWRLapProfiles profiles = OWRLapProfiles.get(level);
        DriverRouteKey cacheKey = new DriverRouteKey(driverId, route.routeId());
        LineCache cached = LINE_CACHE.get(cacheKey);
        OWRAiTrainingData.Record training = trainingState(level, route, driverId);
        int revision = profiles.revision() ^ (int) training.lastUpdate();
        if (cached != null && cached.revision() == revision) return cached.profile();
        int pointCount = Math.min(2000, Math.max(1000, (int) Math.ceil(route.length() / 2.0)));
        BasicAiRacingLineProfile profile;
        OWRAiTrainingData.Prefix prefix = training.prefix();
        if (prefix.distance() > 0.0) {
            profile = BasicAiRacingLineProfile.fromPrefix(route.length(), pointCount, prefix);
        } else {
            profile = BasicAiRacingLineProfile.empty(route.length(), pointCount);
        }
        BasicAiRacingLineProfile explored = profile.withExploration(training.incidents(), route.length());
        LINE_CACHE.put(cacheKey, new LineCache(revision, explored));
        return explored;
    }

    private static AiTrackPlan trackPlan(ServerLevel level, SurveyRouteModel route, OpenwheelCarEntity car, BasicAiGripModel.State grip) {
        OWRLapProfiles profiles = OWRLapProfiles.get(level);
        PlanCache cached = PLAN_CACHE.get(car.getUUID());
        if (cached != null && cached.routeId().equals(route.routeId()) && cached.profileRevision() == profiles.revision()
            && cached.capability() == grip) {
            return cached.plan();
        }
        AiTrackPlan plan = AiTrackPlanCompiler.compile(route, profiles.matching(route.trackId(), route.routeId()), grip);
        PLAN_CACHE.put(car.getUUID(), new PlanCache(route.routeId(), profiles.revision(), grip, plan));
        return plan;
    }

    private static String driverArchetype(UUID driverId) {
        return "prototype_default:" + driverId;
    }

    private static OWRAiTrainingData.Record trainingState(ServerLevel level, SurveyRouteModel route, UUID driverId) {
        return OWRAiTrainingData.get(level).getOrCreate(route.trackId(), route.routeId(), driverArchetype(driverId));
    }

    private static BasicAiGripModel.State gripState(OpenwheelCarEntity car, long tick) {
        GripCache cached = GRIP_CACHE.get(car.getUUID());
        if (cached != null && tick < cached.refreshAt()) {
            return cached.state();
        }
        PrototypeCarSetup setup = car.getSetup();
        double worstWheelDamage = Math.max(Math.max(car.getFrontLeftWheelDamagePercent(), car.getFrontRightWheelDamagePercent()),
            Math.max(car.getRearLeftWheelDamagePercent(), car.getRearRightWheelDamagePercent()));
        double frontAero = CarComponentDamage.frontWingAeroMultiplier(car.getFrontEndDamagePercent());
        double rearAero = CarComponentDamage.rearWingAeroMultiplier(car.getRearEndDamagePercent());
        double powerFactor = CarComponentDamage.chassisPowerMultiplier(car.getChassisDamagePercent())
            * CarComponentDamage.enginePowerMultiplier(car.getEngineDamagePercent());
        double dragFactor = CarComponentDamage.chassisDragMultiplier(car.getChassisDamagePercent());
        BasicAiGripModel.State state = BasicAiGripModel.build(new BasicAiGripModel.Input(
            car.getTyreTemperatureCelsius(), car.getTyreWorkingTemperatureMinCelsius(), car.getTyreWorkingTemperatureMaxCelsius(),
            car.getTyreWearPercent(), worstWheelDamage, setup.tyreMuCoefficient(), car.getAiCurrentSurfaceGrip(),
            setup.clACoefficient(), frontAero, rearAero, setup.powerMultiplier(), powerFactor,
            setup.cdACoefficient(), dragFactor));
        GRIP_CACHE.put(car.getUUID(), new GripCache(state, tick + 10L + Math.floorMod(car.getId(), 10)));
        return state;
    }

    private static void updateLowSpeedTimer(OpenwheelCarEntity car, long tick) {
        if (car.getSpeedKmh() < 3.6f) {
            LOW_SPEED_SINCE.putIfAbsent(car.getUUID(), tick);
        } else {
            LOW_SPEED_SINCE.remove(car.getUUID());
        }
    }

    private static boolean shouldRecover(OpenwheelCarEntity car, long tick) {
        long lowSince = LOW_SPEED_SINCE.getOrDefault(car.getUUID(), tick);
        return BasicAiTrainingMath.stalled(car.getSpeedKmh() / 3.6, tick - lowSince + 1L);
    }

    public static boolean regenerateDestroyedCar(ServerLevel level, OpenwheelCarEntity car) {
        BasicAiDriverIdentity identity = car.getBasicAiIdentity().orElse(null);
        if (identity == null) return false;
        TrackDefinition track = TrackDefinitionsData.get(level).get(identity.trackId()).orElse(null);
        if (track == null) return false;
        return recover(level, car, track, level.getGameTime());
    }

    private static boolean recover(ServerLevel level, OpenwheelCarEntity oldCar, TrackDefinition track, long tick) {
        BasicAiDriverIdentity identity = oldCar.getBasicAiIdentity().orElse(null);
        if (identity == null) return false;
        TrackDefinition.GridSlot slot = track.gridSlots().stream().filter(candidate -> candidate.index() == identity.gridIndex()).findFirst().orElse(null);
        if (slot == null) return false;
        Optional<SurveyRoute> storedRoute = TrackSurveyData.get(level).get(track.trackId());
        if (storedRoute.isEmpty()) return false;
        SurveyRouteModel routeModel = storedRoute.get().toModel();
        SurveyRouteLocalizer.Result spawnLocation = SurveyRouteLocalizer.locate(routeModel,
            new SurveyRouteModel.Point(slot.position().x(), slot.position().y(), slot.position().z()), slot.headingRadians(), new SurveyRouteLocalizer.State());
        double spawnDistance = spawnLocation.best().map(SurveyRouteGeometry.Candidate::distanceAlongRoute).orElse(0.0);
        boolean resume = oldCar.isAutonomousControlEnabled();
        OpenwheelCarEntity replacement = new OpenwheelCarEntity(com.openwheelracing.registry.OWREntities.PROTOTYPE_CAR.get(), level);
        replacement.setPos(slot.position().x(), slot.position().y() + 0.02, slot.position().z());
        replacement.setYRot((float) Math.toDegrees(slot.headingRadians()) - 90.0f);
        replacement.setBasicAiIdentity(identity);
        replacement.setSetup(oldCar.getSetup());
        replacement.setLivery(oldCar.getLivery());
        replacement.setLiveryColors(oldCar.getLiveryColors());
        prepareAiCarDefaults(replacement);
        if (!level.addFreshEntity(replacement)) {
            return false;
        }
        CONTROLLERS.remove(oldCar.getUUID());
        PREPARED_COMMANDS.remove(oldCar.getUUID());
        GRIP_CACHE.remove(oldCar.getUUID());
        PLAN_CACHE.remove(oldCar.getUUID());
        LOW_SPEED_SINCE.remove(oldCar.getUUID());
        LAST_RECORDED_LAP.remove(oldCar.getUUID());
        BasicAiFleetChunkTickets.replaceOwner(level, oldCar, replacement, routeModel, spawnDistance);
        oldCar.discard();
        if (resume) {
            replacement.resetAiDrivetrain();
            replacement.setAutonomousControlEnabled(true);
        }
        AI_LAP_COLLECTORS.remove(identity.driverId());
        AI_LAP_STARTED_AT.remove(identity.driverId());
        return true;
    }

    private static void recordIncident(ServerLevel level, UUID trackId, OpenwheelCarEntity car, BasicAiDriverIdentity identity, long tick) {
        Optional<SurveyRoute> stored = TrackSurveyData.get(level).get(trackId);
        if (stored.isEmpty()) return;
        SurveyRouteModel route = stored.get().toModel();
        BasicAiCarController controller = CONTROLLERS.get(car.getUUID());
        SurveyRouteLocalizer.Result localization = controller == null
            ? SurveyRouteLocalizer.locate(route, point(car), heading(car), new SurveyRouteLocalizer.State())
            : controller.localize(route, point(car), heading(car));
        double distance = localization.best().map(SurveyRouteGeometry.Candidate::distanceAlongRoute).orElse(0.0);
        com.openwheelracing.content.race.LapProfileCollector collector = AI_LAP_COLLECTORS.get(identity.driverId());
        OWRAiTrainingData data = OWRAiTrainingData.get(level);
        OWRAiTrainingData.Record record = data.getOrCreate(trackId, route.routeId(), driverArchetype(identity.driverId()));
        OWRAiTrainingData.Record updated = record.withRecovery(distance, route.length(), tick);
            if (collector != null) updated = updated.withPrefix(collector.safePrefix(), tick);
        data.save(updated);
        LINE_CACHE.remove(new DriverRouteKey(identity.driverId(), route.routeId()));
        AI_LAP_STARTED_AT.remove(identity.driverId());
    }

    private static void captureActualPath(ServerLevel level, SurveyRoute route, OpenwheelCarEntity car, long tick) {
        BasicAiDriverIdentity identity = car.getBasicAiIdentity().orElse(null);
        if (identity == null) return;
        com.openwheelracing.content.race.LapProfileCollector collector = AI_LAP_COLLECTORS.computeIfAbsent(identity.driverId(), ignored -> {
            com.openwheelracing.content.race.LapProfileCollector created = new com.openwheelracing.content.race.LapProfileCollector();
            created.start(route, identity.driverId(), tick);
            AI_LAP_STARTED_AT.put(identity.driverId(), tick);
            return created;
        });
        collector.sample(point(car), heading(car), tick, car.getSpeedKmh());
    }

    private static void recordTrainingLap(ServerLevel level, SurveyRouteModel route, OpenwheelCarEntity car, int laps,
                                          BasicAiTrafficMode mode, long tick) {
        UUID key = car.getUUID();
        Long last = LAST_RECORDED_LAP.put(key, (long) laps);
        if (last != null && last >= laps) return;
        BasicAiDriverIdentity identity = car.getBasicAiIdentity().orElse(null);
        com.openwheelracing.content.race.LapProfileCollector collector = identity == null ? null : AI_LAP_COLLECTORS.remove(identity.driverId());
        Long startedAt = identity == null ? null : AI_LAP_STARTED_AT.remove(identity.driverId());
        int lapMillis = startedAt == null ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.max(1L, tick - startedAt) * 50L);
        if (collector != null && identity != null && lapMillis > 0) {
            OWRLapProfiles.BestLapProfile profile = collector.finish(level.dimension().identifier().toString(), route.trackId(), identity.displayName(),
                OWRLapProfiles.Origin.AI, -1L, lapMillis, tick);
            if (profile != null) OWRLapProfiles.get(level).putIfFaster(profile);
            Optional<SurveyRoute> stored = TrackSurveyData.get(level).get(route.trackId());
            stored.ifPresent(value -> {
                com.openwheelracing.content.race.LapProfileCollector next = new com.openwheelracing.content.race.LapProfileCollector();
                next.start(value, identity.driverId(), tick);
                AI_LAP_COLLECTORS.put(identity.driverId(), next);
                AI_LAP_STARTED_AT.put(identity.driverId(), tick);
            });
        }
        if (identity == null) return;
        OWRAiTrainingData data = OWRAiTrainingData.get(level);
        OWRAiTrainingData.Record record = data.getOrCreate(route.trackId(), route.routeId(), driverArchetype(identity.driverId()));
        if (!record.enabled()) return;
        BasicAiTrainingMath.Update update = BasicAiTrainingMath.update(record.targetScale(), record.brakingScale(), record.steeringScale(),
            record.validLaps(), record.rejectedLaps(), record.recoveries(), record.emaLapMillis() > 0.0 ? record.emaLapMillis() : 120_000.0,
            Math.max(1.0, car.getCurrentLapTicks() * 50.0), true, mode == BasicAiTrafficMode.RACE);
        if (update.accepted()) data.save(record.withLap((int) Math.min(Integer.MAX_VALUE, car.getCurrentLapTicks() * 50L), update, tick));
    }

    private static int playerPriorSampleCount(ServerLevel level, SurveyRouteModel route) {
        if (!BasicAiPlayerPrior.enabled()) return 0;
        return OWRLapProfiles.get(level).matching(route.trackId(), route.routeId(), OWRLapProfiles.Origin.PLAYER).size();
    }

    private static double playerPriorSpeed(ServerLevel level, SurveyRouteModel route, double routeDistance, BasicAiTrafficMode mode) {
        if (!BasicAiPlayerPrior.enabled() || mode != BasicAiTrafficMode.RACE) return 0.0;
        List<OWRLapProfiles.BestLapProfile> profiles = OWRLapProfiles.get(level).matching(route.trackId(), route.routeId(), OWRLapProfiles.Origin.PLAYER);
        if (profiles.isEmpty()) return 0.0;
        double total = 0.0;
        int count = 0;
        for (OWRLapProfiles.BestLapProfile profile : profiles) {
            double speed = profile.speedKmh(routeDistance) / 3.6;
            if (speed > 0.0 && Double.isFinite(speed)) {
                total += speed;
                count++;
            }
        }
        return count == 0 ? 0.0 : total / count;
    }

    private static SurveyRouteModel.Point point(OpenwheelCarEntity car) {
        return new SurveyRouteModel.Point(car.getX(), car.getY(), car.getZ());
    }

    private static double heading(OpenwheelCarEntity car) {
        return Math.toRadians(car.getYRot() + 90.0F);
    }

    private static void prepareEmergencyStops(ServerLevel level, String reason) {
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof OpenwheelCarEntity car) || !car.isBasicAiOwned() || !car.isAutonomousControlEnabled()) {
                continue;
            }
            BasicAiDriverIdentity identity = car.getBasicAiIdentity().orElseThrow();
            if (car.getSpeedKmh() <= 2.0f) {
                car.setAutonomousControlEnabled(false);
                STOPPING.remove(car.getUUID());
            } else {
                STOPPING.add(car.getUUID());
                PREPARED_COMMANDS.put(car.getUUID(), BasicAiDriveCommand.stopped(0.0f));
            }
            BasicAiCarController controller = CONTROLLERS.get(car.getUUID());
            if (controller != null) {
                controller.stop(identity, car.getId(), car.getSpeedKmh(), reason);
            }
        }
    }

    public static void prepareAiCarDefaults(OpenwheelCarEntity car) {
        car.setAbsEnabled(true);
        car.setTractionControlEnabled(true);
        car.setDrsActive(false);
        car.setErsMode(OpenwheelCarEntity.ERS_MODE_HARVEST);
    }

    private static SurveyRouteModel routeModel(SurveyRoute route, int revision) {
        if (cachedRouteModel == null || cachedSurveyRevision != revision || !route.routeId().equals(cachedRouteId)) {
            cachedRouteId = route.routeId();
            cachedSurveyRevision = revision;
            cachedRouteModel = route.toModel();
        }
        return cachedRouteModel;
    }

    private static void invalidatePreparedTick() {
        preparedTick = Long.MIN_VALUE;
        PREPARED_COMMANDS.clear();
    }

    public record RacingLineTrace(UUID id, String label, boolean closed,
                                  List<com.openwheelracing.content.race.LapProfileCollector.TracePoint> points) {
        public RacingLineTrace {
            points = List.copyOf(points);
        }
    }

    private record DriverRouteKey(UUID driverId, UUID routeId) {
    }

    private record LineCache(int revision, BasicAiRacingLineProfile profile) {
    }

    private record GripCache(BasicAiGripModel.State state, long refreshAt) {
    }

    private record PlanCache(UUID routeId, int profileRevision, BasicAiGripModel.State capability, AiTrackPlan plan) {
    }

    private record CarSnapshot(OpenwheelCarEntity car, BasicAiDriverIdentity identity, SurveyRouteLocalizer.Result localization,
                               BasicAiCarController controller) {
    }
}
