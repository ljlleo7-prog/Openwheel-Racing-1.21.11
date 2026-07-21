package com.openwheelracing.content.race;

import net.minecraft.network.FriendlyByteBuf;

public record RaceDirectorLapRow(long id, String driverName, int lapTicks, int checkpointCount, boolean invalidated, String invalidationReason, long startFinishPos, int power, int grip, int aero, int gearing, int damagePercent, int tyreWearPercent, boolean absEnabled) {
    public static RaceDirectorLapRow fromRecord(OWRLapRecords.LapRecord record) {
        OWRLapRecords.CarSnapshot car = record.car();
        return new RaceDirectorLapRow(record.id(), record.driverName(), record.lapTicks(), record.checkpointCount(), record.invalidated(), record.invalidationReason(), record.startFinishPos(), car.power(), car.grip(), car.aero(), car.gearing(), car.damagePercent(), car.tyreWearPercent(), car.absEnabled());
    }

    public static void encode(RaceDirectorLapRow row, FriendlyByteBuf buffer) {
        buffer.writeLong(row.id);
        buffer.writeUtf(row.driverName);
        buffer.writeInt(row.lapTicks);
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
    }

    public static RaceDirectorLapRow decode(FriendlyByteBuf buffer) {
        return new RaceDirectorLapRow(buffer.readLong(), buffer.readUtf(), buffer.readInt(), buffer.readInt(), buffer.readBoolean(), buffer.readUtf(), buffer.readLong(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readBoolean());
    }
}
