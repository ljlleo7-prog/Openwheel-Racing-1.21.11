package com.openwheelracing.content.entity;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.race.OWRLapRecords;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackDefinitionsData;
import com.openwheelracing.content.track.survey.SurveyRoute;
import com.openwheelracing.content.track.survey.TrackSurveyData;
import com.openwheelracing.registry.OWREntities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** End-to-end continuous survey coverage -> recovery -> records/race credit and best-time separation. */
public final class SurveyCompensationGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, OpenwheelRacing.MODID);
    static { FUNCTIONS.register("survey_compensation", () -> SurveyCompensationGameTests::run); }
    private SurveyCompensationGameTests() {}
    public static void register(IEventBus bus) { FUNCTIONS.register(bus); }

    private static void run(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 origin = helper.absoluteVec(new Vec3(6, 8, 6));
        String dimension = level.dimension().identifier().toString();
        var controls = OWRRaceControlState.get(level);
        controls.setMinimumValidLapTicks(1);
        if (controls.isCheckpointCheckEnabled()) controls.toggleCheckpointCheck();
        if (controls.isOffTrackCheckEnabled()) controls.toggleOffTrackCheck();
        var track = TrackDefinition.empty(UUID.randomUUID(), "Survey compensation", dimension).withStartFinish(new TrackDefinition.StartFinishLine(
            new TrackDefinition.Point3(origin.x, origin.y - 1, origin.z - 12), new TrackDefinition.Point3(origin.x, origin.y - 1, origin.z + 12), 0));
        TrackDefinitionsData.get(level).upsert(track);
        TrackDefinitionsData.get(level).setActiveTrack(track.trackId(), dimension);
        double[][] points = {{0, 0, 0}, {30, 0, 30}, {30, 40, 70}, {-30, 40, 130}, {-30, 0, 170}};
        List<SurveyRoute.Node> nodes = new ArrayList<>();
        for (int i = 0; i < points.length; i++) nodes.add(new SurveyRoute.Node(i,
            new SurveyRoute.Point(origin.x + points[i][0], origin.y, origin.z + points[i][1]), 0, points[i][2]));
        TrackSurveyData.get(level).put(new SurveyRoute(UUID.randomUUID(), track.trackId(), List.of(), nodes, 200, 4, SurveyRoute.CURRENT_SCHEMA));
        List<Probe> probes = new ArrayList<>();
        for (String scenario : List.of("geometric_control", "missed_height", "reverse", "dropout", "insufficient", "geometry_after_recovery", "spin_finish", "packet_jitter", "route_only_recovery", "teleport", "grace_start", "velocity_zero")) {
            probes.add(new Probe(helper, origin, scenario));
        }
        helper.onEachTick(() -> {
            if (helper.getTick() >= 1200) return;
            for (Probe probe : probes) probe.advance();
        });
        helper.runAtTickTime(1201, () -> {
            List<String> report = new ArrayList<>();
            report.add("scenario,forward_crossings,expected_completed_laps,race_laps,qualifying_laps,recovered_intervals,estimated_intervals,best_ms,max_precise_recovery_error_ms");
            var records = OWRLapRecords.get(level);
            var all = records.allLaps();
            for (Probe probe : probes) {
                var rows = all.stream().filter(row -> row.driverId().equals(probe.driver.getUUID())).toList();
                long credited = rows.stream().filter(row -> !row.invalidated() || row.car().estimatedTiming()).count();
                long valid = rows.stream().filter(row -> !row.invalidated()).count();
                long recovered = rows.stream().filter(row -> row.car().recoveredTiming()).count();
                long estimated = rows.stream().filter(row -> row.car().estimatedTiming()).count();
                int expected = probe.scenario.equals("insufficient") ? 0 : probe.scenario.equals("teleport") ? probe.creditBeforeTeleport
                    : probe.scenario.equals("grace_start") ? probe.crossings : probe.crossings - 1;
                int expectedMillis = probe.scenario.equals("reverse") ? 4800 : 4000;
                int maxError = rows.stream().filter(row -> row.car().recoveredTiming() && !row.car().estimatedTiming()).mapToInt(row -> Math.abs(row.lapMillis() - expectedMillis)).max().orElse(0);
                report.add(probe.scenario + "," + probe.crossings + "," + expected + "," + credited + "," + valid + "," + recovered + "," + estimated + "," + records.getBestLap(probe.driver.getUUID()) + "," + maxError);
                helper.assertTrue(credited == expected, "Race count mismatch for " + probe.scenario + ": expected " + expected + " got " + credited);
                if (probe.scenario.equals("geometric_control") || probe.scenario.equals("packet_jitter") || probe.scenario.equals("velocity_zero")) helper.assertTrue(recovered == 0 && valid == expected, "A real crossing must not also receive compensation");
                else if (probe.scenario.equals("geometry_after_recovery")) helper.assertTrue(estimated == 2 && valid == expected - 2, "Estimated start uncertainty must end at the next geometric boundary");
                else if (probe.scenario.equals("grace_start")) {
                    helper.assertTrue(estimated == 1 && valid == expected - 1 && records.getBestLap(probe.driver.getUUID()) == 4000,
                        "A sampled grace start must not count as a precise qualifying origin");
                }
                else if (probe.scenario.equals("insufficient") || probe.scenario.equals("route_only_recovery")) helper.assertTrue(valid == 0 && records.getBestLap(probe.driver.getUUID()) == 0, "Route-only or incomplete recovery must not establish a best time");
                else helper.assertTrue(valid == expected && estimated == 0 && records.getBestLap(probe.driver.getUUID()) == expectedMillis,
                    "Movement-backed recovery must retain geometric timing accuracy for qualifying: " + probe.scenario);
                helper.assertTrue(maxError <= 1, "Straight-line recovery timestamps must agree within 1 ms for " + probe.scenario + ": " + maxError);
                probe.driver.stopRiding(); probe.car.discard();
            }
            try { Files.write(Path.of("survey-compensation-summary.csv"), report); }
            catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
            TrackSurveyData.get(level).clear(track.trackId());
            TrackDefinitionsData.get(level).remove(track.trackId(), dimension);
            helper.succeed();
        });
    }

    private static final class Probe {
        private final GameTestHelper helper;
        private final Vec3 origin;
        private final String scenario;
        private final ReplayCar car;
        private final Player driver;
        private int step;
        private int crossings;
        private int creditBeforeTeleport;
        Probe(GameTestHelper helper, Vec3 origin, String scenario) {
            this.helper = helper; this.origin = origin; this.scenario = scenario;
            car = new ReplayCar(helper);
            car.setPos(location(0));
            driver = helper.makeMockPlayer(GameType.CREATIVE);
            driver.getAbilities().invulnerable = true;
            driver.startRiding(car, true, false);
        }
        double progress(int step) {
            if (scenario.equals("grace_start")) return 2 + step * 2.5;
            if (!scenario.equals("reverse")) return 170 + step * 2.5;
            double travel = 210 + step * 2.5;
            double local = travel % 240;
            return Math.floor(travel / 240) * 200 + (local <= 100 ? local : local < 120 ? 200 - local : local - 40);
        }
        Vec3 location(int step) {
            double unwrapped = progress(step), s = unwrapped % 200;
            double x, z;
            if (s < 30) { x = s; z = 0; }
            else if (s < 70) { x = 30; z = s - 30; }
            else if (s < 130) { x = 100 - s; z = 40; }
            else if (s < 170) { x = -30; z = 170 - s; }
            else { x = s - 200; z = 0; }
            boolean control = scenario.equals("geometric_control") || scenario.equals("packet_jitter") || scenario.equals("spin_finish") || scenario.equals("velocity_zero");
            boolean high = !control && unwrapped >= 210 && (s < 10 || s > 190);
            if (scenario.equals("geometry_after_recovery")) high = false;
            if (scenario.equals("grace_start")) high = s < 10 || s > 190;
            double lift = high ? 2 : 0;
            if (scenario.equals("dropout") && s >= 80 && s <= 87.5) lift = 6;
            if (scenario.equals("insufficient") && s >= 60 && s <= 120) lift = 6;
            if (scenario.equals("route_only_recovery") && unwrapped >= 210 && (s < 15 || s > 185)) {
                z = 20 * (1 - Math.min(s, 200 - s) / 15);
                lift = 0;
            }
            if (scenario.equals("geometry_after_recovery") && Math.abs(unwrapped - 400) < 15) {
                z = 20 * (1 - Math.abs(unwrapped - 400) / 15);
                lift = 0;
            }
            return origin.add(x, lift, z);
        }
        void advance() {
            helper.assertTrue(driver.isAlive() && car.hasPassenger(driver), "Live driver required");
            if (scenario.equals("teleport") && step == 120) {
                creditBeforeTeleport = (int) OWRLapRecords.get(helper.getLevel()).getRaceSessionLaps(OWRLapRecords.get(helper.getLevel()).getActiveSessionId())
                    .stream().filter(row -> row.driverId().equals(driver.getUUID())).count();
                car.setPos(origin.add(500, 0, 500));
            }
            Vec3 before = car.position();
            double oldProgress = progress(step);
            Vec3 current = location(++step);
            double newProgress = progress(step);
            boolean crossing = Math.floor(oldProgress / 200) < Math.floor(newProgress / 200);
            if (crossing) crossings++;
            car.setPos(current);
            Vec3 delta = current.subtract(before);
            float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
            if (scenario.equals("spin_finish") && newProgress >= 210 && (newProgress % 200 < 10 || newProgress % 200 > 190)) yaw += 90;
            car.setYRot(yaw); car.setDeltaMovement(delta);
            car.processRaceTimingMovement(before, scenario.equals("velocity_zero") && crossing ? Vec3.ZERO : delta);
            if (scenario.equals("packet_jitter") && crossing) {
                for (double offset : new double[] {2, -2, 2}) {
                    Vec3 previous = car.position();
                    car.setPos(origin.add(offset, 0, 0));
                    car.setYRot(-90);
                    car.onAcceptedVehiclePosition(previous);
                }
            }
        }
    }
    private static final class ReplayCar extends OpenwheelCarEntity {
        ReplayCar(GameTestHelper helper) { super(OWREntities.PROTOTYPE_CAR.get(), helper.getLevel()); }
        @Override public void tick() { }
    }
}
