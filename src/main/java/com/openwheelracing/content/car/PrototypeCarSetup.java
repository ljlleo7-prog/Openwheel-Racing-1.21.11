package com.openwheelracing.content.car;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Driver-adjustable setup. Tyre compound remains here for save compatibility, but is selected by the fitted tyre. */
public record PrototypeCarSetup(int power, int grip, int aero, int gearing,
                                int frontWing, int rearWing, int antiRoll, int brakeBias) {
    public static final int FRONT_WING_MIN = 3;
    public static final int FRONT_WING_MAX = 7;
    public static final int REAR_WING_MIN = 9;
    public static final int REAR_WING_MAX = 15;
    public static final int ANTI_ROLL_MAX = 10;
    public static final int BRAKE_BIAS_MIN = 50;
    public static final int BRAKE_BIAS_MAX = 65;
    public static final PrototypeCarSetup DEFAULT = new PrototypeCarSetup(1, 3, 2, 1, 5, 12, 5, 57);

    public static final Codec<PrototypeCarSetup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("power").forGetter(PrototypeCarSetup::power),
        Codec.INT.fieldOf("grip").forGetter(PrototypeCarSetup::grip),
        Codec.INT.fieldOf("aero").forGetter(PrototypeCarSetup::aero),
        Codec.INT.fieldOf("gearing").forGetter(PrototypeCarSetup::gearing),
        Codec.INT.optionalFieldOf("front_wing", DEFAULT.frontWing()).forGetter(PrototypeCarSetup::frontWing),
        Codec.INT.optionalFieldOf("rear_wing", DEFAULT.rearWing()).forGetter(PrototypeCarSetup::rearWing),
        Codec.INT.optionalFieldOf("anti_roll", DEFAULT.antiRoll()).forGetter(PrototypeCarSetup::antiRoll),
        Codec.INT.optionalFieldOf("brake_bias", DEFAULT.brakeBias()).forGetter(PrototypeCarSetup::brakeBias)
    ).apply(instance, PrototypeCarSetup::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrototypeCarSetup> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT, PrototypeCarSetup::power, ByteBufCodecs.INT, PrototypeCarSetup::grip,
        ByteBufCodecs.INT, PrototypeCarSetup::aero, ByteBufCodecs.INT, PrototypeCarSetup::gearing,
        ByteBufCodecs.INT, PrototypeCarSetup::frontWing, ByteBufCodecs.INT, PrototypeCarSetup::rearWing,
        ByteBufCodecs.INT, PrototypeCarSetup::antiRoll, ByteBufCodecs.INT, PrototypeCarSetup::brakeBias,
        PrototypeCarSetup::new);

    public PrototypeCarSetup(int power, int grip, int aero, int gearing) {
        this(power, grip, aero, gearing, 3 + aero, 9 + (int) Math.round(aero * 1.5), DEFAULT.antiRoll(), DEFAULT.brakeBias());
    }

    public PrototypeCarSetup {
        power = 1; grip = clamp(grip, 4); aero = clamp(aero, 4); gearing = clamp(gearing, 2);
        frontWing = clamp(frontWing, FRONT_WING_MIN, FRONT_WING_MAX);
        rearWing = clamp(rearWing, REAR_WING_MIN, REAR_WING_MAX);
        antiRoll = clamp(antiRoll, ANTI_ROLL_MAX);
        brakeBias = Math.max(BRAKE_BIAS_MIN, Math.min(BRAKE_BIAS_MAX, brakeBias));
    }

    public double powerMultiplier() { return 0.70 + power * 0.20; }
    public double gripMultiplier() { return tyreMuCoefficient(); }
    public double tyreMuCoefficient() { return 1.07 + (grip - DEFAULT.grip()) * 0.07; }
    public double aeroMultiplier() { return clACoefficient(); }
    public double clACoefficient() { return CarSetupPhysics.downforceCoefficient(frontWing, rearWing); }
    public double cdACoefficient() { return CarSetupPhysics.dragCoefficient(frontWing, rearWing); }
    public double frontAeroBalanceAdjustment() { return CarSetupPhysics.frontAeroBalanceAdjustment(frontWing, rearWing); }
    public double frontRollStiffnessShare() { return CarSetupPhysics.frontRollStiffnessShare(antiRoll); }
    public double brakeFrontBias() { return CarSetupPhysics.brakeFrontBias(brakeBias); }
    public double dragMultiplier() { return cdACoefficient(); }
    public double gearingMultiplier() { return topSpeedCoefficient(); }
    public double topSpeedCoefficient() { return 1.0 / gearRatioCoefficient(); }
    public double accelerationMultiplier() { return gearRatioCoefficient(); }
    public double gearRatioCoefficient() { return 1.0 + (DEFAULT.gearing() - gearing) * 0.10; }
    public double fuelUseMultiplier() { return 0.75 + power * 0.25; }
    public double tyreWearMultiplier() { return 0.93 + (grip - DEFAULT.grip()) * 0.18; }

    public PrototypeCarSetup withTyreCompound(int compound) {
        return new PrototypeCarSetup(power, compound, aero, gearing, frontWing, rearWing, antiRoll, brakeBias);
    }

    public PrototypeCarSetup withTuning(int slot, int value) {
        return switch (slot) {
            case 0 -> this;
            case 1 -> new PrototypeCarSetup(power, grip, aero, gearing, value, rearWing, antiRoll, brakeBias);
            case 2 -> new PrototypeCarSetup(power, grip, aero, gearing, frontWing, value, antiRoll, brakeBias);
            case 3 -> new PrototypeCarSetup(power, grip, aero, value, frontWing, rearWing, antiRoll, brakeBias);
            case 4 -> new PrototypeCarSetup(power, grip, aero, gearing, frontWing, rearWing, value, brakeBias);
            case 5 -> new PrototypeCarSetup(power, grip, aero, gearing, frontWing, rearWing, antiRoll, value);
            default -> this;
        };
    }

    private static int clamp(int value, int max) { return Math.max(0, Math.min(max, value)); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
