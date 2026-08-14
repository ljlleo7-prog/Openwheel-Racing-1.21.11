package com.openwheelracing.content.car;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record CarComponentDamage(int frontEnd, int rearEnd, int chassis, int engine, int frontLeftWheel, int frontRightWheel, int rearLeftWheel, int rearRightWheel) {
    public static final CarComponentDamage NONE = new CarComponentDamage(0, 0, 0, 0, 0, 0, 0, 0);

    public static final Codec<CarComponentDamage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(0, 100).fieldOf("front_end").forGetter(CarComponentDamage::frontEnd),
        Codec.intRange(0, 100).fieldOf("rear_end").forGetter(CarComponentDamage::rearEnd),
        Codec.intRange(0, 100).fieldOf("chassis").forGetter(CarComponentDamage::chassis),
        Codec.intRange(0, 100).optionalFieldOf("engine", 0).forGetter(CarComponentDamage::engine),
        Codec.intRange(0, 100).fieldOf("front_left_wheel").forGetter(CarComponentDamage::frontLeftWheel),
        Codec.intRange(0, 100).fieldOf("front_right_wheel").forGetter(CarComponentDamage::frontRightWheel),
        Codec.intRange(0, 100).fieldOf("rear_left_wheel").forGetter(CarComponentDamage::rearLeftWheel),
        Codec.intRange(0, 100).fieldOf("rear_right_wheel").forGetter(CarComponentDamage::rearRightWheel)
    ).apply(instance, CarComponentDamage::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CarComponentDamage> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, CarComponentDamage::frontEnd,
        ByteBufCodecs.VAR_INT, CarComponentDamage::rearEnd,
        ByteBufCodecs.VAR_INT, CarComponentDamage::chassis,
        ByteBufCodecs.VAR_INT, CarComponentDamage::engine,
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
        engine = clamp(engine);
        frontLeftWheel = clamp(frontLeftWheel);
        frontRightWheel = clamp(frontRightWheel);
        rearLeftWheel = clamp(rearLeftWheel);
        rearRightWheel = clamp(rearRightWheel);
    }

    public static CarComponentDamage fromLegacyDamage(int damage) {
        int normalized = clamp(damage);
        return new CarComponentDamage(0, 0, normalized, 0, 0, 0, 0, 0);
    }

    public int aggregate() {
        double total = chassis * 0.32
            + engine * 0.10
            + frontEnd * 0.14
            + rearEnd * 0.14
            + frontLeftWheel * 0.075
            + frontRightWheel * 0.075
            + rearLeftWheel * 0.075
            + rearRightWheel * 0.075;
        return clamp((int) Math.round(total));
    }

    public int worst() {
        return Math.max(Math.max(Math.max(frontEnd, rearEnd), Math.max(chassis, engine)), Math.max(Math.max(frontLeftWheel, frontRightWheel), Math.max(rearLeftWheel, rearRightWheel)));
    }

    public boolean chassisDestroyed() {
        return chassis >= 100;
    }

    public boolean engineDestroyed() {
        return engine >= 100;
    }

    public CarComponentDamage repairAll(int amount) {
        return new CarComponentDamage(
            frontEnd - amount,
            rearEnd - amount,
            chassis - amount,
            engine - amount,
            frontLeftWheel - amount,
            frontRightWheel - amount,
            rearLeftWheel - amount,
            rearRightWheel - amount
        );
    }

    public static double nonlinearDamageCurve(double damage, double maximumLoss, double exponent) {
        double normalized = Math.max(0.0, Math.min(1.0, damage / 100.0));
        return 1.0 - maximumLoss * Math.pow(normalized, exponent);
    }

    public static double frontWingAeroMultiplier(double damage) {
        return nonlinearDamageCurve(damage, 0.45, 1.65);
    }

    public static double rearWingAeroMultiplier(double damage) {
        return nonlinearDamageCurve(damage, 0.55, 1.65);
    }

    public static double chassisPowerMultiplier(double damage) {
        return nonlinearDamageCurve(damage, 0.30, 1.8);
    }

    public static double chassisDragMultiplier(double damage) {
        double normalized = Math.max(0.0, Math.min(1.0, damage / 100.0));
        return 1.0 + 0.45 * normalized * normalized;
    }

    public static double wheelGripMultiplier(double damage) {
        return nonlinearDamageCurve(damage, 0.65, 1.55);
    }

    public static double wheelDragPenalty(double damage) {
        double normalized = Math.max(0.0, Math.min(1.0, damage / 100.0));
        return 0.0012 * Math.pow(normalized, 1.7);
    }

    public static double enginePowerMultiplier(double damage) {
        return nonlinearDamageCurve(damage, 0.75, 1.7);
    }

    public static double engineErsEfficiencyMultiplier(double damage) {
        return nonlinearDamageCurve(damage, 0.60, 1.5);
    }

    public static double chassisToEngineTransferFraction(double chassisDamage) {
        double normalized = Math.max(0.0, Math.min(1.0, chassisDamage / 100.0));
        return 0.05 + 0.35 * Math.pow(normalized, 2.2);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
