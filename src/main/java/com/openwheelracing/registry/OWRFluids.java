package com.openwheelracing.registry;

import com.openwheelracing.OpenwheelRacing;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class OWRFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(ForgeRegistries.FLUID_TYPES, OpenwheelRacing.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, OpenwheelRacing.MODID);

    public static final RegistryObject<FluidType> CRUDE_OIL_TYPE = FLUID_TYPES.register("crude_oil",
        () -> new FluidType(FluidType.Properties.create()
        .canSwim(false)
        .canDrown(true)
        .canConvertToSource(false)
        .density(3000)
        .viscosity(6000)
        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)));

    public static final RegistryObject<FlowingFluid> CRUDE_OIL = FLUIDS.register("crude_oil", () -> new ForgeFlowingFluid.Source(crudeOilProperties()));
    public static final RegistryObject<FlowingFluid> FLOWING_CRUDE_OIL = FLUIDS.register("flowing_crude_oil", () -> new ForgeFlowingFluid.Flowing(crudeOilProperties()));

    private OWRFluids() {
    }

    public static void register(BusGroup modBus) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
    }

    private static ForgeFlowingFluid.Properties crudeOilProperties() {
        return new ForgeFlowingFluid.Properties(CRUDE_OIL_TYPE, CRUDE_OIL, FLOWING_CRUDE_OIL)
            .bucket(OWRItems.CRUDE_OIL_BUCKET)
            .block(OWRBlocks.CRUDE_OIL_DEPOSIT)
            .slopeFindDistance(2)
            .levelDecreasePerBlock(2)
            .tickRate(30)
            .explosionResistance(100.0f);
    }
}
