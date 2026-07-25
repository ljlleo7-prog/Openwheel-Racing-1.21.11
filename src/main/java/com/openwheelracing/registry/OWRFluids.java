package com.openwheelracing.registry;

import com.openwheelracing.OpenwheelRacing;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class OWRFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, OpenwheelRacing.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, OpenwheelRacing.MODID);

    public static final DeferredHolder<FluidType, FluidType> CRUDE_OIL_TYPE = FLUID_TYPES.register("crude_oil", () -> new FluidType(FluidType.Properties.create()
        .canSwim(false)
        .canDrown(true)
        .canConvertToSource(false)
        .density(3000)
        .viscosity(6000)
        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)));

    public static final DeferredHolder<Fluid, FlowingFluid> CRUDE_OIL = FLUIDS.register("crude_oil", () -> new BaseFlowingFluid.Source(crudeOilProperties()));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_CRUDE_OIL = FLUIDS.register("flowing_crude_oil", () -> new BaseFlowingFluid.Flowing(crudeOilProperties()));

    private OWRFluids() {
    }

    public static void register(IEventBus modBus) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
    }

    private static BaseFlowingFluid.Properties crudeOilProperties() {
        return new BaseFlowingFluid.Properties(CRUDE_OIL_TYPE, CRUDE_OIL, FLOWING_CRUDE_OIL)
            .bucket(OWRItems.CRUDE_OIL_BUCKET)
            .block(OWRBlocks.CRUDE_OIL_DEPOSIT)
            .slopeFindDistance(2)
            .levelDecreasePerBlock(2)
            .tickRate(30)
            .explosionResistance(100.0f);
    }
}
