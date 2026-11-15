package com.openwheelracing.content.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.race.OWRLapRecords;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackDefinitionsData;
import com.openwheelracing.registry.OWREntities;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Real vanilla inbound packet-handler coverage, with outbound traffic replaced by a sink. */
public final class LapNetworkGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, OpenwheelRacing.MODID);
    static { FUNCTIONS.register("lap_network_capture", () -> LapNetworkGameTests::test); }
    private LapNetworkGameTests() {}
    public static void register(IEventBus bus) { FUNCTIONS.register(bus); }

    private static void test(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absoluteVec(new Vec3(6, 8, 6));
        var controls = OWRRaceControlState.get(level);
        controls.setMinimumValidLapTicks(1);
        if (controls.isCheckpointCheckEnabled()) controls.toggleCheckpointCheck();
        if (controls.isOffTrackCheckEnabled()) controls.toggleOffTrackCheck();
        String dimension = level.dimension().identifier().toString();
        var track = TrackDefinition.empty(UUID.randomUUID(), "Network capture", dimension).withStartFinish(new TrackDefinition.StartFinishLine(
            new TrackDefinition.Point3(origin.x, origin.y - 1, origin.z - 10),
            new TrackDefinition.Point3(origin.x, origin.y - 1, origin.z + 10), 0));
        TrackDefinitionsData.get(level).upsert(track);
        TrackDefinitionsData.get(level).setActiveTrack(track.trackId(), dimension);
        validateAccelerationPacketTiming(helper, origin.add(0, 12, 60));
        List<Fixture> fixtures = new ArrayList<>();
        // Delay controls when the same position update arrives relative to the server physics tick.
        fixtures.add(new Fixture(helper, origin, false, 0));
        for (int delay : new int[] {0, 1, 2, 4, 10, 20}) {
            fixtures.add(new Fixture(helper, origin, true, delay));
            fixtures.add(new Fixture(helper, origin, false, delay, true));
        }
        for (String safety : List.of("rejected_fast", "backward", "not_loaded", "collision_rejected", "duplicate", "late_duplicate", "far_away", "fast_sweep", "vertical_only")) {
            Vec3 at = safety.equals("collision_rejected") ? origin.add(0, 0, 8) : safety.equals("far_away") ? origin.add(0, 0, 100) : origin;
            fixtures.add(new Fixture(helper, at, true, 0, false, safety));
        }
        for (Fixture fixture : fixtures) fixture.start();
        helper.runAfterDelay(30, () -> {
            for (Fixture fixture : fixtures) fixture.finish();
            helper.runAfterDelay(25, () -> {
                List<String> report = new ArrayList<>();
                report.add("scenario,delay_ticks,server_x_before,server_x_after_packet,valid_laps,all_records");
                var records = OWRLapRecords.get(level).getRecentLaps(0, Integer.MAX_VALUE);
                int missed = 0;
                for (Fixture fixture : fixtures) {
                    var laps = records.stream().filter(lap -> lap.driverId().equals(fixture.player.getUUID())).toList();
                    long valid = laps.stream().filter(lap -> !lap.invalidated()).count();
                    report.add((!fixture.safety.isEmpty() ? fixture.safety : fixture.packet ? "vanilla_position_packet" : fixture.driveInputs ? "delayed_drive_inputs" : "physics_only") + "," + fixture.delay + "," + fixture.beforePacketX + "," + (fixture.packet ? Double.toString(fixture.afterPacketX) : "") + "," + valid + "," + laps.size());
                    helper.assertTrue(fixture.player.isPassenger() && fixture.player.isAlive(), "Network fixture must retain its living driver");
                    if (!fixture.safety.isEmpty()) {
                        long expected = fixture.safety.equals("far_away") ? 0 : 1;
                        helper.assertTrue(valid == expected, "Packet safety regression " + fixture.safety + ": expected " + expected + " got " + valid);
                        if (List.of("rejected_fast", "not_loaded", "collision_rejected").contains(fixture.safety)) helper.assertTrue(fixture.afterPacketX == -2, "Rejected/ignored packet must not move or fabricate a crossing");
                        else helper.assertTrue(fixture.afterPacketX == (fixture.safety.equals("backward") ? -5 : fixture.safety.equals("fast_sweep") ? 5 : fixture.safety.equals("vertical_only") ? -2 : 2), "Accepted safety packet must retain its final position");
                    } else if (!fixture.packet) helper.assertTrue(valid == 1, "Server-physics-only control must record the completed lap");
                    else {
                        helper.assertTrue(fixture.afterPacketX >= 2, "Vanilla handler must actually accept and apply the crossing position packet: delay=" + fixture.delay + " x=" + fixture.afterPacketX);
                        if (valid != 1) missed++;
                    }
                    fixture.player.stopRiding();
                    level.removePlayerImmediately(fixture.player, Entity.RemovalReason.DISCARDED);
                    fixture.car.discard();
                }
                TrackDefinitionsData.get(level).remove(track.trackId(), dimension);
                try { Files.write(Path.of("lap-network-summary.csv"), report); }
                catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
                LogUtils.getLogger().warn("NETWORK CAPTURE packet-crossing losses={}/6; physics-only and delayed-drive-input controls passed", missed);
                helper.assertTrue(!Boolean.getBoolean("openwheelracing.lapCaptureStrict") || missed == 0,
                    "Vanilla vehicle position packets bypassed lap capture in " + missed + "/6 scenarios");
                helper.succeed();
            });
        });
    }

    /** Real listener bookkeeping ticks, with delayed/batched client targets on an empty straight. */
    private static void validateAccelerationPacketTiming(GameTestHelper helper, Vec3 origin) {
        List<String> report = new ArrayList<>();
        report.add("server_moves_first,delay_ticks,tick,client_target_x,server_before_x,server_after_x,corrections");
        for (boolean serverMovesFirst : new boolean[] {false, true}) {
        for (int delay : new int[] {0, 1, 2, 4, 10}) {
            ReplayCar car = new ReplayCar(helper);
            car.setPos(origin);
            car.setYRot(-90);
            ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "timing-probe"), ClientInformation.createDefault());
            final int[] corrections = {0};
            ServerGamePacketListenerImpl listener = new ServerGamePacketListenerImpl(
                helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) {
                    if (packet instanceof net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket) corrections[0]++;
                }
            };
            listener.markClientLoaded();
            player.startRiding(car, true, false);
            helper.getLevel().addNewPlayer(player);
            try {
                Field teleportId = ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");
                teleportId.setAccessible(true);
                listener.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(teleportId.getInt(listener)));
            } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
            List<Vec3> pending = new ArrayList<>();
            double clientX = 0.0;
            for (int tick = 0; tick < 80; tick++) {
                listener.tick();
                // Isolate authority/timing from tyre tuning: both peers integrate the same acceleration.
                double speed = Math.min(4.5, 0.1 + tick * 0.06);
                car.setDeltaMovement(speed, 0, 0);
                if (serverMovesFirst) car.setPos(car.position().add(speed, 0, 0));
                clientX += speed;
                pending.add(origin.add(clientX, 0, 0));
                if (tick % (delay + 1) != delay) continue;
                for (Vec3 target : pending) {
                    double before = car.getX();
                    listener.handleMoveVehicle(new ServerboundMoveVehiclePacket(target, -90, 0, true));
                    report.add(serverMovesFirst + "," + delay + "," + tick + "," + (target.x - origin.x) + ","
                        + (before - origin.x) + "," + (car.getX() - origin.x) + "," + corrections[0]);
                    helper.assertTrue(Double.isFinite(car.getX()), "Packet timing must retain finite position");
                }
                pending.clear();
            }
            player.stopRiding();
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            car.discard();
        }
        }
        try { Files.write(Path.of("acceleration-packet-timing.csv"), report); }
        catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final Vec3 origin;
        private final boolean packet;
        private final int delay;
        private final boolean driveInputs;
        private final String safety;
        private final ReplayCar car;
        private final ServerPlayer player;
        private final ServerGamePacketListenerImpl listener;
        private double afterPacketX;
        private double beforePacketX = -2;

        Fixture(GameTestHelper helper, Vec3 origin, boolean packet, int delay) {
            this(helper, origin, packet, delay, false);
        }

        Fixture(GameTestHelper helper, Vec3 origin, boolean packet, int delay, boolean driveInputs) {
            this(helper, origin, packet, delay, driveInputs, "");
        }
        Fixture(GameTestHelper helper, Vec3 origin, boolean packet, int delay, boolean driveInputs, String safety) {
            this.helper = helper; this.origin = origin; this.packet = packet; this.delay = delay; this.driveInputs = driveInputs; this.safety = safety;
            car = new ReplayCar(helper);
            car.setPos(origin.add(-1, 0, 0));
            car.setYRot(-90);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "net-probe"), ClientInformation.createDefault());
            player.setInvulnerable(true);
            listener = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> outgoing) { }
            };
            listener.markClientLoaded();
            player.startRiding(car, true, false);
            helper.assertTrue(car.hasPassenger(player), "Real ServerPlayer must control the packet test car");
            try {
                Field teleportId = ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");
                teleportId.setAccessible(true);
                listener.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(teleportId.getInt(listener)));
            } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
            helper.getLevel().addNewPlayer(player);
        }

        void step(double x, double z) {
            Vec3 before = car.position();
            car.setPos(origin.add(x, 0, z));
            Vec3 delta = car.position().subtract(before);
            car.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
            car.setDeltaMovement(delta);
            car.processRaceTimingMovement(before, delta);
        }

        void start() {
            step(1, 0); // Actual forward trigger arms the lap.
            helper.runAfterDelay(5, () -> step(20, 0));
            helper.runAfterDelay(10, () -> step(20, 30));
            helper.runAfterDelay(15, () -> step(-20, 30));
            helper.runAfterDelay(20, () -> step(-20, 0));
            helper.runAfterDelay(25, () -> step(-2, 0));
        }

        void finish() {
            helper.runAfterDelay(delay, () -> {
                car.setYRot(-90);
                if (driveInputs) {
                    Vec3 beforeInput = car.position();
                    helper.assertTrue(car.applyDriveInput(100, 0, 0, 0, false), "First input sequence must be accepted");
                    helper.assertTrue(!car.applyDriveInput(99, 0, 0, 0, false), "Stale input must be rejected");
                    helper.assertTrue(car.applyDriveInput(110, 0, 0, 0, false), "Sequence gap must be accepted for reconciliation");
                    helper.assertTrue(car.position().equals(beforeInput), "Control input must not teleport the car");
                }
                if (packet) {
                    if (safety.equals("fast_sweep")) step(-5, 0);
                    beforePacketX = car.getX() - origin.x;
                    if (safety.equals("not_loaded")) listener.markClientUnloadedAfterDeath();
                    if (safety.equals("collision_rejected")) {
                        for (int y = 0; y <= 3; y++) for (int z = -1; z <= 1; z++) helper.getLevel().setBlock(BlockPos.containing(origin.x, origin.y + y, origin.z + z), Blocks.STONE.defaultBlockState(), 3);
                    }
                    // These fields are initialized by the listener's ordinary tick. Set only
                    // fixture bookkeeping; execute the real, unmodified packet handler below.
                    setListenerField("lastVehicle", car);
                    for (String axis : new String[] {"X", "Y", "Z"}) {
                        double value = axis.equals("X") ? car.getX() : axis.equals("Y") ? car.getY() : car.getZ();
                        setListenerField("vehicleFirstGood" + axis, value);
                        setListenerField("vehicleLastGood" + axis, value);
                    }
                    car.setDeltaMovement(new Vec3(safety.equals("fast_sweep") ? 5 : 4, 0, 0));
                    double target = safety.equals("rejected_fast") ? 1000 : safety.equals("backward") ? -5 : safety.equals("fast_sweep") ? 5 : safety.equals("vertical_only") ? -2 : 2;
                    listener.handleMoveVehicle(new ServerboundMoveVehiclePacket(origin.add(target, safety.equals("vertical_only") ? .5 : 0, 0), -90, 0, true));
                    afterPacketX = car.getX() - origin.x;
                    if (safety.equals("collision_rejected")) {
                        for (int y = 0; y <= 3; y++) for (int z = -1; z <= 1; z++) helper.getLevel().removeBlock(BlockPos.containing(origin.x, origin.y + y, origin.z + z), false);
                    }
                    if (safety.equals("duplicate") || safety.equals("late_duplicate")) {
                        Runnable repeat = () -> {
                            car.setDeltaMovement(new Vec3(4, 0, 0));
                            listener.handleMoveVehicle(new ServerboundMoveVehiclePacket(origin.add(-2, 0, 0), -90, 0, true));
                            listener.handleMoveVehicle(new ServerboundMoveVehiclePacket(origin.add(2, 0, 0), -90, 0, true));
                        };
                        if (safety.equals("duplicate")) repeat.run(); else helper.runAfterDelay(1, repeat);
                    }
                }
                // This is the next ordinary movement segment the mod sees. If the packet
                // already crossed the line, both ends are on its departure side.
                step(safety.equals("fast_sweep") ? 6 : 3, 0);
            });
        }

        private void setListenerField(String name, Object value) {
            try {
                Field field = ServerGamePacketListenerImpl.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(listener, value);
            } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        }
    }

    private static final class ReplayCar extends OpenwheelCarEntity {
        ReplayCar(GameTestHelper helper) { super(OWREntities.PROTOTYPE_CAR.get(), helper.getLevel()); }
        @Override public void tick() { }
    }
}
