package com.openwheelracing.network;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Simulation frames are distinct from the legacy, idle-suppressed input sequence. */
public final class VehicleRollbackNetwork {
    public static final int CHECKING = 0, REWOUND = 1, CONFIRMED = 2, UNVERIFIED = 3, SYNC = 4, RECONCILE = 5, BLOCKED = 6;
    private VehicleRollbackNetwork() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Frame.TYPE, Frame.STREAM_CODEC, Frame::handle);
        registrar.playToClient(Update.TYPE, Update.STREAM_CODEC, Update::handle);
    }

    public static void send(ServerPlayer driver, Update update) { PacketDistributor.sendToPlayer(driver, update); }

    public record Frame(int entityId, int epoch, int sequence, int inputSequence, Vec3 position,
                        float yaw, float throttle, float brake, float steering, boolean keyboard,
                        boolean collision, int gear, boolean enabled, int correctionSerial,
                        java.util.UUID vehicleUuid, String dimension, OpenwheelCarEntity.RollbackMotion motion) implements CustomPacketPayload {
        public static final Type<Frame> TYPE = new Type<>(Identifier.fromNamespaceAndPath("openwheelracing", "vehicle_frame"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Frame> STREAM_CODEC = StreamCodec.of(Frame::write, Frame::read);
        @Override public Type<Frame> type() { return TYPE; }
        private static void write(RegistryFriendlyByteBuf b, Frame f) {
            b.writeVarInt(f.entityId); b.writeVarInt(f.epoch); b.writeInt(f.sequence); b.writeInt(f.inputSequence);
            writeVector(b, f.position); b.writeFloat(f.yaw); b.writeFloat(f.throttle); b.writeFloat(f.brake);
            b.writeFloat(f.steering); b.writeBoolean(f.keyboard); b.writeBoolean(f.collision);
            b.writeVarInt(f.gear); b.writeBoolean(f.enabled); b.writeInt(f.correctionSerial);
            b.writeUUID(f.vehicleUuid); b.writeUtf(f.dimension, 256);
            writeMotion(b, f.motion);
        }
        private static Frame read(RegistryFriendlyByteBuf b) {
            return new Frame(b.readVarInt(), b.readVarInt(), b.readInt(), b.readInt(), readVector(b),
                b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean(), b.readBoolean(),
                b.readVarInt(), b.readBoolean(), b.readInt(), b.readUUID(), b.readUtf(256), readMotion(b));
        }
        private static void handle(Frame f, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof OpenwheelCarEntity car
                        && car.getId() == f.entityId && car.getControllingPassenger() == player
                        && car.getUUID().equals(f.vehicleUuid)
                        && car.level().dimension().identifier().toString().equals(f.dimension)
                        && TechnicalRulesNetwork.verified(player.getUUID())) car.acceptClientMotionFrame(f);
            });
        }
        public boolean finite() {
            return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z)
                && motion != null && Double.isFinite(motion.position().x) && Double.isFinite(motion.position().y)
                && Double.isFinite(motion.position().z) && Double.isFinite(motion.velocity().x) && Double.isFinite(motion.velocity().y)
                && Double.isFinite(motion.velocity().z) && Float.isFinite(motion.yaw())
                && Float.isFinite(yaw) && Float.isFinite(throttle) && Float.isFinite(brake) && Float.isFinite(steering)
                && throttle >= 0 && throttle <= 1 && brake >= 0 && brake <= 1 && Math.abs(steering) <= 1.1f;
        }
    }

    public record Update(int entityId, long incidentId, int epoch, int frame, int status, int rewindTicks,
                         OpenwheelCarEntity.RollbackMotion motion, java.util.UUID vehicleUuid, String dimension) implements CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(Identifier.fromNamespaceAndPath("openwheelracing", "vehicle_rewind"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> STREAM_CODEC = StreamCodec.of(Update::write, Update::read);
        @Override public Type<Update> type() { return TYPE; }
        private static void write(RegistryFriendlyByteBuf b, Update u) {
            b.writeVarInt(u.entityId); b.writeLong(u.incidentId); b.writeInt(u.epoch); b.writeInt(u.frame);
            b.writeVarInt(u.status); b.writeVarInt(u.rewindTicks);
            b.writeUUID(u.vehicleUuid); b.writeUtf(u.dimension, 256);
            b.writeBoolean(u.motion != null);
            if (u.motion != null) {
                writeMotion(b, u.motion);
            }
        }
        private static Update read(RegistryFriendlyByteBuf b) {
            int entity = b.readVarInt(); long incident = b.readLong(); int epoch = b.readInt(); int frame = b.readInt();
            int status = b.readVarInt(); int ticks = b.readVarInt();
            java.util.UUID uuid = b.readUUID(); String dimension = b.readUtf(256);
            OpenwheelCarEntity.RollbackMotion motion = null;
            if (b.readBoolean()) {
                motion = readMotion(b);
            }
            return new Update(entity, incident, epoch, frame, status, ticks, motion, uuid, dimension);
        }
        private static void handle(Update u, IPayloadContext context) {
            context.enqueueWork(() -> {
                try {
                    Class.forName("com.openwheelracing.client.debug.VehicleRollbackClient")
                        .getMethod("receive", Update.class).invoke(null, u);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Unable to apply vehicle rewind", exception);
                }
            });
        }
    }

    private static void writeMotion(RegistryFriendlyByteBuf b, OpenwheelCarEntity.RollbackMotion motion) {
        writeVector(b, motion.position()); writeVector(b, motion.velocity());
        b.writeFloat(motion.yaw()); b.writeBoolean(motion.grounded()); b.writeInt(motion.tick());
        b.writeVarInt(motion.state().length);
        for (double value : motion.state()) b.writeDouble(value);
        b.writeVarInt(motion.counters().length);
        for (int value : motion.counters()) b.writeInt(value);
    }

    private static OpenwheelCarEntity.RollbackMotion readMotion(RegistryFriendlyByteBuf b) {
        Vec3 position = readVector(b), velocity = readVector(b); float yaw = b.readFloat();
        boolean ground = b.readBoolean(); int tick = b.readInt();
        int length = b.readVarInt();
        if (length != OpenwheelCarEntity.rollbackStateSize()) throw new IllegalArgumentException("Invalid rewind state");
        double[] state = new double[length];
        for (int i = 0; i < length; i++) state[i] = b.readDouble();
        int count = b.readVarInt();
        if (count != OpenwheelCarEntity.rollbackCounterSize()) throw new IllegalArgumentException("Invalid rewind counters");
        int[] counters = new int[count];
        for (int i = 0; i < count; i++) counters[i] = b.readInt();
        return new OpenwheelCarEntity.RollbackMotion(position, velocity, yaw, ground, tick, state, counters);
    }

    private static void writeVector(FriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 readVector(FriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
}
