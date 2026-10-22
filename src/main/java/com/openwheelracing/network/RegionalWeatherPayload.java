package com.openwheelracing.network;

import com.openwheelracing.content.weather.RegionalWeather;
import com.openwheelracing.content.weather.WeatherRegion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

public record RegionalWeatherPayload(String dimension, List<WeatherRegion> regions) implements CustomPacketPayload {
    public static final Type<RegionalWeatherPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("openwheelracing", "regional_weather"));
    private static final StreamCodec<RegistryFriendlyByteBuf, RegionalWeatherPayload> CODEC = StreamCodec.of(
        (buf, message) -> {
            buf.writeUtf(message.dimension);
            buf.writeVarInt(message.regions.size());
            for (var region : message.regions) {
                buf.writeUtf(region.name(), 64);
                buf.writeInt(region.minX()); buf.writeInt(region.minZ());
                buf.writeInt(region.maxX()); buf.writeInt(region.maxZ());
                buf.writeDouble(region.wetChance()); buf.writeDouble(region.stormChance()); buf.writeDouble(region.snowChance());
                buf.writeInt(region.periodTicks()); buf.writeLong(region.seed());
            }
        }, buf -> {
            String dimension = buf.readUtf();
            int count = buf.readVarInt();
            if (count < 1 || count > 129) throw new IllegalArgumentException("Invalid weather region count");
            List<WeatherRegion> regions = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                regions.add(new WeatherRegion(buf.readUtf(64), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readLong()));
            }
            return new RegionalWeatherPayload(dimension, List.copyOf(regions));
        });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (message, context) ->
            context.enqueueWork(() -> RegionalWeather.applyClient(message.dimension, message.regions)));
    }

    public static void send(ServerPlayer player) {
        var level = player.level();
        PacketDistributor.sendToPlayer(player, new RegionalWeatherPayload(level.dimension().identifier().toString(), RegionalWeather.regions(level)));
    }
}
