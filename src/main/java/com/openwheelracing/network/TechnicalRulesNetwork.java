package com.openwheelracing.network;

import com.openwheelracing.content.entity.PerformanceRules;
import com.openwheelracing.content.entity.VehiclePhysicsPreset;
import com.openwheelracing.content.entity.VehiclePhysicsPresetState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Fresh per-login challenge. A match admits driving; malformed/mismatched performance is rejected. */
public final class TechnicalRulesNetwork {
    private record Session(UUID nonce, long started, int preset, boolean verified) {}
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private TechnicalRulesNetwork() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Challenge.TYPE, StreamCodec.of(Challenge::write, Challenge::read), (m, c) ->
            c.enqueueWork(() -> {
                clientAdmission("begin", m.nonce);
                VehiclePhysicsPresetState.setClientPreset(VehiclePhysicsPreset.values()[m.preset]);
                OWRNetwork.sendToServer(new Answer(m.nonce, PerformanceRules.fingerprint(), PerformanceRules.probe(m.preset)));
            }));
        registrar.playToServer(Answer.TYPE, StreamCodec.of(Answer::write, Answer::read), (m, c) ->
            c.enqueueWork(() -> { if (c.player() instanceof ServerPlayer player) answer(player, m); }));
        registrar.playToClient(Result.TYPE, StreamCodec.of(Result::write, Result::read), (m, c) ->
            c.enqueueWork(() -> clientAdmission("accept", m.nonce)));
    }

    private static void clientAdmission(String method, UUID nonce) {
        try {
            Class.forName("com.openwheelracing.client.debug.TechnicalClientState")
                .getMethod(method, UUID.class).invoke(null, nonce);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to apply technical admission", exception);
        }
    }

    public static void begin(ServerPlayer player) {
        int preset = VehiclePhysicsPresetState.current(player.level()).ordinal();
        Session session = new Session(UUID.randomUUID(), System.nanoTime(), preset, false);
        SESSIONS.put(player.getUUID(), session);
        PacketDistributor.sendToPlayer(player, new Challenge(session.nonce, preset));
    }
    public static boolean hasSession(UUID player) { return SESSIONS.containsKey(player); }
    public static boolean verified(UUID player) {
        Session session = SESSIONS.get(player);
        return session != null && session.verified;
    }
    public static void clear(UUID player) { SESSIONS.remove(player); }
    public static void clearAll() { SESSIONS.clear(); }

    public static void answer(ServerPlayer player, Answer message) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.verified || !session.nonce.equals(message.nonce)) return;
        if (!PerformanceRules.fingerprint().equals(message.fingerprint)
                || !PerformanceRules.probe(session.preset).equals(message.probe)) {
            violation(player, "performance definitions or physics probe differ from the server");
            return;
        }
        SESSIONS.put(player.getUUID(), new Session(session.nonce, session.started, session.preset, true));
        PacketDistributor.sendToPlayer(player, new Result(session.nonce));
        player.sendSystemMessage(Component.translatable("technical.openwheelracing.passed"));
    }

    public static void violation(ServerPlayer player, String reason) {
        SESSIONS.remove(player.getUUID());
        player.connection.disconnect(Component.translatable("technical.openwheelracing.violation", reason));
    }

    public static void tick(ServerTickEvent.Post event) {
        long now = System.nanoTime();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Session session = SESSIONS.get(player.getUUID());
            if (session != null && !session.verified && now - session.started > 30_000_000_000L) {
                SESSIONS.remove(player.getUUID());
                player.connection.disconnect(Component.translatable("technical.openwheelracing.timeout"));
            }
        }
    }

    public record Result(UUID nonce) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(Identifier.fromNamespaceAndPath("openwheelracing", "technical_result"));
        @Override public Type<Result> type() { return TYPE; }
        private static void write(RegistryFriendlyByteBuf b, Result m) { b.writeUUID(m.nonce); }
        private static Result read(RegistryFriendlyByteBuf b) { return new Result(b.readUUID()); }
    }

    public record Challenge(UUID nonce, int preset) implements CustomPacketPayload {
        public static final Type<Challenge> TYPE = new Type<>(Identifier.fromNamespaceAndPath("openwheelracing", "technical_challenge"));
        @Override public Type<Challenge> type() { return TYPE; }
        private static void write(RegistryFriendlyByteBuf b, Challenge m) { b.writeUUID(m.nonce); b.writeVarInt(m.preset); }
        private static Challenge read(RegistryFriendlyByteBuf b) {
            UUID nonce = b.readUUID(); int preset = b.readVarInt();
            if (preset < 0 || preset >= VehiclePhysicsPreset.values().length) throw new IllegalArgumentException("Invalid physics preset");
            return new Challenge(nonce, preset);
        }
    }
    public record Answer(UUID nonce, String fingerprint, String probe) implements CustomPacketPayload {
        public static final Type<Answer> TYPE = new Type<>(Identifier.fromNamespaceAndPath("openwheelracing", "technical_answer"));
        @Override public Type<Answer> type() { return TYPE; }
        private static void write(RegistryFriendlyByteBuf b, Answer m) {
            b.writeUUID(m.nonce); b.writeUtf(m.fingerprint, 128); b.writeUtf(m.probe, 256);
        }
        private static Answer read(RegistryFriendlyByteBuf b) { return new Answer(b.readUUID(), b.readUtf(128), b.readUtf(256)); }
    }
}
