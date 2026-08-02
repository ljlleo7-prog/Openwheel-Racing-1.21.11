package com.openwheelracing.content.race;

import com.openwheelracing.content.car.CarLivery;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.track.TrackMapSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public record TeamCarRow(int entityId, String liveryName, String riderName, int liveryColor, float speedKmh, int gear, int rpm, float ersPercent, float ersPowerKw, float tyrePercent, float tyreTemperatureC, float damagePercent,
                         double x, double z, float headingDegrees, boolean onMap, boolean inPitLane, int liveRank) {
    public static TeamCarRow fromCar(OpenwheelCarEntity car, TrackMapSnapshot map, int liveRank) {
        Entity passenger = car.getFirstPassenger();
        String rider = passenger instanceof LivingEntity living ? living.getDisplayName().getString() : "--";
        int blockX = (int) Math.floor(car.getX());
        int blockZ = (int) Math.floor(car.getZ());
        boolean inPitLane = contains(map.pitRuns(), blockX, blockZ);
        boolean onMap = inPitLane || contains(map.asphaltRuns(), blockX, blockZ);
        return new TeamCarRow(
            car.getId(),
            CarLivery.fromIndex(car.getLivery()).displayName(),
            rider,
            car.getLiveryColors().bodySide(),
            car.getSpeedKmh(),
            car.getGear(),
            car.getRpm(),
            car.getErsEnergyPercent(),
            car.getErsPowerKw(),
            Math.max(0.0f, 100.0f - car.getTyreWearPercent()),
            car.getTyreTemperatureCelsius(),
            car.getDamagePercent(),
            car.getX(),
            car.getZ(),
            car.getYRot(),
            onMap,
            inPitLane,
            liveRank
        );
    }

    public static TeamCarRow fromCar(OpenwheelCarEntity car) {
        return fromCar(car, TrackMapSnapshot.EMPTY, 0);
    }

    public static void encode(TeamCarRow row, FriendlyByteBuf buffer) {
        buffer.writeInt(row.entityId);
        buffer.writeUtf(row.liveryName);
        buffer.writeUtf(row.riderName);
        buffer.writeInt(row.liveryColor);
        buffer.writeFloat(row.speedKmh);
        buffer.writeInt(row.gear);
        buffer.writeInt(row.rpm);
        buffer.writeFloat(row.ersPercent);
        buffer.writeFloat(row.ersPowerKw);
        buffer.writeFloat(row.tyrePercent);
        buffer.writeFloat(row.tyreTemperatureC);
        buffer.writeFloat(row.damagePercent);
        buffer.writeDouble(row.x);
        buffer.writeDouble(row.z);
        buffer.writeFloat(row.headingDegrees);
        buffer.writeBoolean(row.onMap);
        buffer.writeBoolean(row.inPitLane);
        buffer.writeInt(row.liveRank);
    }

    public static TeamCarRow decode(FriendlyByteBuf buffer) {
        return new TeamCarRow(buffer.readInt(), buffer.readUtf(), buffer.readUtf(), buffer.readInt(), buffer.readFloat(), buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean(), buffer.readInt());
    }

    private static boolean contains(java.util.List<TrackMapSnapshot.CellRun> runs, int x, int z) {
        for (TrackMapSnapshot.CellRun run : runs) {
            if (run.z() == z && x >= run.startX() && x <= run.endX()) {
                return true;
            }
        }
        return false;
    }
}
