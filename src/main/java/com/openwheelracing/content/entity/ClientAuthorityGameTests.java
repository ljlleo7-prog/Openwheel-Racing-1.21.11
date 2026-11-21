package com.openwheelracing.content.entity;

import com.mojang.authlib.GameProfile;
import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.race.OWRLapRecords;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackDefinitionsData;
import com.openwheelracing.network.TechnicalRulesNetwork;
import com.openwheelracing.network.VehicleRollbackNetwork;
import com.openwheelracing.registry.OWREntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Real login admission, accepted client segments and server lap recording, including finish bursts. */
public final class ClientAuthorityGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, OpenwheelRacing.MODID);
    static {
        FUNCTIONS.register("client_authority", () -> ClientAuthorityGameTests::test);
        FUNCTIONS.register("shared_car_authority", () -> ClientAuthorityGameTests::sharedCar);
        FUNCTIONS.register("car_contacts", () -> ClientAuthorityGameTests::carContacts);
        FUNCTIONS.register("barrier_scraping", () -> ClientAuthorityGameTests::barrierScraping);
    }
    private ClientAuthorityGameTests() {}
    public static void register(IEventBus bus) { FUNCTIONS.register(bus); }

    private static final class Driver {
        final ServerPlayer player;
        TechnicalRulesNetwork.Challenge challenge;
        boolean rejected;
        final List<VehicleRollbackNetwork.Update> corrections = new ArrayList<>();
        Driver(GameTestHelper helper, String name) {
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
            player.setInvulnerable(true);
            new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND),
                player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) {
                    if (packet instanceof ClientboundCustomPayloadPacket payload
                            && payload.payload() instanceof TechnicalRulesNetwork.Challenge message) challenge = message;
                    if (packet instanceof ClientboundCustomPayloadPacket payload
                            && payload.payload() instanceof VehicleRollbackNetwork.Update update) corrections.add(update);
                }
                @Override public void disconnect(Component reason) { rejected = true; }
            };
            helper.getLevel().addNewPlayer(player);
        }
        void verify() {
            TechnicalRulesNetwork.answer(player, new TechnicalRulesNetwork.Answer(challenge.nonce(),
                PerformanceRules.fingerprint(), PerformanceRules.probe(challenge.preset())));
        }
        void close(GameTestHelper helper) {
            TechnicalRulesNetwork.clear(player.getUUID());
            player.stopRiding();
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
    }

    private static void test(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 origin = helper.absoluteVec(new Vec3(8, 3, 8));
        for (int cx = ((int) origin.x - 40) >> 4; cx <= ((int) origin.x + 40) >> 4; cx++)
            for (int cz = ((int) origin.z - 8) >> 4; cz <= ((int) origin.z + 50) >> 4; cz++)
                level.setChunkForced(cx, cz, true);
        for (int x = -36; x <= 36; x++) for (int z = -5; z <= 45; z++) {
            level.setBlock(BlockPos.containing(origin.add(x, -1, z)), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y < 4; y++) level.removeBlock(BlockPos.containing(origin.add(x, y, z)), false);
        }
        validateDelayedDrivingEchoes(helper, origin);
        validateTerrainAndClientEnergy(helper, origin);
        Driver mismatch = new Driver(helper, "technical-mismatch");
        TechnicalRulesNetwork.begin(mismatch.player);
        TechnicalRulesNetwork.answer(mismatch.player, new TechnicalRulesNetwork.Answer(mismatch.challenge.nonce(),
            "modified", PerformanceRules.probe(mismatch.challenge.preset())));
        helper.assertTrue(mismatch.rejected && !TechnicalRulesNetwork.verified(mismatch.player.getUUID()),
            "Modified performance definitions must be rejected");
        mismatch.close(helper);

        Driver driver = new Driver(helper, "accepted-motion");
        TechnicalRulesNetwork.begin(driver.player);
        UUID staleNonce = driver.challenge.nonce();
        TechnicalRulesNetwork.begin(driver.player);
        TechnicalRulesNetwork.answer(driver.player, new TechnicalRulesNetwork.Answer(staleNonce,
            PerformanceRules.fingerprint(), PerformanceRules.probe(driver.challenge.preset())));
        helper.assertTrue(!TechnicalRulesNetwork.verified(driver.player.getUUID()), "Old login proof must not authorize a new challenge");
        var admissionCar = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level);
        helper.assertTrue(!driver.player.startRiding(admissionCar, false, false), "Pending technical check must block mounting");
        admissionCar.discard();
        driver.verify();
        helper.assertTrue(TechnicalRulesNetwork.verified(driver.player.getUUID()), "Matching performance must pass");

        AtomicLong nanos = new AtomicLong();
        var car = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level, nanos::get);
        var reporter = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level);
        car.setPos(location(origin, 0)); car.setYRot(-90); car.setOnGround(true);
        driver.player.startRiding(car, true, false);
        var controls = OWRRaceControlState.get(level);
        controls.setMinimumValidLapTicks(1);
        if (controls.isOffTrackCheckEnabled()) controls.toggleOffTrackCheck();
        if (!controls.isCheckpointCheckEnabled()) controls.toggleCheckpointCheck();
        String dimension = level.dimension().identifier().toString();
        var track = TrackDefinition.empty(UUID.randomUUID(), "Client authority", dimension)
            .withStartFinish(new TrackDefinition.StartFinishLine(point(origin, 0, -5), point(origin, 0, 5), 0))
            .withStewardLines(List.of(
                new TrackDefinition.StewardLine(TrackDefinition.StewardLineType.CHECKPOINT, 1, "CP1", point(origin, 18, -5), point(origin, 18, 5), 0, 18),
                new TrackDefinition.StewardLine(TrackDefinition.StewardLineType.CHECKPOINT, 2, "CP2", point(origin, 19, -5), point(origin, 19, 5), 0, 19)));
        TrackDefinitionsData.get(level).upsert(track);
        TrackDefinitionsData.get(level).setActiveTrack(track.trackId(), dimension);
        List<VehicleRollbackNetwork.Frame> delayed = new ArrayList<>();
        final VehicleRollbackNetwork.Frame[] last = {null};
        helper.onEachTick(() -> {
            int step = (int) helper.getTick();
            if (step < 1 || step > 161) return;
            nanos.set(step * AcceptedMotionClock.STEP_NANOS);
            if (step <= 160) {
                Vec3 before = location(origin, step - 1), position = location(origin, step);
                Vec3 delta = position.subtract(before);
                reporter.setPos(position); reporter.setDeltaMovement(delta); reporter.setOnGround(true);
                reporter.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
                var reportedMotion = reporter.captureRollbackMotion();
                reportedMotion.state()[55] = step * 10.0;
                var frame = new VehicleRollbackNetwork.Frame(car.getId(), car.getRollbackEpoch(), step, step,
                    position, reporter.getYRot(), 0, 0, 0, false, false, 0, true, 0,
                    car.getUUID(), dimension, reportedMotion);
                delayed.add(frame); last[0] = frame;
                // Finish and both checkpoints arrive in one burst.
                boolean stall = step >= 4 && step <= 11 || step >= 44 && step <= 51;
                if (!stall) {
                    for (var queued : delayed) car.acceptClientMotionFrame(queued);
                    delayed.clear();
                }
                car.tick();
                if (!stall) {
                    helper.assertTrue(car.position().distanceToSqr(position) < 1e-8,
                        "Server must accept client pose without an extra physics step");
                    helper.assertTrue(Math.abs(car.getSpeedKmh() - delta.horizontalDistance() * 72) < .001,
                        "Accepted speed telemetry must track client velocity");
                    helper.assertTrue(car.getDebugFlLatForce() == step * 10.0,
                        "Force telemetry must come from the accepted frame");
                }
            } else {
                var rows = OWRLapRecords.get(level).getRecentLaps(0, Integer.MAX_VALUE).stream()
                    .filter(row -> row.driverId().equals(driver.player.getUUID()) && !row.invalidated()).toList();
                helper.assertTrue(rows.size() == 3, "Accepted client laps must record once, including burst crossings: " + rows.size());
                for (var row : rows) helper.assertTrue(row.lapMillis() == 2000,
                    "Arrival bursts must not shorten or lengthen a 40-frame lap: " + row.lapMillis());
                long count = OWRLapRecords.get(level).getLapCount();
                Vec3 pose = car.position();
                car.acceptClientMotionFrame(last[0]); car.tick();
                helper.assertTrue(car.position().equals(pose) && OWRLapRecords.get(level).getLapCount() == count,
                    "Duplicate motion must not advance car or duplicate timing");
                reporter.setPos(pose.add(100, 0, 0));
                var impossible = new VehicleRollbackNetwork.Frame(car.getId(), car.getRollbackEpoch(), 161, 161,
                    reporter.position(), reporter.getYRot(), 0, 0, 0, false, false, 0, true, 0,
                    car.getUUID(), dimension, reporter.captureRollbackMotion());
                nanos.addAndGet(AcceptedMotionClock.STEP_NANOS);
                car.acceptClientMotionFrame(impossible); car.tick();
                helper.assertTrue(car.position().equals(pose) && OWRLapRecords.get(level).getLapCount() == count,
                    "Rejected teleport must not move car or create a lap");
                helper.assertTrue(!driver.rejected, "Motion disagreement alone must not accuse technical cheating");
                helper.assertTrue(car.getDeltaMovement().equals(Vec3.ZERO),
                    "A rejection must not restore momentum into the same failed path");
                int corrections = driver.corrections.size();
                for (int i = 0; i < 100; i++) car.acceptClientMotionFrame(impossible);
                helper.assertTrue(driver.corrections.size() == corrections,
                    "Old-epoch traffic must not create a correction storm");
                car.acceptClientMotionFrame(new VehicleRollbackNetwork.Frame(car.getId(), car.getRollbackEpoch(), 161, 161,
                    impossible.position(), impossible.yaw(), 1, 0, 0, false, false, 0, true, 0,
                    car.getUUID(), dimension, impossible.motion()));
                car.tick();
                helper.assertTrue(driver.corrections.getLast().status() == VehicleRollbackNetwork.BLOCKED,
                    "Repeated rejection at the same spot must require a fresh pedal action");
                reporter.setPos(pose);
                var tampered = reporter.captureRollbackMotion();
                tampered.state()[8] = 2.0;
                car.acceptClientMotionFrame(new VehicleRollbackNetwork.Frame(car.getId(), car.getRollbackEpoch(), 161, 161,
                    pose, reporter.getYRot(), 0, 0, 0, false, false, 0, true, 0, car.getUUID(), dimension, tampered));
                helper.assertTrue(driver.rejected, "Modified grip envelope must be rejected as a technical violation");
                driver.close(helper); car.discard(); reporter.discard();
                TrackDefinitionsData.get(level).remove(track.trackId(), dimension);
                for (int cx = ((int) origin.x - 40) >> 4; cx <= ((int) origin.x + 40) >> 4; cx++)
                    for (int cz = ((int) origin.z - 8) >> 4; cz <= ((int) origin.z + 50) >> 4; cz++)
                        level.setChunkForced(cx, cz, false);
                helper.succeed();
            }
        });
    }

    private static void validateDelayedDrivingEchoes(GameTestHelper helper, Vec3 origin) {
        var seed = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), helper.getLevel());
        seed.setPos(origin.add(0, 0, 20)); seed.setYRot(-90); seed.setOnGround(true); seed.shiftLocal(1);
        var oracle = seed.replayVehicle(seed.captureRollbackCheckpoint());
        var local = seed.replayVehicle(seed.captureRollbackCheckpoint());
        var oldBehavior = seed.replayVehicle(seed.captureRollbackCheckpoint());
        var history = new ArrayList<OpenwheelCarEntity.RollbackCheckpoint>();
        history.add(oracle.captureRollbackCheckpoint());
        double oldVelocityError = 0;
        for (int tick = 1; tick <= 80; tick++) {
            for (var car : List.of(oracle, local, oldBehavior)) {
                car.setPos(origin.add(0, 0, 20)); car.setOnGround(true);
                if (tick == 30 || tick == 55) car.shiftLocal(1);
            }
            if (tick > 8) {
                var echo = history.get(tick - 9).data();
                local.applyLocalDrivingMetadata(echo);
                oldBehavior.getEntityData().assignValues(echo);
            }
            for (var car : List.of(oracle, local, oldBehavior)) {
                float steering = tick > 40 ? 0.2f : 0;
                boolean clear = car.replayRollbackFrame(new VehicleRollbackNetwork.Frame(car.getId(), 0, tick, tick,
                    car.position(), car.getYRot(), 1, 0, steering, true, false, car.getGear(), true, 0,
                    car.getUUID(), car.level().dimension().identifier().toString(), car.captureRollbackMotion()));
                helper.assertTrue(clear, "Echo fixture must remain clear of walls");
            }
            history.add(oracle.captureRollbackCheckpoint());
            helper.assertTrue(local.getDeltaMovement().distanceToSqr(oracle.getDeltaMovement()) < 1e-16
                    && Math.abs(local.getYRot() - oracle.getYRot()) < 1e-6
                    && local.getRpm() == oracle.getRpm() && local.getGear() == oracle.getGear(),
                "Delayed observer metadata must not change acceleration, steering, RPM or local shifts at frame " + tick);
            oldVelocityError = Math.max(oldVelocityError,
                oldBehavior.getDeltaMovement().distanceTo(oracle.getDeltaMovement()));
        }
        helper.assertTrue(oldVelocityError > 1e-4,
            "Control fixture must reproduce handling interference from unfiltered delayed metadata");
        oracle.setDamagePercent(20);
        local.applyLocalDrivingMetadata(oracle.captureRollbackCheckpoint().data());
        helper.assertTrue(local.getDamagePercent() == oracle.getDamagePercent(),
            "Protecting local driving state must still accept server damage");
        // An explicit service transition is permitted to replace local solver state.
        try {
            var field = OpenwheelCarEntity.class.getDeclaredField("PIT_STOP_TICKS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var serviceTicks = (net.minecraft.network.syncher.EntityDataAccessor<Integer>) field.get(null);
            var service = new ArrayList<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>>(
                seed.captureRollbackCheckpoint().data());
            service.add(net.minecraft.network.syncher.SynchedEntityData.DataValue.create(serviceTicks, 3));
            local.applyLocalDrivingMetadata(service);
            helper.assertTrue(local.getPitStopTicks() == 3 && local.getGear() == seed.getGear()
                    && local.getRpm() == seed.getRpm(),
                "Explicit service updates must bypass observer-echo protection");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Missing service metadata", exception);
        }
        seed.discard(); oracle.discard(); local.discard(); oldBehavior.discard();
    }

    private static void barrierScraping(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 origin = helper.absoluteVec(new Vec3(8.5, 3, 8.5));
        var chunks = new ArrayList<net.minecraft.world.level.ChunkPos>();
        for (int x = ((int) origin.x - 12) >> 4; x <= ((int) origin.x + 12) >> 4; x++)
            for (int z = ((int) origin.z - 75) >> 4; z <= ((int) origin.z + 75) >> 4; z++) {
                level.setChunkForced(x, z, true); chunks.add(new net.minecraft.world.level.ChunkPos(x, z));
            }
        for (int x = -9; x <= 9; x++) for (int z = -70; z <= 70; z++) {
            level.setBlock(BlockPos.containing(origin.add(x, -1, z)), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y < 4; y++) level.removeBlock(BlockPos.containing(origin.add(x, y, z)), false);
        }
        int wallX = BlockPos.containing(origin.add(3, 0, 0)).getX();
        for (int z = -68; z <= 68; z++) for (int y = 0; y < 3; y++)
            level.setBlock(new BlockPos(wallX, (int) origin.y + y, (int) origin.z + z),
                com.openwheelracing.registry.OWRBlocks.BARRIER.get().defaultBlockState(), 3);
        // Include kerbs beside both directions of the barrier, not just a perfectly flat scrape.
        for (int z : new int[] {-8, -7, -6, 5, 6, 7}) for (int x = -2; x <= 2; x++)
            level.setBlock(BlockPos.containing(origin.add(x, 0, z)), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 3);
        try {
            Driver embeddedDriver = new Driver(helper, "embedded-wall-retreat");
            TechnicalRulesNetwork.begin(embeddedDriver.player); embeddedDriver.verify();
            AtomicLong embeddedNanos = new AtomicLong();
            var embedded = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level, embeddedNanos::get);
            try {
                embedded.setPos(wallX + .1, origin.y, origin.z + 20);
                embeddedDriver.player.startRiding(embedded, true, false);
                int epoch = embedded.getRollbackEpoch();
                int corrections = embeddedDriver.corrections.size();
                // Include an idle embedded frame, then retreat without reporting contact.
                // Geometry disagreement must not require a collision flag to permit escape.
                for (int sequence = 1; sequence <= 20; sequence++) {
                    var previous = embedded.captureRollbackMotion();
                    Vec3 movement = new Vec3(sequence == 1 ? 0 : -.2, 0, 0);
                    embedded.setPos(previous.position().add(movement));
                    embedded.setDeltaMovement(movement);
                    var motion = embedded.captureRollbackMotion();
                    embedded.restoreRollbackMotion(previous);
                    embedded.acceptClientMotionFrame(new VehicleRollbackNetwork.Frame(embedded.getId(), epoch,
                        sequence, sequence, motion.position(), motion.yaw(), 0, 0, 0, true,
                        false, embedded.getGear(), true, 0, embedded.getUUID(),
                        level.dimension().identifier().toString(), motion));
                    embeddedNanos.addAndGet(AcceptedMotionClock.STEP_NANOS); embedded.tick();
                    helper.assertTrue(embedded.getRollbackEpoch() == epoch
                            && embedded.position().distanceToSqr(motion.position()) < 1e-8
                            && embeddedDriver.corrections.size() == corrections,
                        "Embedded idle/retreat must remain client-controlled without correction or pedal latch");
                }
                helper.assertTrue(embedded.getX() < wallX - 3, "Driver must retreat clear of the wall");
            } finally { embeddedDriver.close(helper); embedded.discard(); }
            int caseIndex = 0;
            for (double speed : new double[] {.1, 1, 5.5}) for (int direction : new int[] {-1, 1}) {
                Driver driver = new Driver(helper, "barrier-scrape-" + caseIndex++);
                TechnicalRulesNetwork.begin(driver.player); driver.verify();
                AtomicLong nanos = new AtomicLong();
                var server = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level, nanos::get);
                var client = new ClientPhysicsCar(level);
                float heading = direction == 1 ? 0 : 180;
                client.setYRot(heading); client.setPos(origin);
                double edge = OpenwheelCarEntity.componentBoundingBox(client).maxX - client.getX();
                Vec3 start = new Vec3(wallX - edge - .025, origin.y, origin.z);
                client.setPos(start); client.setOnGround(true); client.shiftLocal(1); client.shiftLocal(1); client.shiftLocal(1);
                server.setPos(start); server.setYRot(heading + 3); server.setOnGround(true);
                driver.player.startRiding(server, true, false);
                int epoch = server.getRollbackEpoch(), contacts = 0;
                java.lang.reflect.Field side = null;
                try {
                    side = net.minecraft.world.level.Level.class.getDeclaredField("isClientSide"); side.setAccessible(true);
                    int frames = speed > 5 ? 9 : 60;
                    for (int sequence = 1; sequence <= frames; sequence++) {
                        client.setYRot(heading); client.stopRejectedClientMotion();
                        client.setDeltaMovement(.12, 0, speed * direction);
                        side.setBoolean(level, true);
                        client.replayRollbackFrame(physicsFrame(client, sequence, 0, 0));
                        side.setBoolean(level, false);
                        var motion = client.captureRollbackMotion();
                        if (motion.counters()[17] == 1) contacts++;
                        server.acceptClientMotionFrame(new VehicleRollbackNetwork.Frame(server.getId(), server.getRollbackEpoch(),
                            sequence, sequence, motion.position(), motion.yaw(), 0, 0, 0, true,
                            client.horizontalCollision, server.getGear(), true, 0, server.getUUID(),
                            level.dimension().identifier().toString(), motion));
                        nanos.addAndGet(AcceptedMotionClock.STEP_NANOS); server.tick();
                        helper.assertTrue(client.getY() >= origin.y - .001, "Barrier scraping cannot sink through the road");
                        helper.assertTrue(server.getRollbackEpoch() == epoch && server.position().distanceToSqr(client.position()) < 1e-8,
                            "Legal barrier scrape must be accepted without resync/pedal latch: speed=" + speed
                                + ", direction=" + direction + ", frame=" + sequence + ", " + server.rollbackDiagnostics());
                        helper.assertTrue(Math.abs(server.getYawRateRadiansPerSecond() - client.getYawRateRadiansPerSecond()) < 1e-9,
                            "Server damage processing cannot apply the client's rebound torque twice");
                    }
                    double scrapeX = client.getX();
                    client.stopRejectedClientMotion();
                    int escapeFrames = speed > 5 ? 3 : 6;
                    for (int extra = 1; extra <= escapeFrames; extra++) {
                        int sequence = frames + extra;
                        client.setYRot(direction == 1 ? 30 : 150);
                        client.setDeltaMovement(-.4, 0, speed * direction);
                        side.setBoolean(level, true); client.replayRollbackFrame(physicsFrame(client, sequence, 0, 0));
                        side.setBoolean(level, false);
                        var motion = client.captureRollbackMotion();
                        server.acceptClientMotionFrame(new VehicleRollbackNetwork.Frame(server.getId(), server.getRollbackEpoch(),
                            sequence, sequence, motion.position(), motion.yaw(), 0, 0, 0, true,
                            client.horizontalCollision, server.getGear(), true, 0, server.getUUID(),
                            level.dimension().identifier().toString(), motion));
                        nanos.addAndGet(AcceptedMotionClock.STEP_NANOS); server.tick();
                        helper.assertTrue(server.getRollbackEpoch() == epoch && server.position().distanceToSqr(client.position()) < 1e-8,
                            "Steering away from a scrape must not repeat a movement resync");
                    }
                    helper.assertTrue(client.getX() < scrapeX - .3, "Driver must be able to escape the barrier: speed=" + speed + ", direction=" + direction
                            + ", beforeX=" + scrapeX + ", after=" + client.position());
                    helper.assertTrue(contacts > 0, "Scrape fixture must actually contact the barrier");
                    helper.assertTrue(Math.abs(client.getZ() - start.z) > speed * frames * .4,
                        "Scraping must retain useful tangential progress");
                    helper.assertTrue(server.getDamagePercent() < 5, "Gentle normal scrubbing must not damage a fast car severely");
                    helper.assertTrue(!driver.rejected, "A legal barrier scrape is not a technical violation");
                } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
                finally {
                    if (side != null) try { side.setBoolean(level, false); }
                        catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
                    driver.close(helper); server.discard(); client.discard();
                }
            }
            // Hard head-on contact now produces its rebound locally, rather than awaiting server velocity.
            var client = new ClientPhysicsCar(level);
            client.setYRot(-90); client.setPos(origin);
            double edge = OpenwheelCarEntity.componentBoundingBox(client).maxX - client.getX();
            client.setPos(wallX - edge - .02, origin.y, origin.z); client.setOnGround(true);
            client.shiftLocal(1); client.shiftLocal(1); client.shiftLocal(1); client.setDeltaMovement(1.5, 0, 0);
            java.lang.reflect.Field side = null;
            try {
                side = net.minecraft.world.level.Level.class.getDeclaredField("isClientSide"); side.setAccessible(true);
                side.setBoolean(level, true); client.replayRollbackFrame(physicsFrame(client, 1, 0, 0));
                side.setBoolean(level, false);
                helper.assertTrue(client.captureRollbackMotion().counters()[17] == 1 && client.getDeltaMovement().x < -.11,
                    "Client head-on barrier rebound must exceed the previous .10 blocks/tick cap: " + client.getDeltaMovement());
                helper.assertTrue(client.getDeltaMovement().y <= 0, "Barrier rebound must not launch the car");
                var lever = OpenwheelCarEntity.class.getDeclaredField("preemptiveBarrierLever"); lever.setAccessible(true);
                lever.set(client, new Vec3(2, 0, 2));
                double yawBefore = client.getYawRateRadiansPerSecond();
                var bounce = OpenwheelCarEntity.class.getDeclaredMethod("bounceFromBarrier", Vec3.class, Vec3.class, double.class);
                bounce.setAccessible(true);
                Vec3 reflected = (Vec3) bounce.invoke(client, new Vec3(1.5, 0, 2), new Vec3(-1, 0, 0), 1.0);
                helper.assertTrue(client.getYawRateRadiansPerSecond() == yawBefore && reflected.x < 0 && reflected.z > 1.9,
                    "Offset barrier rebound must preserve steering yaw without impact torque");
                var validator = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level);
                validator.setPos(wallX - edge - .02, origin.y, origin.z); validator.setYRot(-90); validator.setOnGround(true);
                helper.assertTrue(!validator.clientMotionPathClear(validator.position(), new Vec3(6, 0, 0), -90,
                    client.captureRollbackMotion()), "A claimed scrape cannot authorize driving through the barrier");
                validator.discard();
            } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
            finally {
                if (side != null) try { side.setBoolean(level, false); }
                    catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
                client.discard();
            }
            helper.succeed();
        } finally { for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false); }
    }

    private static void carContacts(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 origin = helper.absoluteVec(new Vec3(8.5, 3, 8.5));
        var chunks = new ArrayList<net.minecraft.world.level.ChunkPos>();
        for (int x = ((int) origin.x - 15) >> 4; x <= ((int) origin.x + 25) >> 4; x++)
            for (int z = ((int) origin.z - 15) >> 4; z <= ((int) origin.z + 15) >> 4; z++) {
                level.setChunkForced(x, z, true); chunks.add(new net.minecraft.world.level.ChunkPos(x, z));
            }
        for (int x = -12; x <= 22; x++) for (int z = -12; z <= 12; z++) {
            level.setBlock(BlockPos.containing(origin.add(x, -1, z)), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y < 6; y++) level.removeBlock(BlockPos.containing(origin.add(x, y, z)), false);
        }
        validateRemoteContactPresentation(helper, origin);
        Driver a = new Driver(helper, "contact-driver-a"), b = new Driver(helper, "contact-driver-b");
        for (var driver : List.of(a, b)) { TechnicalRulesNetwork.begin(driver.player); driver.verify(); }
        AtomicLong nanos = new AtomicLong();
        var first = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level, nanos::get);
        var second = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level, nanos::get);
        level.addFreshEntity(first); level.addFreshEntity(second);
        try {
            // A complete sideways crossing in one frame must be stopped before the other car.
            first.setPos(origin); second.setPos(origin.add(3.5, 0, 0));
            first.setYRot(0); second.setYRot(0); first.setOnGround(true); second.setOnGround(true);
            a.player.startRiding(first, true, false); b.player.startRiding(second, true, false);
            int firstEpoch = first.getRollbackEpoch(), secondEpoch = second.getRollbackEpoch();
            var staleTarget = contactFrame(second, 1, second.position().add(-2, 0, 0), new Vec3(-2, 0, 0));
            second.acceptClientMotionFrame(staleTarget);
            first.acceptClientMotionFrame(contactFrame(first, 1, origin.add(6.2, 0, 0), new Vec3(6.2, 0, 0)));
            first.tick();
            helper.assertTrue(OpenwheelCarEntity.componentBoundingBox(first).maxX
                    <= OpenwheelCarEntity.componentBoundingBox(second).minX + .05,
                "Fast crossing must be clipped before interpenetration, even when the end pose is beyond the target");
            helper.assertTrue(first.getRollbackEpoch() > firstEpoch && second.getRollbackEpoch() > secondEpoch,
                "Both owners need a fresh contact epoch");
            helper.assertTrue(a.corrections.getLast().status() == VehicleRollbackNetwork.CAR_CONTACT
                    && b.corrections.getLast().status() == VehicleRollbackNetwork.CAR_CONTACT,
                "Both drivers must receive explicit contact state");
            helper.assertTrue(first.getDeltaMovement().x < 6.2 && second.getDeltaMovement().x > 0,
                "Impact must transfer momentum to the struck car");
            helper.assertTrue(first.getYawRateRadiansPerSecond() == 0 && second.getYawRateRadiansPerSecond() == 0,
                "Off-center car impact must not generate collision torque");
            helper.assertTrue(first.getDeltaMovement().y == 0 && second.getDeltaMovement().y == 0,
                "Car contacts must not introduce launch velocity");
            double pushed = second.getDeltaMovement().x;
            second.acceptClientMotionFrame(staleTarget); second.tick();
            helper.assertTrue(second.getDeltaMovement().x == pushed,
                "Queued pre-impact target frames cannot erase the transferred impulse");

            // Head-on normal rebound with equal masses, without deleting shared tangential motion.
            first.setPos(origin); second.setPos(origin.add(8, 0, 0));
            first.setYRot(-90); second.setYRot(-90);
            second.setDeltaMovement(-6, 0, 1);
            int sequence = a.corrections.getLast().frame() + 1;
            first.acceptClientMotionFrame(contactFrame(first, sequence, origin.add(6, 0, 1), new Vec3(6, 0, 1)));
            nanos.addAndGet(AcceptedMotionClock.STEP_NANOS); first.tick();
            helper.assertTrue(first.getDeltaMovement().x < 0 && second.getDeltaMovement().x > 0,
                "Head-on contact must rebound rather than cross through");
            helper.assertTrue(Math.abs(first.getDeltaMovement().z - 1) < 1e-7
                    && Math.abs(second.getDeltaMovement().z - 1) < 1e-7,
                "Normal impulse must preserve tangential velocity");

            // Same-speed convoy motion must not be treated as a crash into a stationary packet pose.
            first.setPos(origin); second.setPos(origin.add(7, 0, 0));
            first.setYRot(-90); second.setYRot(-90); first.stopRejectedClientMotion(); second.stopRejectedClientMotion();
            first.setDeltaMovement(4, 0, 0); second.setDeltaMovement(4, 0, 0);
            firstEpoch = first.getRollbackEpoch(); secondEpoch = second.getRollbackEpoch();
            sequence = a.corrections.getLast().frame() + 1;
            first.acceptClientMotionFrame(contactFrame(first, sequence, origin.add(4, 0, 0), new Vec3(4, 0, 0)));
            int targetSequence = b.corrections.getLast().frame() + 1;
            second.acceptClientMotionFrame(contactFrame(second, targetSequence, origin.add(11, 0, 0), new Vec3(4, 0, 0)));
            nanos.addAndGet(AcceptedMotionClock.STEP_NANOS); first.tick(); second.tick();
            helper.assertTrue(first.getRollbackEpoch() == firstEpoch && second.getRollbackEpoch() == secondEpoch
                    && first.getDeltaMovement().x == 4 && second.getDeltaMovement().x == 4,
                "Matched-speed convoy must keep driving without phantom impulses");

            // Vertically separated cars (bridges) are not contacts.
            first.setPos(origin); second.setPos(origin.add(3.5, 3, 0));
            first.setYRot(0); second.setYRot(0); second.setDeltaMovement(Vec3.ZERO);
            firstEpoch = first.getRollbackEpoch(); sequence++;
            first.acceptClientMotionFrame(contactFrame(first, sequence, origin.add(6.2, 0, 0), new Vec3(6.2, 0, 0)));
            nanos.addAndGet(AcceptedMotionClock.STEP_NANOS); first.tick();
            helper.assertTrue(first.getRollbackEpoch() == firstEpoch && first.position().equals(origin.add(6.2, 0, 0)),
                "An overpass must not create an air-wall car contact");
            // Client prediction stops at contact and cannot directly push or steer the remote entity.
            first.setPos(origin); first.setYRot(0); first.stopRejectedClientMotion(); first.setDeltaMovement(6, 0, 0);
            second.setPos(origin.add(3.5, 0, 0)); second.setYRot(0); second.stopRejectedClientMotion();
            var local = new ClientPhysicsCar(level);
            local.setUUID(first.getUUID()); local.setPos(origin); local.setYRot(0); local.setOnGround(true);
            local.setDeltaMovement(6, 0, 0); local.shiftLocal(1); local.shiftLocal(1); local.shiftLocal(1);
            int oldOtherEpoch = second.getRollbackEpoch();
            java.lang.reflect.Field side = null;
            try {
                side = net.minecraft.world.level.Level.class.getDeclaredField("isClientSide");
                side.setAccessible(true); side.setBoolean(level, true);
                local.replayRollbackFrame(physicsFrame(local, 1, 0, 0));
                side.setBoolean(level, false);
                var predicted = local.captureRollbackMotion();
                helper.assertTrue(predicted.counters()[16] == 1
                        && OpenwheelCarEntity.componentBoundingBox(local).maxX <= OpenwheelCarEntity.componentBoundingBox(second).minX + .05,
                    "Client physics must clip the contact immediately without waiting for a response");
                helper.assertTrue(second.getDeltaMovement().equals(Vec3.ZERO) && second.getRollbackEpoch() == oldOtherEpoch,
                    "Local prediction must never mutate the remote car");
                first.acceptClientMotionFrame(new VehicleRollbackNetwork.Frame(first.getId(), first.getRollbackEpoch(),
                    ++sequence, sequence, predicted.position(), predicted.yaw(), 0, 0, 0, true, false,
                    first.getGear(), true, 0, first.getUUID(), level.dimension().identifier().toString(), predicted));
                nanos.addAndGet(AcceptedMotionClock.STEP_NANOS); first.tick();
                helper.assertTrue(second.getDeltaMovement().horizontalDistance() > .01
                        && second.getRollbackEpoch() > oldOtherEpoch,
                    "A locally clipped contact must still transfer its pre-impact impulse on the server");
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to exercise client car contacts", exception);
            } finally {
                if (side != null) try { side.setBoolean(level, false); }
                    catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
                local.discard();
            }

            // A tyreless/serviced target remains fixed and retains its server-owned hold.
            first.setPos(origin); first.setYRot(0); first.stopRejectedClientMotion();
            second.setPos(origin.add(3.5, 0, 0)); second.stopRejectedClientMotion(); second.setTyresFitted(false);
            first.acceptClientMotionFrame(contactFrame(first, ++sequence, origin.add(6.2, 0, 0), new Vec3(6.2, 0, 0)));
            nanos.addAndGet(AcceptedMotionClock.STEP_NANOS); first.tick();
            helper.assertTrue(second.position().equals(origin.add(3.5, 0, 0)) && second.getDeltaMovement().equals(Vec3.ZERO)
                    && b.corrections.getLast().status() == VehicleRollbackNetwork.SERVER_CONTROL,
                "Contact cannot move a suspended car or accidentally return its driver authority");
            helper.assertTrue(!a.rejected && !b.rejected, "Ordinary contacts are not technical-rule violations");
            helper.succeed();
        } finally {
            a.close(helper); b.close(helper); first.discard(); second.discard();
            for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false);
        }
    }

    private static void validateRemoteContactPresentation(GameTestHelper helper, Vec3 origin) {
        var level = helper.getLevel();
        AtomicLong now = new AtomicLong();
        var own = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level, now::get);
        var remote = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level, now::get);
        level.addFreshEntity(own); level.addFreshEntity(remote);
        java.lang.reflect.Field side = null;
        try {
            var query = OpenwheelCarEntity.class.getDeclaredMethod("firstCarContact",
                Vec3.class, Vec3.class, Vec3.class, double.class);
            query.setAccessible(true);
            side = net.minecraft.world.level.Level.class.getDeclaredField("isClientSide"); side.setAccessible(true);
            own.setPos(origin); own.setYRot(0);
            remote.setPos(origin.add(3.5, 0, 0)); remote.setYRot(0);
            var visible = remote.remoteContactPose();
            remote.setPos(origin.add(15, 0, 0)); remote.setYRot(90);
            helper.assertTrue(remote.remoteContactPose().equals(visible), "Packet must preserve visible pose at retarget");
            Vec3 crossing = new Vec3(6, 0, 0);
            side.setBoolean(level, true);
            helper.assertTrue(query.invoke(own, origin, crossing, crossing, .015) != null,
                "Client must collide with visible car even when logical car lies outside ordinary broad phase");
            helper.assertTrue(remote.position().equals(origin.add(15, 0, 0)) && remote.getYRot() == 90,
                "Presentation queries must not mutate remote logical position or yaw");
            side.setBoolean(level, false);
            helper.assertTrue(query.invoke(own, origin, crossing, crossing, .015) == null,
                "Server collision must retain accepted logical poses");
            side.setBoolean(level, true);
            now.set(50_000_000);
            helper.assertTrue(query.invoke(own, origin, crossing, crossing, .015) == null,
                "Old visual collider must disappear when presentation reaches target");

            remote.clearRemoteContactPose(); remote.setPos(origin.add(3.5, 3, 0)); remote.remoteContactPose();
            remote.setPos(origin.add(3.5, 0, 0)); remote.remoteContactPose();
            helper.assertTrue(query.invoke(own, origin, crossing, crossing, .015) == null,
                "Visible overpass car must not collide with its newly received ground-level pose");
            now.addAndGet(50_000_000);
            helper.assertTrue(query.invoke(own, origin, crossing, crossing, .015) != null,
                "Ground-level presentation must become collidable after height transition");

            remote.clearRemoteContactPose(); remote.setPos(origin.add(3.5, 0, 0)); remote.setYRot(0);
            remote.remoteContactPose(); remote.setYRot(90); remote.remoteContactPose();
            Vec3 beside = origin.add(0, 0, 4);
            own.setPos(beside);
            helper.assertTrue(query.invoke(own, beside, crossing, crossing, .015) != null,
                "Contact geometry must use visible heading during a sharp network turn");
            now.addAndGet(50_000_000);
            helper.assertTrue(query.invoke(own, beside, crossing, crossing, .015) == null,
                "Contact geometry must follow the completed visible turn");
            own.setPos(origin);

            remote.setPos(origin.add(100, 0, 0)); remote.remoteContactPose();
            helper.assertTrue(query.invoke(own, origin, crossing, crossing, .015) == null,
                "Teleport must not retain a collider at the previous visible position");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to exercise shared render/contact poses", exception);
        } finally {
            if (side != null) try { side.setBoolean(level, false); }
                catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
            own.discard(); remote.discard();
        }
    }

    private static VehicleRollbackNetwork.Frame contactFrame(OpenwheelCarEntity car, int sequence, Vec3 position, Vec3 velocity) {
        var snapshot = car.captureRollbackMotion();
        double[] state = snapshot.state().clone(); int[] counters = snapshot.counters().clone();
        state[71] = state[72] = state[73] = 0; counters[16] = 0;
        var motion = new OpenwheelCarEntity.RollbackMotion(position, velocity, car.getYRot(), true,
            snapshot.tick(), state, counters);
        return new VehicleRollbackNetwork.Frame(car.getId(), car.getRollbackEpoch(), sequence, sequence,
            position, car.getYRot(), 0, 0, 0, false, false, car.getGear(), true, 0,
            car.getUUID(), car.level().dimension().identifier().toString(), motion);
    }

    private static void sharedCar(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 origin = helper.absoluteVec(new Vec3(8.5, 3, 8.5));
        var chunks = new ArrayList<net.minecraft.world.level.ChunkPos>();
        for (int cx = ((int) origin.x - 12) >> 4; cx <= ((int) origin.x + 12) >> 4; cx++)
            for (int cz = ((int) origin.z - 12) >> 4; cz <= ((int) origin.z + 12) >> 4; cz++) {
                level.setChunkForced(cx, cz, true); chunks.add(new net.minecraft.world.level.ChunkPos(cx, cz));
            }
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) {
            level.setBlock(BlockPos.containing(origin.add(x, -1, z)), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y < 8; y++) level.removeBlock(BlockPos.containing(origin.add(x, y, z)), false);
        }
        level.setBlock(BlockPos.containing(origin.add(0, -1, 0)),
            com.openwheelracing.registry.OWRBlocks.PIT_STOP_MARK.get().defaultBlockState(), 3);
        Driver driver = new Driver(helper, "shared-driver"), replacement = new Driver(helper, "replacement-driver");
        Driver crew = new Driver(helper, "pit-crew"), colleague = new Driver(helper, "second-technician");
        for (var actor : List.of(driver, replacement, crew, colleague)) {
            TechnicalRulesNetwork.begin(actor.player); actor.verify();
            actor.player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            actor.player.setPos(origin.add(4, 0, 0));
        }
        AtomicLong nanos = new AtomicLong();
        var car = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level, nanos::get);
        car.setPos(origin); car.setOnGround(true); level.addFreshEntity(car);
        try {
            helper.assertTrue(driver.player.startRiding(car, false, false), "Driver must mount");
            helper.assertTrue(!replacement.player.startRiding(car, false, false), "Another driver cannot take an occupied seat");
            int initialEpoch = car.getRollbackEpoch();
            var queued = stationaryFrame(car, 1, origin.add(.2, 0, 0));
            car.acceptClientMotionFrame(queued);
            car.setTyreWearPercent(25);
            crew.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(com.openwheelracing.registry.OWRItems.JACK.get()));
            car.interact(crew.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(car.isJacked() && car.isDrivingSuspended() && car.getRollbackEpoch() != initialEpoch,
                "Jack operation must take motion authority and invalidate queued input");
            var initialHold = driver.corrections.getLast();
            int phase = car.getTyreServicePhase();
            colleague.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            car.interact(colleague.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(car.getTyreServicePhase() == phase, "Competing technician cannot mutate an active action");
            car.beginRecovery(colleague.player);
            helper.assertTrue(!car.isRecoveryActive(), "Grabber cannot interrupt jack/service");
            while (car.getPitStopTicks() > 0) {
                nanos.addAndGet(AcceptedMotionClock.STEP_NANOS); car.acceptClientMotionFrame(queued); car.tick();
                helper.assertTrue(car.position().equals(origin), "Late driver frames cannot move a jacked car");
            }
            // Jack up/down without touching tyres must preserve the set and its wear.
            car.interact(crew.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            finishAction(car, nanos);
            helper.assertTrue(!car.isJacked() && car.hasTyres() && car.getTyreWearPercent() == 25,
                "Lowering directly must not require or replace tyres");
            helper.assertTrue(driver.corrections.getLast().status() == VehicleRollbackNetwork.DRIVER_CONTROL,
                "Finished service must explicitly return authority to the driver");
            car.acceptClientMotionFrame(queued); car.tick();
            helper.assertTrue(car.position().equals(origin), "Pre-service frames cannot undo the service handoff");

            // Different technicians can carry out independent idle actions.
            crew.player.getCooldowns().removeCooldown(net.minecraft.resources.Identifier.fromNamespaceAndPath("openwheelracing", "jack"));
            car.interact(crew.player, net.minecraft.world.InteractionHand.MAIN_HAND); finishAction(car, nanos);
            int oldItems = colleague.player.getInventory().countItem(com.openwheelracing.registry.OWRItems.TIRES.get());
            car.interact(colleague.player, net.minecraft.world.InteractionHand.MAIN_HAND); finishAction(car, nanos);
            helper.assertTrue(!car.hasTyres()
                    && colleague.player.getInventory().countItem(com.openwheelracing.registry.OWRItems.TIRES.get()) == oldItems + 1,
                "Removing fitted tyres must return one set to the actual technician: phase=" + car.getTyreServicePhase()
                    + ", fitted=" + car.hasTyres() + ", count=" + colleague.player.getInventory().countItem(com.openwheelracing.registry.OWRItems.TIRES.get()));
            var returned = colleague.player.getInventory().getItem(0);
            helper.assertTrue(com.openwheelracing.content.item.TyreItem.getRemainingPercent(returned) == 75,
                "Returned set must preserve remaining life");
            colleague.player.getInventory().setItem(1, returned.copy());
            colleague.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            car.interact(colleague.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(!car.hasTyres() && car.getPitStopTicks() == 0,
                "Repeated empty-hand removal cannot duplicate tyres");
            car.interact(crew.player, net.minecraft.world.InteractionHand.MAIN_HAND); finishAction(car, nanos);
            helper.assertTrue(!car.isJacked() && !car.hasTyres() && car.isDrivingSuspended(),
                "Tyreless cars may be lowered but must not drive");
            helper.assertTrue(!car.createPickupItem().getOrDefault(
                com.openwheelracing.registry.OWRDataComponents.TYRES_FITTED.get(), true),
                "Packaging a tyreless car must not mint fitted tyres");
            var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
            car.addAdditionalSaveData(saved);
            var reloaded = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level);
            reloaded.readAdditionalSaveData(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
            helper.assertTrue(!reloaded.hasTyres() && reloaded.isDrivingSuspended(),
                "Tyreless state must survive save/reload without allowing driving");
            reloaded.discard();
            var legacyTag = saved.buildResult().copy();
            legacyTag.remove("TyresFitted");
            legacyTag.putInt("TyreServicePhase", OpenwheelCarEntity.TYRE_PHASE_READY_INSTALL);
            legacyTag.putInt("RemovedTyreCompound", 1);
            legacyTag.putInt("RemovedTyreType", 0);
            legacyTag.putInt("RemovedTyreRemaining", 61);
            legacyTag.putString("PitCrewPlayer", colleague.player.getUUID().toString());
            var legacy = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level);
            legacy.readAdditionalSaveData(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), legacyTag));
            legacy.setPos(origin.add(1, 0, 0));
            int beforeMigration = colleague.player.getInventory().countItem(com.openwheelracing.registry.OWRItems.TIRES.get());
            legacy.tick(); legacy.tick();
            helper.assertTrue(!legacy.hasTyres()
                    && colleague.player.getInventory().countItem(com.openwheelracing.registry.OWRItems.TIRES.get()) == beforeMigration + 1,
                "Legacy procedural saves return the withheld old set once, without fitting free tyres");
            legacy.discard();
            crew.player.getCooldowns().removeCooldown(net.minecraft.resources.Identifier.fromNamespaceAndPath("openwheelracing", "jack"));
            car.interact(crew.player, net.minecraft.world.InteractionHand.MAIN_HAND); finishAction(car, nanos);
            var fresh = com.openwheelracing.content.item.TyreItem.create(3, 1, 92);
            crew.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, fresh);
            car.interact(crew.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(fresh.isEmpty(), "Installing reserves exactly one real set");
            finishAction(car, nanos);
            helper.assertTrue(car.hasTyres() && car.getTyreCompound() == 3 && Math.abs(car.getTyreWearPercent() - 8) < .01,
                "Installation must apply the supplied compound and remaining life");
            crew.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(com.openwheelracing.registry.OWRItems.JACK.get()));
            car.interact(crew.player, net.minecraft.world.InteractionHand.MAIN_HAND); finishAction(car, nanos);
            var handoff = driver.corrections.getLast();
            helper.assertTrue(handoff.status() == VehicleRollbackNetwork.DRIVER_CONTROL
                    && handoff.motion().counters()[13] == 3 && handoff.motion().counters()[15] == 1
                    && Math.abs(handoff.motion().state()[66] - 8) < .01,
                "Driver handoff must carry fresh tyre configuration and wear atomically");
            validateClientHandoff(helper, initialHold, handoff);

            // Interrupting installation refunds one reservation; reversing the jack cannot refund twice.
            crew.player.getCooldowns().removeCooldown(net.minecraft.resources.Identifier.fromNamespaceAndPath("openwheelracing", "jack"));
            car.interact(crew.player, net.minecraft.world.InteractionHand.MAIN_HAND); finishAction(car, nanos);
            colleague.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            car.interact(colleague.player, net.minecraft.world.InteractionHand.MAIN_HAND); finishAction(car, nanos);
            var reserved = com.openwheelracing.content.item.TyreItem.create(2, 1, 84);
            crew.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, reserved);
            int inventoryBefore = crew.player.getInventory().countItem(com.openwheelracing.registry.OWRItems.TIRES.get());
            car.interact(crew.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            crew.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(com.openwheelracing.registry.OWRItems.JACK.get()));
            car.interact(crew.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            int refunded = crew.player.getInventory().countItem(com.openwheelracing.registry.OWRItems.TIRES.get());
            helper.assertTrue(refunded == inventoryBefore && !car.hasTyres(),
                "Lowering during installation must refund exactly the reserved set without fitting it");
            car.interact(crew.player, net.minecraft.world.InteractionHand.MAIN_HAND); finishAction(car, nanos);
            helper.assertTrue(car.isJacked() && !car.hasTyres()
                    && crew.player.getInventory().countItem(com.openwheelracing.registry.OWRItems.TIRES.get()) == refunded,
                "Jack reversal changes height only and cannot repeat a tyre refund");
            crew.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                com.openwheelracing.content.item.TyreItem.create(3, 1, 92));
            car.interact(crew.player, net.minecraft.world.InteractionHand.MAIN_HAND); finishAction(car, nanos);
            crew.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(com.openwheelracing.registry.OWRItems.JACK.get()));
            car.interact(crew.player, net.minecraft.world.InteractionHand.MAIN_HAND); finishAction(car, nanos);

            // Driver switch before the next tick cannot inherit queued old-driver movement.
            var staleDriver = stationaryFrame(car, 1, origin.add(.25, 0, 0)); car.acceptClientMotionFrame(staleDriver);
            driver.player.stopRiding();
            helper.assertTrue(replacement.player.startRiding(car, false, false), "Replacement driver can take the freed seat");
            car.acceptClientMotionFrame(staleDriver); car.tick();
            helper.assertTrue(car.position().equals(origin), "Driver switch must invalidate old motion synchronously");
            var freshDriver = stationaryFrame(car, 1, origin); car.acceptClientMotionFrame(freshDriver); car.tick();
            helper.assertTrue(!replacement.rejected, "Fresh driver must inherit serviced tyres without a false violation");

            // Occupied recovery is server-owned and releases at the final carried pose.
            driver.player.setPos(origin.add(20, 0, 0)); colleague.player.setPos(origin.add(20, 0, 4));
            crew.player.setPos(origin.add(4, 0, 0)); crew.player.setYRot(90); crew.player.setXRot(-90);
            crew.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(com.openwheelracing.registry.OWRItems.CAR_DRAGGER.get()));
            car.beginRecovery(crew.player);
            helper.assertTrue(car.isRecoveryActive() && car.isRecoveryOwner(crew.player), "Marshal must acquire occupied recovery");
            var beforeRecovery = freshDriver;
            car.beginRecovery(colleague.player);
            helper.assertTrue(car.isRecoveryOwner(crew.player), "Second marshal cannot steal recovery ownership");
            for (int i = 0; i < 5; i++) { car.acceptClientMotionFrame(beforeRecovery); car.tick(); }
            helper.assertTrue(car.getY() > origin.y, "Marshal must lift car despite delayed driver frames");
            var anotherCar = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level);
            anotherCar.setPos(origin.add(0, 0, 3));
            anotherCar.beginRecovery(crew.player);
            helper.assertTrue(!anotherCar.isRecoveryActive(), "One marshal cannot acquire a second car");
            anotherCar.discard();
            var carried = replacement.corrections.getLast();
            helper.assertTrue(carried.status() == VehicleRollbackNetwork.SERVER_CONTROL
                    && carried.motion().position().equals(car.position()), "Seated driver must receive current carried pose");
            car.controlRecovery(colleague.player);
            helper.assertTrue(car.isRecoveryOwner(crew.player), "Non-owner controls cannot release the carried car");
            car.controlRecovery(crew.player);
            for (int i = 0; i < 100 && car.isRecoveryActive(); i++) car.tick();
            helper.assertTrue(!car.isRecoveryActive() && car.getDeltaMovement().equals(Vec3.ZERO),
                "Recovery must land with no throwing impulse");
            var released = replacement.corrections.getLast();
            helper.assertTrue(released.status() == VehicleRollbackNetwork.DRIVER_CONTROL
                    && released.motion().position().equals(car.position()), "Release must send final pose in a fresh driver epoch");
            Vec3 landing = car.position(); car.acceptClientMotionFrame(beforeRecovery); car.tick();
            helper.assertTrue(car.position().equals(landing), "Pre-grab frames cannot pull the released car back");
            // General repairs also survive the driver's departure and exclude grabbing/boarding.
            car.setPos(origin); car.setDamagePercent(20); car.setTyreWearPercent(12);
            replacement.player.addItem(new net.minecraft.world.item.ItemStack(com.openwheelracing.registry.OWRItems.RUBBER.get(), 2));
            var preRepair = stationaryFrame(car, 2, origin.add(.2, 0, 0)); car.acceptClientMotionFrame(preRepair);
            helper.assertTrue(car.tryStartPitStop(replacement.player), "Seated driver must start general repairs");
            car.beginRecovery(crew.player);
            helper.assertTrue(!car.isRecoveryActive(), "General repair excludes grabbing");
            replacement.player.stopRiding();
            helper.assertTrue(!crew.player.startRiding(car, false, false), "Boarding while repairs own the car is rejected");
            finishAction(car, nanos);
            helper.assertTrue(car.getDamagePercent() == 0 && car.getTyreWearPercent() == 0,
                "Repairs complete without the original driver");
            helper.assertTrue(crew.player.startRiding(car, false, false), "A new driver can board after repairs");
            car.acceptClientMotionFrame(preRepair); car.tick();
            helper.assertTrue(car.position().equals(origin), "Departed driver's queued repair-era motion stays invalid");
            helper.assertTrue(crew.corrections.getLast().status() == VehicleRollbackNetwork.DRIVER_CONTROL,
                "Replacement driver receives a fresh post-repair handoff");
            var pickup = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level);
            pickup.setPos(origin.add(6, 0, 0)); level.addFreshEntity(pickup);
            int beforePickup = driver.player.getInventory().countItem(com.openwheelracing.registry.OWRItems.PROTOTYPE_CAR_SPAWN.get())
                + colleague.player.getInventory().countItem(com.openwheelracing.registry.OWRItems.PROTOTYPE_CAR_SPAWN.get());
            for (var actor : List.of(driver, colleague)) {
                actor.player.setPos(origin.add(7, 0, 0));
                actor.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                actor.player.setShiftKeyDown(true);
                pickup.interact(actor.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            helper.assertTrue(pickup.isRemoved()
                    && driver.player.getInventory().countItem(com.openwheelracing.registry.OWRItems.PROTOTYPE_CAR_SPAWN.get())
                    + colleague.player.getInventory().countItem(com.openwheelracing.registry.OWRItems.PROTOTYPE_CAR_SPAWN.get()) == beforePickup + 1,
                "Competing pickup interactions must package an empty car only once");
            for (var actor : List.of(driver, replacement, crew, colleague))
                helper.assertTrue(!actor.rejected, "Cooperative operations must not falsely accuse technical violations");
            helper.succeed();
        } finally {
            for (var actor : List.of(driver, replacement, crew, colleague)) actor.close(helper);
            car.discard();
            for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false);
        }
    }

    private static void validateClientHandoff(GameTestHelper helper, VehicleRollbackNetwork.Update hold,
            VehicleRollbackNetwork.Update release) {
        var client = new ClientPhysicsCar(helper.getLevel());
        java.lang.reflect.Field side = null;
        try {
            side = net.minecraft.world.level.Level.class.getDeclaredField("isClientSide");
            side.setAccessible(true); side.setBoolean(helper.getLevel(), true);
            helper.assertTrue(client.applyRollbackUpdate(hold) && client.isDrivingSuspended(),
                "Client must yield to the server operation before simulating more driver frames");
            helper.assertTrue(client.applyRollbackUpdate(release) && !client.isDrivingSuspended()
                    && client.hasTyres() && client.getTyreCompound() == 3 && Math.abs(client.getTyreWearPercent() - 8) < .01,
                "Client must resume with the new tyre state, without waiting for unrelated metadata");
            helper.assertTrue(!client.applyRollbackUpdate(hold) && !client.isDrivingSuspended(),
                "A stale operation packet cannot re-suspend the released client");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to exercise client handoff", exception);
        } finally {
            if (side != null) try { side.setBoolean(helper.getLevel(), false); }
                catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
            client.discard();
        }
    }

    private static void finishAction(OpenwheelCarEntity car, AtomicLong nanos) {
        for (int i = 0; i < 100 && car.getPitStopTicks() > 0; i++) {
            nanos.addAndGet(AcceptedMotionClock.STEP_NANOS); car.tick();
        }
    }

    private static VehicleRollbackNetwork.Frame stationaryFrame(OpenwheelCarEntity car, int sequence, Vec3 position) {
        var state = car.captureRollbackMotion();
        var motion = new OpenwheelCarEntity.RollbackMotion(position, Vec3.ZERO, state.yaw(), true,
            state.tick(), state.state(), state.counters());
        return new VehicleRollbackNetwork.Frame(car.getId(), car.getRollbackEpoch(), sequence, sequence,
            position, car.getYRot(), 0, 0, 0, false, false, car.getGear(), true, 0,
            car.getUUID(), car.level().dimension().identifier().toString(), motion);
    }

    private static final class ClientPhysicsCar extends OpenwheelCarEntity {
        ClientPhysicsCar(net.minecraft.server.level.ServerLevel level) {
            super(OWREntities.PROTOTYPE_CAR.get(), level);
        }
        @Override protected boolean isLocalClientAuthoritative() { return true; }
    }

    /** Exercise the client branch and normal Entity.move, rather than the read-only server replay path. */
    private static void validateTerrainAndClientEnergy(GameTestHelper helper, Vec3 origin) {
        var level = helper.getLevel();
        Vec3 lane = origin.add(0, 0, 30);
        for (int x = -20; x <= 20; x++) for (int z = -4; z <= 4; z++) {
            level.setBlock(BlockPos.containing(lane.add(x, -1, z)),
                com.openwheelracing.registry.OWRBlocks.ASPHALT_TRACK.get().defaultBlockState(), 3);
            if (x >= 0 && x < 8) level.setBlock(BlockPos.containing(lane.add(x, 0, z)),
                com.openwheelracing.registry.OWRBlocks.ASPHALT_TRACK.get().defaultBlockState(), 3);
        }
        var probe = new ClientPhysicsCar(level);
        var validator = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level);
        probe.setPos(lane.add(-7, 0, 0)); probe.setYRot(-90); probe.setOnGround(true);
        probe.shiftLocal(1); probe.shiftLocal(1); probe.shiftLocal(1);
        probe.setDeltaMovement(new Vec3(1, 0, 0));
        java.lang.reflect.Field side = null;
        try {
            side = net.minecraft.world.level.Level.class.getDeclaredField("isClientSide");
            side.setAccessible(true);
            for (int tick = 1; tick <= 24; tick++) {
                var before = probe.captureRollbackMotion();
                validator.restoreRollbackMotion(before);
                // Only this synchronous fixture call sees the client flag; always restore it before returning.
                side.setBoolean(level, true);
                helper.assertTrue(level.isClientSide(), "Client physics branch must actually be exercised");
                probe.replayRollbackFrame(physicsFrame(probe, tick, 0, 0));
                side.setBoolean(level, false);
                Vec3 movement = probe.position().subtract(before.position());
                helper.assertTrue(probe.getDeltaMovement().y <= 1e-6,
                    "An uphill step must not become upward launch velocity: " + probe.getDeltaMovement());
                helper.assertTrue(validator.clientMotionPathClear(before.position(), movement, probe.getYRot()),
                    "Legal client terrain movement must not trigger a resync: " + movement);
            }
            helper.assertTrue(probe.getX() > lane.x + 8 && Math.abs(probe.getY() - lane.y) < .1,
                "Car must climb and descend the raised road without hovering: " + probe.position());
            validator.setPos(lane.add(7, 1, 0)); validator.setYRot(-90); validator.setOnGround(true);
            Vec3 descent = new Vec3(4.5, -1, 0);
            helper.assertTrue(Entity.collideBoundingBox(validator, descent, validator.getBoundingBox(), level, List.of())
                    .distanceToSqr(descent) > .1,
                "Downhill regression must reproduce rejection by the old straight sweep");
            helper.assertTrue(validator.clientMotionPathClear(validator.position(), descent, -90),
                "Contour-following descent must pass even when the old straight sweep rejects it");
            for (int y = 0; y <= 3; y++) for (int z = -4; z <= 4; z++)
                level.setBlock(BlockPos.containing(lane.add(12, y, z)), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(!validator.clientMotionPathClear(validator.position(), descent, -90),
                "Terrain grace must not permit crossing a real wall");
            for (int y = 0; y <= 3; y++) for (int z = -4; z <= 4; z++)
                level.removeBlock(BlockPos.containing(lane.add(12, y, z)), false);
            probe.setPos(lane.add(-10, 0, 0)); probe.setOnGround(true);
            probe.setDeltaMovement(new Vec3(4.5, 0, 0));
            probe.setErsEnergyJoules(probe.getErsCapacityJoules() * .25);
            probe.setErsMode(OpenwheelCarEntity.ERS_MODE_HARVEST);
            double initial = probe.captureRollbackMotion().state()[54];
            side.setBoolean(level, true);
            probe.replayRollbackFrame(physicsFrame(probe, 25, 0, .7f));
            side.setBoolean(level, false);
            helper.assertTrue(probe.captureRollbackMotion().state()[54] > initial && probe.getErsPowerKw() < 0,
                "Client physics must harvest energy and show charging power");
            probe.setPos(lane.add(-10, 0, 0)); probe.setOnGround(true);
            probe.setDeltaMovement(new Vec3(2, 0, 0));
            probe.setErsMode(OpenwheelCarEntity.ERS_MODE_ATTACK);
            initial = probe.captureRollbackMotion().state()[54];
            side.setBoolean(level, true);
            probe.replayRollbackFrame(physicsFrame(probe, 26, 1, 0));
            side.setBoolean(level, false);
            helper.assertTrue(probe.captureRollbackMotion().state()[54] < initial && probe.getErsPowerKw() > 0,
                "Client physics must deploy stored energy and show deployment power");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to exercise client physics", exception);
        } finally {
            if (side != null) try { side.setBoolean(level, false); }
                catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
            probe.discard(); validator.discard();
            for (int x = 0; x < 8; x++) for (int z = -4; z <= 4; z++)
                level.removeBlock(BlockPos.containing(lane.add(x, 0, z)), false);
        }
    }

    private static VehicleRollbackNetwork.Frame physicsFrame(OpenwheelCarEntity car, int tick, float throttle, float brake) {
        return new VehicleRollbackNetwork.Frame(car.getId(), 0, tick, tick, car.position(), car.getYRot(),
            throttle, brake, 0, true, false, car.getGear(), true, 0, car.getUUID(),
            car.level().dimension().identifier().toString(), car.captureRollbackMotion());
    }

    private static Vec3 location(Vec3 origin, int step) {
        double distance = (step * 5.0 + .37) % 200;
        if (distance < 60) return origin.add(-30 + distance, 0, 0);
        if (distance < 100) return origin.add(30, 0, distance - 60);
        if (distance < 160) return origin.add(130 - distance, 0, 40);
        return origin.add(-30, 0, 200 - distance);
    }
    private static TrackDefinition.Point3 point(Vec3 origin, double x, double z) {
        return new TrackDefinition.Point3(origin.x + x, origin.y - 1, origin.z + z);
    }
}
