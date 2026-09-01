package com.openwheelracing.content.race;

import net.minecraft.network.FriendlyByteBuf;

public record PitLanePenaltyRow(long id, String driverName, double instantKmh, double averageKmh, long gameTime, String reason) {
    public static PitLanePenaltyRow from(PitLanePenaltyData.Incident incident) {
        return new PitLanePenaltyRow(incident.id(), incident.driverName(), incident.instantKmh(), incident.averageKmh(), incident.gameTime(), incident.reason());
    }

    public static void encode(PitLanePenaltyRow row, FriendlyByteBuf buffer) {
        buffer.writeLong(row.id);
        buffer.writeUtf(row.driverName);
        buffer.writeDouble(row.instantKmh);
        buffer.writeDouble(row.averageKmh);
        buffer.writeLong(row.gameTime);
        buffer.writeUtf(row.reason);
    }

    public static PitLanePenaltyRow decode(FriendlyByteBuf buffer) {
        return new PitLanePenaltyRow(buffer.readLong(), buffer.readUtf(), buffer.readDouble(), buffer.readDouble(), buffer.readLong(), buffer.readUtf());
    }
}
