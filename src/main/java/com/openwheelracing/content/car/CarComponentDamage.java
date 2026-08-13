package com.openwheelracing.content.car;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record CarComponentDamage(int frontEnd, int rearEnd, int chassis, int frontLeftWheel, int frontRightWheel, int rearLeftWheel, int rearRightWheel) {
    public static final CarComponentDamage NONE = new CarComponentDamage(0, 0, 0, 0, 0, 0, 0);

    public static final Codec<CarComponentDamage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(0, 100).fieldOf("front_end").forGetter(CarComponentDamage::frontEnd),
        Codec.intRange(0, 100).fieldOf("rear_end").forGetter(CarComponentDamage::rearEnd),
        Codec.intRange(0, 100).fieldOf("chassis").forGetter(CarComponentDamage::chassis),
        Codec.intRange(0, 100).fieldOf("front_left_wheel").forGetter(CarComponentDamage::frontLeftWheel),
        Codec.intRange(0, 100).fieldOf("front_right_wheel").forGetter(CarComponentDamage::frontRightWheel),
        Codec.intRange(0, 100).fieldOf("rear_left_wheel").forGetter(CarComponentDamage::rearLeftWheel),
        Codec.intRange(0, 100).fieldOf("rear_right_wheel").forGetter(CarComponentDamage::rearRightWheel)
    ).apply(instance, CarComponentDamage::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CarComponentDamage> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, CarComponentDamage::frontEnd,
        ByteBufCodecs.VAR_INT, CarComponentDamage::rearEnd,
        ByteBufCodecs.VAR_INT, CarComponentDamage::chassis,
        ByteBufCodecs.VAR_INT, CarComponentDamage::frontLeftWheel,
        ByteBufCodecs.VAR_INT, CarComponentDamage::frontRightWheel,
        ByteBufCodecs.VAR_INT, CarComponentDamage::rearLeftWheel,
        ByteBufCodecs.VAR_INT, CarComponentDamage::rearRightWheel,
        CarComponentDamage::new
    );

    public CarComponentDamage {
        frontEnd = clamp(frontEnd);
        rearEnd = clamp(rearEnd);
        chassis = clamp(chassis);
        frontLeftWheel = clamp(frontLeftWheel);
        frontRightWheel = clamp(frontRightWheel);
        rearLeftWheel = clamp(rearLeftWheel);
        rearRightWheel = clamp(rearRightWheel);
    }

    public static CarComponentDamage fromLegacyDamage(int damage) {
        int normalized = clamp(damage);
        return new CarComponentDamage(normalized, normalized, normalized, normalized, normalized, normalized, normalized);
    }

    public int aggregate() {
        double total = chassis * 0.35
            + frontEnd * 0.15
            + rearEnd * 0.15
            + frontLeftWheel * 0.0875
            + frontRightWheel * 0.0875
            + rearLeftWheel * 0.0875
            + rearRightWheel * 0.0875;
        return clamp((int) Math.round(total));
    }

    public int worst() {
        return Math.max(Math.max(Math.max(frontEnd, rearEnd), chassis), Math.max(Math.max(frontLeftWheel, frontRightWheel), Math.max(rearLeftWheel, rearRightWheel)));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
