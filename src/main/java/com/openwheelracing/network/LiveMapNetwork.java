package com.openwheelracing.network;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.race.map.LiveMapSnapshot;
import com.openwheelracing.content.race.map.LiveMapSnapshot.*;
import com.openwheelracing.content.track.TrackDefinitionsData;
import com.openwheelracing.content.track.TrackMapData;
import com.openwheelracing.content.track.TrackMapSnapshot;
import com.openwheelracing.content.track.survey.TrackSurveyData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class LiveMapNetwork {
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();
    private static final int MAX_CARS = 8192;
    private static final int MAX_ROUTE_POINTS = 8192;

    private LiveMapNetwork() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Request.TYPE, Request.CODEC, LiveMapNetwork::request);
        registrar.playToClient(Response.TYPE, Response.CODEC, (message, context) -> context.enqueueWork(() -> {
            try {
                Class.forName("com.openwheelracing.client.map.LiveMapClient")
                    .getMethod("accept", LiveMapSnapshot.class).invoke(null, message.snapshot());
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not deliver live map", exception);
            }
        }));
    }

    private static void request(Request request, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = (ServerLevel) player.level();
            long tick = level.getGameTime();
            Long previous = LAST_REQUEST.get(player);
            if (previous != null && tick >= previous && tick - previous < 2) return;
            LAST_REQUEST.put(player, tick);
            String dimension = level.dimension().identifier().toString();
            Geometry geometry = null;
            if (request.geometry()) {
                var track = TrackDefinitionsData.get(level).activeTrack(dimension).orElse(null);
                List<Point> route = List.of();
                Finish finish = null;
                if (track != null) {
                    var survey = TrackSurveyData.get(level).get(track.trackId()).orElse(null);
                    route = survey != null
                        ? survey.nodes().stream().limit(MAX_ROUTE_POINTS).map(node -> new Point(node.position().x(), node.position().z())).toList()
                        : track.centerline().stream().limit(MAX_ROUTE_POINTS).map(node -> new Point(node.position().x(), node.position().z())).toList();
                    finish = track.startFinish().map(line -> new Finish(new Point(line.left().x(), line.left().z()),
                        new Point(line.right().x(), line.right().z()), line.headingRadians())).orElse(null);
                }
                geometry = new Geometry(TrackMapData.get(level).snapshot(dimension).orElse(TrackMapSnapshot.EMPTY), route, finish);
            }
            List<Car> cars = new ArrayList<>();
            // Do not use a viewer-centered AABB or tracking-player list here.
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof OpenwheelCarEntity car) || car.isRemoved()) continue;
                Entity passenger = car.getControllingPassenger();
                var ai = car.getBasicAiIdentity().orElse(null);
                var driverId = passenger != null ? passenger.getUUID() : ai != null ? ai.driverId() : car.getUUID();
                String label = passenger != null ? passenger.getDisplayName().getString()
                    : ai != null ? ai.displayName() : "[" + car.getId() + "]";
                if (label.length() > 128) label = label.substring(0, 128);
                cars.add(new Car(car.getUUID(), driverId, label, car.getX(), car.getZ(), car.getYRot()));
                if (cars.size() == MAX_CARS) break;
            }
            PacketDistributor.sendToPlayer(player, new Response(new LiveMapSnapshot(request.generation(), dimension, tick, geometry, cars)));
        });
    }

    public record Request(int generation, boolean geometry) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "live_map_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
            (buffer, request) -> { buffer.writeInt(request.generation()); buffer.writeBoolean(request.geometry()); },
            buffer -> new Request(buffer.readInt(), buffer.readBoolean()));
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Response(LiveMapSnapshot snapshot) implements CustomPacketPayload {
        public static final Type<Response> TYPE = new Type<>(Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "live_map_response"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Response> CODEC = StreamCodec.of(Response::encode, Response::decode);
        @Override public Type<Response> type() { return TYPE; }

        private static void encode(RegistryFriendlyByteBuf buffer, Response response) {
            LiveMapSnapshot snapshot = response.snapshot();
            buffer.writeInt(snapshot.generation());
            buffer.writeUtf(snapshot.dimension(), 128);
            buffer.writeLong(snapshot.tick());
            Geometry geometry = snapshot.geometry();
            buffer.writeBoolean(geometry != null);
            if (geometry != null) {
                TrackMapSnapshot.encode(geometry.surface(), buffer);
                buffer.writeVarInt(geometry.route().size());
                for (Point point : geometry.route()) writePoint(buffer, point);
                buffer.writeBoolean(geometry.finish() != null);
                if (geometry.finish() != null) {
                    writePoint(buffer, geometry.finish().left());
                    writePoint(buffer, geometry.finish().right());
                    buffer.writeDouble(geometry.finish().heading());
                }
            }
            buffer.writeVarInt(snapshot.cars().size());
            for (Car car : snapshot.cars()) {
                buffer.writeUUID(car.carId()); buffer.writeUUID(car.driverId()); buffer.writeUtf(car.label(), 128);
                buffer.writeDouble(car.x()); buffer.writeDouble(car.z()); buffer.writeFloat(car.heading());
            }
        }

        private static Response decode(RegistryFriendlyByteBuf buffer) {
            int generation = buffer.readInt();
            String dimension = buffer.readUtf(128);
            long tick = buffer.readLong();
            Geometry geometry = null;
            if (buffer.readBoolean()) {
                TrackMapSnapshot surface = TrackMapSnapshot.decode(buffer);
                int size = boundedCount(buffer, MAX_ROUTE_POINTS);
                List<Point> route = new ArrayList<>(size);
                for (int i = 0; i < size; i++) route.add(readPoint(buffer));
                Finish finish = buffer.readBoolean() ? new Finish(readPoint(buffer), readPoint(buffer), buffer.readDouble()) : null;
                geometry = new Geometry(surface, route, finish);
            }
            int size = boundedCount(buffer, MAX_CARS);
            List<Car> cars = new ArrayList<>(size);
            for (int i = 0; i < size; i++) cars.add(new Car(buffer.readUUID(), buffer.readUUID(), buffer.readUtf(128),
                buffer.readDouble(), buffer.readDouble(), buffer.readFloat()));
            return new Response(new LiveMapSnapshot(generation, dimension, tick, geometry, cars));
        }

        private static int boundedCount(RegistryFriendlyByteBuf buffer, int maximum) {
            int count = buffer.readVarInt();
            if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid live map count: " + count);
            return count;
        }

        private static void writePoint(RegistryFriendlyByteBuf buffer, Point point) { buffer.writeDouble(point.x()); buffer.writeDouble(point.z()); }
        private static Point readPoint(RegistryFriendlyByteBuf buffer) { return new Point(buffer.readDouble(), buffer.readDouble()); }
    }
}
