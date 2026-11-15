package com.openwheelracing.content.entity;

import com.mojang.logging.LogUtils;
import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.race.OWRLapRecords;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackDefinitionsData;
import com.openwheelracing.registry.OWRBlocks;
import com.openwheelracing.registry.OWREntities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Seeded 20 Hz trajectories on real server entities, blocks, SavedData and lap recording.
 * Scripted cars replace the force solver only; the production movement-to-record pipeline is unchanged.
 * The final stage also exercises the force/collision solver with ordinary coasting ticks.
 */
public final class LapCaptureGameTests {
    private static final long SEED = 0x51A7E570L;
    private static final int STAGE_TICKS = 1280;
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, OpenwheelRacing.MODID);

    static { FUNCTIONS.register("lap_capture_campaign", () -> LapCaptureGameTests::campaign); }

    private LapCaptureGameTests() {}

    public static void register(IEventBus bus) { FUNCTIONS.register(bus); }

    private static void campaign(GameTestHelper helper) {
        var campaign = new Campaign(helper);
        helper.onEachTick(campaign::tick);
    }

    private record Setting(String name, double lift, double yawError, int period, boolean shouldCapture, int pulseEvery) {
        Setting(String name, double lift, double yawError, int period, boolean shouldCapture) {
            this(name, lift, yawError, period, shouldCapture, 0);
        }
    }

    private static final class Campaign {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Vec3 origin;
        private final List<ChunkPos> heldChunks = new ArrayList<>();
        private final List<ScriptedCar> cars = new ArrayList<>();
        private final List<CoastingCar> coastingCars = new ArrayList<>();
        private final List<Player> drivers = new ArrayList<>();
        private final List<String> results = new ArrayList<>();
        private final List<String> events = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();
        private final Random random = new Random(SEED);
        private TrackDefinition track;
        private int stage = -1;
        private boolean done;

        Campaign(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            origin = helper.absoluteVec(new Vec3(6, 1, 6));
            // Mock riders do not carry player chunk tickets. Hold the full replay area explicitly.
            for (int x = ((int) Math.floor(origin.x) - 64) >> 4; x <= ((int) Math.floor(origin.x) + 64) >> 4; x++) {
                for (int z = ((int) Math.floor(origin.z) - 32) >> 4; z <= ((int) Math.floor(origin.z) + 80) >> 4; z++) {
                    level.setChunkForced(x, z, true);
                    heldChunks.add(new ChunkPos(x, z));
                }
            }
            results.add("stage,seed,driver,lift_m,yaw_error_deg,period_ticks,forward_crossings,expected_valid_laps,valid_records,all_records,invalid_reasons");
            events.add("stage,driver,tick,expected_crossing,records_added,x,y,z");
        }

        void tick() {
            if (done) return;
            int nextStage = (int) helper.getTick() / STAGE_TICKS;
            if (nextStage != stage) {
                if (stage >= 0) finishStage();
                stage = nextStage;
                if (stage == 6) { finish(); return; }
                beginStage();
            }
            // GameTest chunks need not entity-tick without a nearby real player. Drive the
            // replay explicitly; each car also guards against a second world tick dispatch.
            for (ScriptedCar car : cars) car.tick();
            for (CoastingCar car : coastingCars) car.tick();
        }

        private void beginStage() {
            var controls = OWRRaceControlState.get(level);
            controls.setMinimumValidLapTicks(1);
            if (controls.isOffTrackCheckEnabled()) controls.toggleOffTrackCheck();
            if (controls.isCheckpointCheckEnabled() != (stage == 4)) controls.toggleCheckpointCheck();
            String dimension = level.dimension().identifier().toString();
            track = TrackDefinition.empty(UUID.randomUUID(), "Capture campaign " + stage, dimension);
            if (stage != 2) track = track.withStartFinish(new TrackDefinition.StartFinishLine(
                point(0, -1, -14), point(0, -1, 14), 0));
            if (stage == 4) {
                // Both checkpoints can lie inside a single normal-speed movement interval.
                track = track.withStewardLines(List.of(checkpoint(1, 18), checkpoint(2, 19)));
            }
            TrackDefinitionsData.get(level).upsert(track);
            TrackDefinitionsData.get(level).setActiveTrack(track.trackId(), dimension);
            if (stage == 2) {
                for (int z = -14; z <= 14; z++) {
                    level.setBlock(BlockPos.containing(origin.x + 0.5, origin.y - 1, origin.z + z),
                        OWRBlocks.START_FINISH.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST), 3);
                }
            }
            if (stage == 5) {
                // Actual engine movement and ground collisions, resetting upstream between passes.
                for (int x = -40; x <= 40; x++) for (int z = -14; z <= 14; z++) {
                    level.setBlock(BlockPos.containing(origin.x + x, origin.y - 1, origin.z + z), OWRBlocks.ASPHALT_TRACK.get().defaultBlockState(), 3);
                    for (int y = 0; y <= 4; y++) level.removeBlock(BlockPos.containing(origin.x + x, origin.y + y, origin.z + z), false);
                }
                var car = new CoastingCar(level, this, 0);
                Player driver = makeProtectedDriver();
                car.setPos(origin.add(-30, 0, car.lane));
                level.addFreshEntity(car);
                driver.startRiding(car, true, false);
                helper.assertTrue(car.hasPassenger(driver), "Coasting driver must mount the actual car");
                car.driverId = driver.getUUID();
                coastingCars.add(car); drivers.add(driver);
                return;
            }
            List<Setting> settings = new ArrayList<>();
            if (stage == 0) {
                for (int i = 0; i < 24; i++) settings.add(new Setting("normal_" + i, random.nextDouble() * 0.8,
                    random.nextDouble() * 50 - 25, new int[] {40, 64, 80, 128}[i % 4], true));
            } else if (stage == 1 || stage == 2) {
                for (double lift : new double[] {0, 0.05, 0.19, 0.21, 0.3, 0.5, 0.8, 1, 1.49, 1.51, 2, 3}) {
                    settings.add(new Setting("height_" + lift, lift, 0, 80, stage == 1 ? lift <= 1.49 : lift <= 0.5));
                }
                settings.add(new Setting("occasional_lift_13", 0.3, 0, 80, true, 13));
            } else if (stage == 3) {
                for (double yaw : new double[] {-90, -75, -70, -69, -45, 0, 45, 69, 70, 75, 90}) {
                    settings.add(new Setting("yaw_" + yaw, 0.1, yaw, 80, Math.abs(yaw) <= 69));
                }
            } else {
                for (int i = 0; i < 12; i++) settings.add(new Setting("checkpoints_" + i, 0.1, 0, i < 6 ? 40 : 400, true));
            }
            for (int i = 0; i < settings.size(); i++) {
                Setting setting = settings.get(i);
                ScriptedCar car = new ScriptedCar(level, this, setting, -10 + i * 20.0 / settings.size(), random.nextDouble());
                Player driver = makeProtectedDriver();
                car.setPos(car.location(0));
                level.addFreshEntity(car);
                driver.startRiding(car, true, false);
                helper.assertTrue(car.hasPassenger(driver), "Campaign driver must mount the actual car");
                car.driverId = driver.getUUID();
                cars.add(car);
                drivers.add(driver);
            }
        }

        private Player makeProtectedDriver() {
            Player driver = helper.makeMockPlayer(GameType.CREATIVE);
            // GameTest's mock only overrides gameMode(); it does not apply creative abilities.
            driver.getAbilities().invulnerable = true;
            driver.getAbilities().instabuild = true;
            return driver;
        }

        private TrackDefinition.Point3 point(double x, double y, double z) {
            return new TrackDefinition.Point3(origin.x + x, origin.y + y, origin.z + z);
        }

        private TrackDefinition.StewardLine checkpoint(int index, double x) {
            return new TrackDefinition.StewardLine(TrackDefinition.StewardLineType.CHECKPOINT, index, "CP " + index,
                point(x, -1, -14), point(x, -1, 14), 0, x);
        }

        private void finishStage() {
            var records = OWRLapRecords.get(level);
            var all = records.getRecentLaps(0, Integer.MAX_VALUE);
            for (ScriptedCar car : cars) {
                var rows = all.stream().filter(row -> row.driverId().equals(car.driverId)).toList();
                long valid = rows.stream().filter(row -> !row.invalidated()).count();
                helper.assertTrue(car.steps > 0 && car.crossings >= 2, "Replay must actually advance and complete multiple circuits");
                int expected = Math.max(0, car.crossings - 1);
                String invalid = rows.stream().filter(OWRLapRecords.LapRecord::invalidated)
                    .map(OWRLapRecords.LapRecord::invalidationReason).distinct().sorted().reduce((a, b) -> a + "|" + b).orElse("");
                results.add(String.format(Locale.ROOT, "%d,%d,%s,%.4f,%.4f,%d,%d,%d,%d,%d,%s",
                    stage, SEED, car.setting.name(), car.setting.lift(), car.setting.yawError(), car.setting.period(), car.crossings, expected, valid, rows.size(), invalid));
                LogUtils.getLogger().info("CAPTURE stage={} driver={} crossings={} expectedLaps={} valid={} total={} invalid={}",
                    stage, car.setting.name(), car.crossings, expected, valid, rows.size(), invalid);
                if (car.setting.shouldCapture() && valid != expected) failures.add("stage " + stage + " " + car.setting.name() + ": expected " + expected + ", got " + valid);
                if (!car.setting.shouldCapture() && valid != 0) failures.add("Unexpected capture outside configured guard: " + car.setting.name());
                car.discard();
            }
            for (CoastingCar car : coastingCars) {
                var rows = all.stream().filter(row -> row.driverId().equals(car.driverId)).toList();
                long valid = rows.stream().filter(row -> !row.invalidated()).count();
                helper.assertTrue(car.crossings > 2, "Physics stage must actually cross the finish repeatedly: " + car.position() + " origin=" + origin + " rider=" + car.getControllingPassenger() + " alive=" + car.isAlive());
                int expected = car.crossings - 1;
                String invalid = rows.stream().filter(OWRLapRecords.LapRecord::invalidated)
                    .map(OWRLapRecords.LapRecord::invalidationReason).distinct().sorted().reduce((a, b) -> a + "|" + b).orElse("");
                results.add(String.format(Locale.ROOT, "5,%d,physics_lane_%.0f,0,0,0,%d,%d,%d,%d,%s", SEED, car.lane, car.crossings, expected, valid, rows.size(), invalid));
                if (valid != expected) failures.add("Physics lane " + car.lane + ": expected " + expected + ", got " + valid);
                car.discard();
            }
            coastingCars.clear();
            for (Player driver : drivers) driver.stopRiding();
            cars.clear(); drivers.clear();
            if (stage == 2) for (int z = -14; z <= 14; z++) level.removeBlock(BlockPos.containing(origin.x + 0.5, origin.y - 1, origin.z + z), false);
            TrackDefinitionsData.get(level).remove(track.trackId(), level.dimension().identifier().toString());
        }

        private void finish() {
            done = true;
            try {
                Files.write(Path.of("lap-capture-summary.csv"), results);
                Files.write(Path.of("lap-capture-events.csv"), events);
            } catch (IOException exception) { throw new IllegalStateException("Could not write capture report", exception); }
            for (ChunkPos chunk : heldChunks) level.setChunkForced(chunk.x, chunk.z, false);
            LogUtils.getLogger().warn("CAPTURE campaign seed={} mismatches={}", SEED, failures);
            helper.assertTrue(failures.stream().noneMatch(failure -> failure.startsWith("stage 0 ") || failure.startsWith("Physics ") || failure.startsWith("Unexpected capture")),
                "Capture control/fixture failure: " + failures);
            helper.assertTrue(!Boolean.getBoolean("openwheelracing.lapCaptureStrict") || failures.isEmpty(),
                "Lap capture campaign lost laps: " + failures);
            helper.succeed();
        }
    }

    private static final class ScriptedCar extends OpenwheelCarEntity {
        private final Campaign campaign;
        private final Setting setting;
        private final double lane;
        private final double phase;
        private int steps;
        private int crossings;
        private UUID driverId;
        private long lastProcessedTick = Long.MIN_VALUE;

        ScriptedCar(ServerLevel level, Campaign campaign, Setting setting, double lane, double phase) {
            super(OWREntities.PROTOTYPE_CAR.get(), level);
            this.campaign = campaign; this.setting = setting; this.lane = lane; this.phase = phase;
        }

        /** Independent route-distance oracle: the finish is 30m into a 200m rectangular circuit. */
        private Vec3 location(int step) {
            double distance = (step * 200.0 / setting.period() + phase) % 200;
            double x, z;
            if (distance < 60) { x = -30 + distance; z = lane; }
            else if (distance < 100) { x = 30; z = lane + distance - 60; }
            else if (distance < 160) { x = 130 - distance; z = lane + 40; }
            else { x = -30; z = lane + 200 - distance; }
            double lift = setting.lift();
            if (setting.pulseEvery() > 0) {
                int lap = (int) Math.floor((step * 200.0 / setting.period() + phase) / 200);
                lift = lap % setting.pulseEvery() == setting.pulseEvery() - 1
                    ? setting.lift() * Math.exp(-Math.pow((distance - 30) / 8, 2)) : 0;
            }
            return campaign.origin.add(x, lift, z);
        }

        private String diagnosticState() {
            Player driver = campaign.drivers.stream().filter(player -> player.getUUID().equals(driverId)).findFirst().orElse(null);
            String riderState = driver == null ? "unknown" : "riderAlive=" + driver.isAlive() + " riderRemoved=" + driver.isRemoved() + " riderHealth=" + driver.getHealth();
            StringBuilder result = new StringBuilder();
            for (String name : List.of("lapStartedAt", "lapStartEligible", "lapTimerArmed", "lapMovementMeters", "lapDriverId", "lastStartFinishTriggerAt")) {
                try {
                    var field = OpenwheelCarEntity.class.getDeclaredField(name);
                    field.setAccessible(true);
                    result.append(name).append('=').append(field.get(this)).append(' ');
                } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
            }
            return result.append(riderState).toString();
        }

        @Override public void tick() {
            if (lastProcessedTick == level().getGameTime()) return;
            lastProcessedTick = level().getGameTime();
            if (getControllingPassenger() == null) campaign.helper.fail("Fixture lost its driver: " + setting.name() + " " + diagnosticState());
            Vec3 previous = position();
            Vec3 current = location(++steps);
            Vec3 movement = current.subtract(previous);
            setPos(current);
            double heading = Math.toDegrees(Math.atan2(movement.z, movement.x)) - 90 + setting.yawError();
            setYRot((float) heading);
            setDeltaMovement(movement);
            long before = OWRLapRecords.get((ServerLevel) level()).getLapCount();
            boolean crossed = Math.floor(((steps - 1) * 200.0 / setting.period() + phase - 30) / 200)
                < Math.floor((steps * 200.0 / setting.period() + phase - 30) / 200);
            if (crossed) crossings++;
            processRaceTimingMovement(previous, movement);
            long added = OWRLapRecords.get((ServerLevel) level()).getLapCount() - before;
            if (crossed && added == 0 && campaign.stage == 0) LogUtils.getLogger().warn("BASELINE MISS name={} step={} tick={} removed={} alive={} rider={} previous={} current={} yaw={} state={}",
                setting.name(), steps, level().getGameTime(), isRemoved(), isAlive(), getControllingPassenger(), previous, current, getYRot(), diagnosticState());
            if (crossed || added != 0) campaign.events.add(String.format(Locale.ROOT, "%d,%s,%d,%s,%d,%.6f,%.6f,%.6f",
                campaign.stage, setting.name(), level().getGameTime(), crossed, added, getX(), getY(), getZ()));
        }

    }
    /** Runs the actual car tick, including tickLapTimer, vehicle forces, collisions and movement scanning. */
    private static final class CoastingCar extends OpenwheelCarEntity {
        private final Campaign campaign;
        private final double lane;
        private long lastProcessedTick = Long.MIN_VALUE;
        private UUID driverId;
        private int crossings;

        CoastingCar(ServerLevel level, Campaign campaign, double lane) {
            super(OWREntities.PROTOTYPE_CAR.get(), level);
            this.campaign = campaign;
            this.lane = lane;
        }

        @Override public void tick() {
            if (lastProcessedTick == level().getGameTime()) return;
            lastProcessedTick = level().getGameTime();
            if (getX() > campaign.origin.x + 25) {
                for (Vec3 next : List.of(new Vec3(getX(), campaign.origin.y, campaign.origin.z + lane + 40),
                    campaign.origin.add(-30, 0, lane + 40), campaign.origin.add(-30, 0, lane))) {
                    Vec3 beforeReturn = position();
                    setPos(next);
                    processRaceTimingMovement(beforeReturn, next.subtract(beforeReturn));
                }
            }
            setYRot(-90);
            setDeltaMovement(new Vec3(4, 0, 0));
            Vec3 previous = position();
            int before = OWRLapRecords.get((ServerLevel) level()).getLapCount();
            super.tick();
            boolean crossed = previous.x < campaign.origin.x && getX() >= campaign.origin.x;
            if (crossed) crossings++;
            int added = OWRLapRecords.get((ServerLevel) level()).getLapCount() - before;
            if (crossed || added != 0) campaign.events.add(String.format(Locale.ROOT, "5,physics_lane_%.0f,%d,%s,%d,%.6f,%.6f,%.6f",
                lane, level().getGameTime(), crossed, added, getX(), getY(), getZ()));
        }
    }

}
