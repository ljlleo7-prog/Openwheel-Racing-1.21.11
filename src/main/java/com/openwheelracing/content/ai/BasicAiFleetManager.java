package com.openwheelracing.content.ai;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.car.CarComponentDamage;
import com.openwheelracing.content.car.PrototypeCarSetup;
import com.openwheelracing.content.race.OWRRaceControlState;
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
            }
        }
    }
    public static void clearAll() {
        CONTROLLERS.clear();
        STOPPING.clear();
        PREPARED_COMMANDS.clear();
        GRIP_CACHE.clear();
        modeOverride = BasicAiTrafficMode.AUTO;
        cachedRouteId = null;
        cachedRouteModel = null;
        BasicAiFleetChunkTickets.releaseAll();
        invalidatePreparedTick();
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
            double queueGap = trafficMode.queueing() ? nearestOrderedGap(snapshot, snapshots, route.length()) : Double.POSITIVE_INFINITY;
            BasicAiCarController controller = snapshot.controller();
            BasicAiDriveCommand command = controller.tick(new BasicAiCarController.Input(snapshot.identity(), car.getId(), route, point(car), heading(car),
                car.getSpeedKmh() / 3.6, snapshot.localization(), avoidance, trafficMode, grip, queueGap));
            PREPARED_COMMANDS.put(car.getUUID(), command);
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

    private record GripCache(BasicAiGripModel.State state, long refreshAt) {
    }

    private record CarSnapshot(OpenwheelCarEntity car, BasicAiDriverIdentity identity, SurveyRouteLocalizer.Result localization,
                               BasicAiCarController controller) {
    }
}
