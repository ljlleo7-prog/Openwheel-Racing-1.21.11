package com.openwheelracing.registry;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.block.entity.CarAssemblyWorkstationBlockEntity;
import com.openwheelracing.content.block.entity.RaceDirectorBlockEntity;
import com.openwheelracing.content.block.entity.RefineryBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Set;

public final class OWRBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, OpenwheelRacing.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CarAssemblyWorkstationBlockEntity>> CAR_ASSEMBLY_WORKSTATION = BLOCK_ENTITIES.register("car_assembly_workstation",
        () -> new BlockEntityType<>(CarAssemblyWorkstationBlockEntity::new, Set.of(OWRBlocks.CAR_ASSEMBLY_WORKSTATION.get()))
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefineryBlockEntity>> REFINERY = BLOCK_ENTITIES.register("refinery",
        () -> new BlockEntityType<>(RefineryBlockEntity::new, Set.of(OWRBlocks.REFINERY.get()))
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RaceDirectorBlockEntity>> RACE_DIRECTOR = BLOCK_ENTITIES.register("race_director",
        () -> new BlockEntityType<>(RaceDirectorBlockEntity::new, Set.of(OWRBlocks.RACE_DIRECTOR.get()))
    );

    private OWRBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
