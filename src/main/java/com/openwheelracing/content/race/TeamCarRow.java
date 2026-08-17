package com.openwheelracing.content.race;

import com.openwheelracing.content.car.CarComponentDamage;
import com.openwheelracing.content.car.CarLivery;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.track.TrackMapSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

public record TeamCarRow(int entityId, UUID riderId, String liveryName, String riderName, int liveryColor, float speedKmh, int gear, int rpm, float ersPercent, float ersPowerKw, float tyrePercent, float tyreTemperatureC, float damagePercent, CarComponentDamage componentDamage,
                         boolean aiOwned, int lastLapMillis, int bestLapMillis, float aiPaceScale,
                         double x, double z, float headingDegrees, boolean onMap, boolean inPitLane, int liveRank) {
    public static TeamCarRow fromCar(OpenwheelCarEntity car, TrackMapSnapshot map, int liveRank) {
        Entity passenger = car.getFirstPassenger();
        var aiIdentity = car.getBasicAiIdentity().orElse(null);
        String rider = aiIdentity != null ? aiIdentity.displayName() : passenger instanceof LivingEntity living ? living.getDisplayName().getString() : "--";
        int blockX = (int) Math.floor(car.getX());
        int blockZ = (int) Math.floor(car.getZ());
        boolean inPitLane = contains(map.pitRuns(), blockX, blockZ);
        boolean onMap = inPitLane || contains(map.asphaltRuns(), blockX, blockZ);
        return new TeamCarRow(
            car.getId(),
            aiIdentity != null ? aiIdentity.driverId() : passenger instanceof ServerPlayer serverPlayer ? serverPlayer.getUUID() : new UUID(0L, 0L),
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
            car.getComponentDamage(),
            car.isBasicAiOwned(),
            car.getCompletedLapTicks(),
            car.getBestLapTicks(),
            (float) com.openwheelracing.content.ai.BasicAiFleetManager.calibrationPaceScale(car),
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
        buffer.writeUUID(row.riderId);
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
        buffer.writeVarInt(row.componentDamage.frontEnd());
        buffer.writeVarInt(row.componentDamage.rearEnd());
        buffer.writeVarInt(row.componentDamage.chassis());
        buffer.writeVarInt(row.componentDamage.engine());
        buffer.writeVarInt(row.componentDamage.frontLeftWheel());
        buffer.writeVarInt(row.componentDamage.frontRightWheel());
        buffer.writeVarInt(row.componentDamage.rearLeftWheel());
        buffer.writeVarInt(row.componentDamage.rearRightWheel());
        buffer.writeBoolean(row.aiOwned);
        buffer.writeVarInt(row.lastLapMillis);
        buffer.writeVarInt(row.bestLapMillis);
        buffer.writeFloat(row.aiPaceScale);
        buffer.writeDouble(row.x);
        buffer.writeDouble(row.z);
        buffer.writeFloat(row.headingDegrees);
        buffer.writeBoolean(row.onMap);
        buffer.writeBoolean(row.inPitLane);
        buffer.writeInt(row.liveRank);
    }

    public static TeamCarRow decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readInt();
        UUID riderId = buffer.readUUID();
        String liveryName = buffer.readUtf();
        String riderName = buffer.readUtf();
        int liveryColor = buffer.readInt();
        float speedKmh = buffer.readFloat();
        int gear = buffer.readInt();
        int rpm = buffer.readInt();
        float ersPercent = buffer.readFloat();
        float ersPowerKw = buffer.readFloat();
        float tyrePercent = buffer.readFloat();
        float tyreTemperatureC = buffer.readFloat();
        float damagePercent = buffer.readFloat();
        CarComponentDamage componentDamage = new CarComponentDamage(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        boolean aiOwned = buffer.readBoolean();
        int lastLapMillis = buffer.readVarInt();
        int bestLapMillis = buffer.readVarInt();
        float aiPaceScale = buffer.readFloat();
        return new TeamCarRow(entityId, riderId, liveryName, riderName, liveryColor, speedKmh, gear, rpm, ersPercent, ersPowerKw, tyrePercent, tyreTemperatureC, damagePercent, componentDamage,
            aiOwned, lastLapMillis, bestLapMillis, aiPaceScale, buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean(), buffer.readInt());
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
