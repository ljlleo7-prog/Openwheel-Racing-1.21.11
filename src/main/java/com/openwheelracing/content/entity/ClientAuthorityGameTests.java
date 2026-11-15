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
    static { FUNCTIONS.register("client_authority", () -> ClientAuthorityGameTests::test); }
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
