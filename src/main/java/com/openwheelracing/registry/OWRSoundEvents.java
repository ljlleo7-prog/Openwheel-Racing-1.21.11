package com.openwheelracing.registry;

import com.openwheelracing.OpenwheelRacing;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.RegistryObject;

public final class OWRSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, OpenwheelRacing.MODID);

    public static final RegistryObject<SoundEvent> CAR_ENGINE_RPM_5250 = registerVariable("car.engine.rpm.5250");
    public static final RegistryObject<SoundEvent> CAR_ENGINE_RPM_7425 = registerVariable("car.engine.rpm.7425");
    public static final RegistryObject<SoundEvent> CAR_ENGINE_RPM_9970 = registerVariable("car.engine.rpm.9970");
    public static final RegistryObject<SoundEvent> CAR_ENGINE_RPM_11360 = registerVariable("car.engine.rpm.11360");
    public static final RegistryObject<SoundEvent> CAR_TYRE_SQUEAL = registerVariable("car.tyre_squeal");
    public static final RegistryObject<SoundEvent> DRS_BEEP = registerVariable("car.drs_beep");

    private OWRSoundEvents() {
    }

    public static void register(BusGroup modBus) {
        SOUND_EVENTS.register(modBus);
    }

    private static RegistryObject<SoundEvent> registerVariable(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, name)));
    }
}
