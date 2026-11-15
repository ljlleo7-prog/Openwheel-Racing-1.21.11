package com.openwheelracing.content.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.network.VehicleRollbackNetwork;
import com.openwheelracing.registry.OWRBlocks;
import com.openwheelracing.registry.OWREntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Real solver, block shapes, server ticks, payload codecs and vanilla movement mixin. */
public final class BlockRollbackGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, OpenwheelRacing.MODID);
    static { FUNCTIONS.register("block_rollback", () -> BlockRollbackGameTests::test); }
    private BlockRollbackGameTests() {}
    public static void register(IEventBus bus) { FUNCTIONS.register(bus); }

    private static void test(GameTestHelper helper) {
        validateReplayStateCopies(helper);
        List<Probe> probes = new ArrayList<>();
        Vec3 origin = helper.absoluteVec(new Vec3(8, 3, 8));
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = ((int) origin.x - 16) >> 4; x <= ((int) origin.x + 256) >> 4; x++) {
            for (int z = ((int) origin.z - 16) >> 4; z <= ((int) origin.z + 140) >> 4; z++) {
                helper.getLevel().setChunkForced(x, z, true);
                chunks.add(new ChunkPos(x, z));
            }
        }
        probes.add(new Probe(helper, origin, "clear_replay", true, false, false));
        probes.add(new Probe(helper, origin.add(16, 0, 0), "future_collision", true, true, false));
        probes.add(new Probe(helper, origin.add(32, 0, 0), "disabled", false, false, false));
        probes.add(new Probe(helper, origin.add(48, 0, 0), "missing_evidence", true, false, true));
        probes.add(new Probe(helper, origin.add(64, 0, 0), "repeat_same_wall", true, false, false));
        probes.add(new Probe(helper, origin.add(80, 0, 0), "forged_clear_collision", true, true, false));
        probes.add(new Probe(helper, origin.add(96, 0, 0), "unrelated_damage", true, false, false));
        probes.add(new Probe(helper, origin.add(112, 0, 0), "changed_geometry", true, false, false));
        probes.add(new Probe(helper, origin.add(128, 0, 0), "future_first_tick", true, true, false));
        probes.add(new Probe(helper, origin.add(144, 0, 0), "future_last_tick", true, true, false));
        probes.add(new Probe(helper, origin.add(160, 0, 0), "recovery_during_check", true, false, false));
        probes.add(new Probe(helper, origin.add(176, 0, 0), "delayed_gear_report", true, false, false));
        probes.add(new Probe(helper, origin.add(192, 0, 0), "rapid_steering_stalls", true, false, false));
        probes.add(new Probe(helper, origin.add(224, 0, 0), "rapid_steering_control", true, false, false));
        helper.onEachTick(() -> {
            int tick = (int) helper.getTick();
            if (tick >= 1 && tick <= 75) for (Probe probe : probes) probe.step(tick);
            if (tick == 76) {
                for (Probe probe : probes) probe.check();
                var control = probes.stream().filter(probe -> probe.name.equals("rapid_steering_control")).findFirst().orElseThrow();
                var stalls = probes.stream().filter(probe -> probe.name.equals("rapid_steering_stalls")).findFirst().orElseThrow();
                helper.assertTrue(stalls.maxMotionDisagreement > control.maxMotionDisagreement + 0.1,
                    "Timing stalls must produce more disagreement than identical steering delivered normally");
                helper.assertTrue(stalls.catchupDisagreement < 0.05,
                    "Late inputs must repair server velocity after delivery resumes; got " + stalls.catchupDisagreement);
                for (Probe probe : probes) probe.close();
                for (ChunkPos chunk : chunks) helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
                helper.succeed();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void validateReplayStateCopies(GameTestHelper helper) {
        OpenwheelCarEntity live = new SafetyCarEntity(OWREntities.SAFETY_CAR.get(), helper.getLevel());
        live.setAbsEnabled(false);
        live.setTractionControlEnabled(false);
        live.setDamagePercent(5);
        var damage = live.getComponentDamage();
        var checkpoint = live.captureRollbackCheckpoint();
        var ghost = live.replayVehicle(checkpoint);
        helper.assertTrue(ghost.isSafetyCar(), "Replay must preserve the actual vehicle profile");
        helper.assertTrue(!ghost.isAbsEnabled() && !ghost.isTractionControlEnabled(),
            "Constructor defaults must not override checkpoint assist settings");
        ghost.setDamagePercent(99);
        helper.assertTrue(live.getComponentDamage().equals(damage), "Replay data must not alias live damage");
        helper.assertTrue(live.position().equals(checkpoint.motion().position()), "Replay must not move the live vehicle");
        try {
            String[] fields = {"TYRE_TEMPERATURE_FL", "TYRE_TEMPERATURE_FR", "TYRE_TEMPERATURE_RL",
                "TYRE_TEMPERATURE_RR", "TYRE_CARCASS_TEMPERATURE_FL", "TYRE_CARCASS_TEMPERATURE_FR",
                "TYRE_CARCASS_TEMPERATURE_RL", "TYRE_CARCASS_TEMPERATURE_RR", "TYRE_TEMPERATURE"};
            for (int i = 0; i < fields.length; i++) {
                var accessor = OpenwheelCarEntity.class.getDeclaredField(fields[i]);
                accessor.setAccessible(true);
                live.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<Float>) accessor.get(null),
                    i == 8 ? 65f : 50f + i * 10f);
            }
            live.applySyncedTyreThermalState();
            var thermal = live.captureRollbackMotion().state();
            for (int i = 0; i < 8; i++) helper.assertTrue(thermal[25 + i] == 50 + i * 10,
                "Physics must use per-wheel surface/carcass temperatures rather than HUD average or stale defaults");
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        helper.assertTrue(live.rollbackTimingCompatible(checkpoint), "Unchanged timing state should be compatible");
        try {
            var field = OpenwheelCarEntity.class.getDeclaredField("visitedCheckpoints");
            field.setAccessible(true);
            ((java.util.List<Long>) field.get(live)).add(123L);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        helper.assertTrue(!live.rollbackTimingCompatible(checkpoint), "Rewind cannot undo a checkpoint crossing");
        live.discard(); ghost.discard();
    }

    private static final class Probe {
        final GameTestHelper helper;
        final Vec3 origin;
        final String name;
        final boolean enabled, future, missing;
        final OpenwheelCarEntity car, client;
        final ServerPlayer player;
        final ServerGamePacketListenerImpl listener;
        final List<VehicleRollbackNetwork.Update> updates = new ArrayList<>();
        final List<VehicleRollbackNetwork.Frame> delayed = new ArrayList<>();
        int frame;
        int epoch;
        boolean resolved;
        int rewinds;
        int checking;
        int ordinaryCorrections;
        int commits;
        double speedAtCommit;
        double unrelatedDamage;
        int firstClientCollision;
        int syncEpoch = -1;
        double maxMotionDisagreement;
        double catchupDisagreement;
        ServerPlayer marshal;

        Probe(GameTestHelper helper, Vec3 origin, String name, boolean enabled, boolean future, boolean missing) {
            this.helper = helper; this.origin = origin; this.name = name;
            this.enabled = enabled; this.future = future; this.missing = missing;
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 110; z++) {
                helper.getLevel().setBlock(BlockPos.containing(origin.x + x, origin.y - 1, origin.z + z),
                    Blocks.STONE.defaultBlockState(), 3);
                for (int y = 0; y < 5; y++) helper.getLevel().removeBlock(
                    BlockPos.containing(origin.x + x, origin.y + y, origin.z + z), false);
            }
            for (int z = -4; z <= 105 && !name.startsWith("rapid_steering_"); z++) for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(BlockPos.containing(origin.x + 3, origin.y + y, origin.z + z),
                    OWRBlocks.BARRIER.get().defaultBlockState(), 3);
            }
            // This front wall is reached AFTER the disputed side-wall hit.
            if (future) for (int x = -3; x < 3; x++) for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(BlockPos.containing(origin.x + x, origin.y + y, origin.z
                        + (name.equals("future_first_tick") ? 31 : name.equals("future_last_tick") ? 44 : 36)),
                    Blocks.STONE.defaultBlockState(), 3);
            }
            car = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), helper.getLevel());
            car.setPos(origin); car.setYRot(0); car.setOnGround(true);
            car.setDeltaMovement(0, 0, 1.5);
            if (name.equals("clear_replay")) {
                helper.assertTrue(car.correctionVisualPathClear(new Vec3(0, 0, -0.5)),
                    "A clear visual correction should be blendable");
                helper.assertTrue(!car.correctionVisualPathClear(new Vec3(4, 0, 0)),
                    "A visual blend must not pass through a barrier");
            }
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "rollback-" + name), ClientInformation.createDefault());
            player.setInvulnerable(true);
            listener = new ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) {
                    if (packet instanceof ClientboundCustomPayloadPacket payload
                            && payload.payload() instanceof VehicleRollbackNetwork.Update update) updates.add(wire(update));
                }
            };
            listener.markClientLoaded();
            player.startRiding(car, true, false);
            helper.getLevel().addNewPlayer(player);
            client = car.replayVehicle(car.captureRollbackCheckpoint());
        }

        VehicleRollbackNetwork.Update wire(VehicleRollbackNetwork.Update update) {
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                helper.getLevel().registryAccess());
            try {
                VehicleRollbackNetwork.Update.STREAM_CODEC.encode(buffer, update);
                var decoded = VehicleRollbackNetwork.Update.STREAM_CODEC.decode(buffer);
                helper.assertTrue(decoded.epoch() == update.epoch() && decoded.frame() == update.frame()
                    && decoded.status() == update.status(), "Restore protocol lost epoch/frame/status");
                if (update.motion() != null) {
                    helper.assertTrue(java.util.Arrays.equals(decoded.motion().state(), update.motion().state())
                        && java.util.Arrays.equals(decoded.motion().counters(), update.motion().counters()),
                        "Restore protocol lost physics state");
                }
                return decoded;
            } finally { buffer.release(); }
        }

        VehicleRollbackNetwork.Frame wire(VehicleRollbackNetwork.Frame frame) {
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                helper.getLevel().registryAccess());
            try {
                VehicleRollbackNetwork.Frame.STREAM_CODEC.encode(buffer, frame);
                var decoded = VehicleRollbackNetwork.Frame.STREAM_CODEC.decode(buffer);
                helper.assertTrue(frame.sequence() == decoded.sequence() && frame.epoch() == decoded.epoch()
                    && frame.position().equals(decoded.position()) && java.util.Arrays.equals(frame.motion().state(),
                        decoded.motion().state()), "Vehicle frame protocol lost evidence");
                return decoded;
            } finally { buffer.release(); }
        }

        VehicleRollbackNetwork.Frame report(boolean collision) {
            return new VehicleRollbackNetwork.Frame(car.getId(), epoch, frame, frame, client.position(),
                client.getYRot(), 0, 0, name.startsWith("rapid_steering_") ? (frame / 4 % 2 == 0 ? 0.12f : -0.12f) : 0,
                false, collision, client.getGear(), enabled, 0,
                car.getUUID(), car.level().dimension().identifier().toString(), client.captureRollbackMotion());
        }

        void step(int tick) {
            if (resolved) return;
            listener.tick();
            frame++;
            // Generate a counterfactual client continuation with the real force/collision solver.
            boolean clear = client.replayRollbackFrame(report(false));
            if (!clear && firstClientCollision == 0) firstClientCollision = tick;
            var packet = wire(report(!clear && !name.equals("forged_clear_collision")));
            if (name.startsWith("rapid_steering_")) {
                delayed.add(packet);
                boolean stalled = name.equals("rapid_steering_stalls")
                    && ((tick >= 12 && tick <= 15) || (tick >= 32 && tick <= 34));
                if (!stalled) {
                    for (var queued : delayed) car.acceptRollbackFrame(queued);
                    delayed.clear();
                }
            } else if (!missing || tick < 20) {
                // Two-frame bursts after impact exercise late but ordered evidence.
                if (tick >= 20) {
                    delayed.add(packet);
                    if (tick % 2 == 0) {
                        for (var queued : delayed) car.acceptRollbackFrame(queued);
                        delayed.clear();
                    }
                } else car.acceptRollbackFrame(packet);
            }
            if (name.equals("delayed_gear_report") && tick == 10) car.shiftUp();
            if ((tick == 20 && !name.startsWith("rapid_steering_"))
                    || (name.equals("repeat_same_wall") && tick == 45)) {
                // Server-only stale heading/velocity: client remains on the clear center line.
                car.setYRot(-90);
                car.setDeltaMovement(1.5, 0, 0);
            }
            if (name.equals("recovery_during_check") && tick == 25) {
                car.setDeltaMovement(Vec3.ZERO);
                marshal = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "rollback-marshal"), ClientInformation.createDefault());
                new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND),
                    marshal, CommonListenerCookie.createInitial(marshal.getGameProfile(), false)) {
                    @Override public void send(Packet<?> packet) {}
                };
                marshal.setInvulnerable(true);
                marshal.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(com.openwheelracing.registry.OWRItems.CAR_DRAGGER.get()));
                marshal.setPos(car.position().add(0, 0, -2));
                helper.getLevel().addNewPlayer(marshal);
                car.beginRecovery(marshal);
            }
            if (name.equals("changed_geometry") && tick == 25) {
                for (int z = 20; z <= 35; z++) for (int y = 0; y < 3; y++) {
                    helper.getLevel().removeBlock(BlockPos.containing(origin.x + 3, origin.y + y, origin.z + z), false);
                }
            }
            if (name.equals("unrelated_damage") && tick == 25) {
                car.setDamagePercent(10);
                unrelatedDamage = car.getDamagePercent();
            }
            car.tick();
            double error = car.getDeltaMovement().subtract(client.getDeltaMovement()).horizontalDistance() * 20.0;
            maxMotionDisagreement = Math.max(maxMotionDisagreement, error);
            if (name.equals("rapid_steering_stalls") && (tick == 16 || tick == 35)) {
                catchupDisagreement = Math.max(catchupDisagreement, error);
            }

            // A late vanilla position packet cannot override framed server movement.
            Vec3 before = car.position();
            listener.handleMoveVehicle(new ServerboundMoveVehiclePacket(origin.add(80, 0, 0), -90, 0, true));
            helper.assertTrue(car.position().equals(before), name + ": stale vanilla packet bypassed frame authority");

            for (var update : List.copyOf(updates)) {
                if (update.status() == VehicleRollbackNetwork.RECONCILE) ordinaryCorrections++;
                if (name.equals("rapid_steering_stalls") && update.status() == VehicleRollbackNetwork.RECONCILE) {
                    helper.assertTrue(!((tick >= 12 && tick <= 15) || (tick >= 32 && tick <= 34)),
                        "Unconfirmed predicted motion must not be sent as an authoritative correction");
                }
                if (update.status() == VehicleRollbackNetwork.SYNC) syncEpoch = update.epoch();
                if (update.status() == VehicleRollbackNetwork.CHECKING) checking++;
                else if (update.status() == VehicleRollbackNetwork.REWOUND) {
                    rewinds++;
                    helper.assertTrue(car.getDamagePercent() == unrelatedDamage,
                        name + ": rewind changed unrelated damage or applied forgiven impact damage");
                    helper.assertTrue(update.epoch() > epoch, "Successful rewind must advance motion epoch");
                    Vec3 restored = car.position();
                    car.acceptRollbackFrame(packet); // Pre-rewind epoch must be rejected.
                    helper.assertTrue(car.position().equals(restored), "Old evidence moved restored vehicle");
                    epoch = update.epoch();
                    resolved = !name.equals("repeat_same_wall");
                    if (!resolved) {
                        client.restoreRollbackMotion(update.motion());
                        frame = update.frame();
                    }
                } else if (update.status() == VehicleRollbackNetwork.CONFIRMED
                        || update.status() == VehicleRollbackNetwork.UNVERIFIED) {
                    commits++;
                    speedAtCommit = car.getDeltaMovement().horizontalDistance() * 72.0;
                    resolved = true;
                }
            }
            updates.clear();
            if (name.equals("delayed_gear_report") && tick == 11) {
                helper.assertTrue(car.getGear() == 1, "Stale neutral-gear reports must not undo a shift command");
                resolved = true;
            }
            if (!enabled && tick >= 21) resolved = true;
            if (name.equals("repeat_same_wall") && tick >= 46) resolved = true;
        }

        void check() {
            LogUtils.getLogger().info("ROLLBACK TEST {} checking={} rewinds={} commits={} damage={} speed={}",
                name, checking, rewinds, commits, car.getDamagePercent(), speedAtCommit);
            if (name.startsWith("rapid_steering_")) {
                helper.assertTrue(ordinaryCorrections == 0,
                    "Normal steering and repaired stalls must not force client motion corrections");
                LogUtils.getLogger().info("STEERING PROBE {} max velocity disagreement={} m/s, catchup error={} m/s, {}",
                    name, maxMotionDisagreement, catchupDisagreement, car.rollbackDiagnostics());
                helper.assertTrue(Double.isFinite(maxMotionDisagreement),
                    "Steering fixture must retain finite motion");
            } else if (name.equals("delayed_gear_report")) {
                helper.assertTrue(car.getGear() == 1 && checking == 0 && rewinds == 0,
                    "Live gear changes must remain authoritative despite old frame reports");
            } else if (!enabled) {
                helper.assertTrue(checking == 0 && rewinds == 0, "Disabled rewind must never defer an impact");
                helper.assertTrue(car.getDamagePercent() > 0, "Disabled rewind must retain normal collision damage");
            } else if (name.equals("repeat_same_wall")) {
                helper.assertTrue(checking == 1 && rewinds == 1 && commits == 0 && car.getDamagePercent() > 0,
                    "Repeated contact must receive normal damage, not enter another rewind cycle");
            } else if (future || missing || name.equals("changed_geometry") || name.equals("recovery_during_check")) {
                helper.assertTrue(checking == 1 && commits == 1 && rewinds == 0,
                    name + ": must commit once, not loop or forgive");
                helper.assertTrue(car.getDamagePercent() > 0, name + ": true/unverifiable impact must retain damage");
                if (name.equals("recovery_during_check")) helper.assertTrue(car.isRecoveryActive()
                    && syncEpoch == car.getRollbackEpoch(), "Recovery must cancel pending impact and notify the new epoch");
                if (name.equals("future_first_tick")) helper.assertTrue(firstClientCollision == 21,
                    "First-tick horizon scenario must actually collide at impact+1; got " + firstClientCollision);
                if (name.equals("future_last_tick")) helper.assertTrue(firstClientCollision == 30,
                    "Last-tick horizon scenario must actually collide at impact+10; got " + firstClientCollision);
            } else {
                helper.assertTrue(checking == 1 && rewinds == 1 && commits == 0,
                    name + ": expected exactly one successful replay-validated rewind");
            }
        }

        void close() {
            player.stopRiding();
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            if (marshal != null) helper.getLevel().removePlayerImmediately(marshal, Entity.RemovalReason.DISCARDED);
            car.discard(); client.discard();
        }
    }
}
