package com.openwheelracing.content.race;

import net.minecraft.network.FriendlyByteBuf;

public record RaceDirectorLapRow(long id, String driverName, int lapMillis, int checkpointCount, boolean invalidated, String invalidationReason, long startFinishPos, int power, int grip, int aero, int gearing, int damagePercent, int tyreWearPercent, boolean absEnabled, long sessionId, String sessionName) {
    public static RaceDirectorLapRow fromRecord(OWRLapRecords.LapRecord record) {
        OWRLapRecords.CarSnapshot car = record.car();
        return new RaceDirectorLapRow(record.id(), record.driverName(), record.lapMillis(), record.checkpointCount(), record.invalidated(), record.invalidationReason(), record.startFinishPos(), car.power(), car.grip(), car.aero(), car.gearing(), car.damagePercent(), car.tyreWearPercent(), car.absEnabled(), record.sessionId(), record.sessionName());
    }

    public static void encode(RaceDirectorLapRow row, FriendlyByteBuf buffer) {
        buffer.writeLong(row.id);
        buffer.writeUtf(row.driverName);
        buffer.writeInt(row.lapMillis);
        buffer.writeInt(row.checkpointCount);
        buffer.writeBoolean(row.invalidated);
        buffer.writeUtf(row.invalidationReason);
        buffer.writeLong(row.startFinishPos);
        buffer.writeInt(row.power);
        buffer.writeInt(row.grip);
        buffer.writeInt(row.aero);
        buffer.writeInt(row.gearing);
        buffer.writeInt(row.damagePercent);
        buffer.writeInt(row.tyreWearPercent);
        buffer.writeBoolean(row.absEnabled);
        buffer.writeLong(row.sessionId);
        buffer.writeUtf(row.sessionName);
    }

    public static RaceDirectorLapRow decode(FriendlyByteBuf buffer) {
        return new RaceDirectorLapRow(buffer.readLong(), buffer.readUtf(), buffer.readInt(), buffer.readInt(), buffer.readBoolean(), buffer.readUtf(), buffer.readLong(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readBoolean(), buffer.readLong(), buffer.readUtf());
    }
}
