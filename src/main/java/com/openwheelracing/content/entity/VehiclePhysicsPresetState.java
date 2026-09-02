package com.openwheelracing.content.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class VehiclePhysicsPresetState extends SavedData {
    private static final Codec<VehiclePhysicsPresetState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("preset", VehiclePhysicsPreset.DYNAMIC.name())
            .forGetter(state -> state.preset.name())
    ).apply(instance, VehiclePhysicsPresetState::new));
    private static final SavedDataType<VehiclePhysicsPresetState> TYPE = new SavedDataType<>(
        OpenwheelRacing.MODID + "_vehicle_physics_preset",
        VehiclePhysicsPresetState::new,
        CODEC,
        null
    );

    private static volatile VehiclePhysicsPreset clientPreset = VehiclePhysicsPreset.DYNAMIC;

    private VehiclePhysicsPreset preset;

    public VehiclePhysicsPresetState() {
        this(VehiclePhysicsPreset.DYNAMIC.name());
    }

    private VehiclePhysicsPresetState(String preset) {
        this.preset = VehiclePhysicsPreset.fromName(preset);
    }

    public static VehiclePhysicsPresetState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static VehiclePhysicsPreset current(Level level) {
        return level instanceof ServerLevel serverLevel
            ? get(serverLevel.getServer()).preset()
            : clientPreset;
    }

    public static VehiclePhysicsPreset clientPreset() {
        return clientPreset;
    }

    public static void setClientPreset(VehiclePhysicsPreset preset) {
        clientPreset = preset == null ? VehiclePhysicsPreset.DYNAMIC : preset;
    }

    public VehiclePhysicsPreset preset() {
        return preset;
    }

    public boolean setPreset(VehiclePhysicsPreset preset) {
        VehiclePhysicsPreset next = preset == null ? VehiclePhysicsPreset.DYNAMIC : preset;
        if (this.preset == next) return false;
        this.preset = next;
        setDirty();
        return true;
    }
}
