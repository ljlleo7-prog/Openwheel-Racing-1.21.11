package com.openwheelracing.content.race;

import com.openwheelracing.content.car.CarLivery;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public record TeamCarRow(int entityId, String liveryName, String riderName, int liveryColor, float speedKmh, int gear, int rpm, float ersPercent, float ersPowerKw, float tyrePercent, float tyreTemperatureC, float damagePercent) {
    public static TeamCarRow fromCar(OpenwheelCarEntity car) {
        Entity passenger = car.getFirstPassenger();
        String rider = passenger instanceof LivingEntity living ? living.getDisplayName().getString() : "--";
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
            car.getDamagePercent()
        );
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
    }

    public static TeamCarRow decode(FriendlyByteBuf buffer) {
        return new TeamCarRow(buffer.readInt(), buffer.readUtf(), buffer.readUtf(), buffer.readInt(), buffer.readFloat(), buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }
}
