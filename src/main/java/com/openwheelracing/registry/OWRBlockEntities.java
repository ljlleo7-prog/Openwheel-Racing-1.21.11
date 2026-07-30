package com.openwheelracing.registry;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.block.entity.CarAssemblyWorkstationBlockEntity;
import com.openwheelracing.content.block.entity.CarWorkstationType;
import com.openwheelracing.content.block.entity.RaceDirectorBlockEntity;
import com.openwheelracing.content.block.entity.RaceMonitorType;
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
        () -> new BlockEntityType<>((pos, state) -> new CarAssemblyWorkstationBlockEntity(pos, state, CarWorkstationType.LEGACY), Set.of(OWRBlocks.CAR_ASSEMBLY_WORKSTATION.get()))
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CarAssemblyWorkstationBlockEntity>> CAR_CONSTRUCTION_STATION = BLOCK_ENTITIES.register("car_construction_station",
        () -> new BlockEntityType<>((pos, state) -> new CarAssemblyWorkstationBlockEntity(pos, state, CarWorkstationType.CONSTRUCTION), Set.of(OWRBlocks.CAR_CONSTRUCTION_STATION.get()))
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CarAssemblyWorkstationBlockEntity>> CAR_SETUP_STATION = BLOCK_ENTITIES.register("car_setup_station",
        () -> new BlockEntityType<>((pos, state) -> new CarAssemblyWorkstationBlockEntity(pos, state, CarWorkstationType.SETUP), Set.of(OWRBlocks.CAR_SETUP_STATION.get()))
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CarAssemblyWorkstationBlockEntity>> CAR_LIVERY_STATION = BLOCK_ENTITIES.register("car_livery_station",
        () -> new BlockEntityType<>((pos, state) -> new CarAssemblyWorkstationBlockEntity(pos, state, CarWorkstationType.LIVERY), Set.of(OWRBlocks.CAR_LIVERY_STATION.get()))
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefineryBlockEntity>> REFINERY = BLOCK_ENTITIES.register("refinery",
        () -> new BlockEntityType<>(RefineryBlockEntity::new, Set.of(OWRBlocks.REFINERY.get()))
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RaceDirectorBlockEntity>> RACE_DIRECTOR = BLOCK_ENTITIES.register("race_director",
        () -> new BlockEntityType<>((pos, state) -> new RaceDirectorBlockEntity(pos, state, RaceMonitorType.DIRECTOR), Set.of(OWRBlocks.RACE_DIRECTOR.get()))
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RaceDirectorBlockEntity>> RACE_BOARD_TERMINAL = BLOCK_ENTITIES.register("race_board_terminal",
        () -> new BlockEntityType<>((pos, state) -> new RaceDirectorBlockEntity(pos, state, RaceMonitorType.BOARD), Set.of(OWRBlocks.RACE_BOARD_TERMINAL.get()))
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RaceDirectorBlockEntity>> TEAM_TERMINAL = BLOCK_ENTITIES.register("team_terminal",
        () -> new BlockEntityType<>((pos, state) -> new RaceDirectorBlockEntity(pos, state, RaceMonitorType.TEAM), Set.of(OWRBlocks.TEAM_TERMINAL.get()))
    );

    private OWRBlockEntities() {
    }

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<CarAssemblyWorkstationBlockEntity>> typeFor(CarWorkstationType workstationType) {
        return switch (workstationType) {
            case CONSTRUCTION -> CAR_CONSTRUCTION_STATION;
            case SETUP -> CAR_SETUP_STATION;
            case LIVERY -> CAR_LIVERY_STATION;
            case LEGACY -> CAR_ASSEMBLY_WORKSTATION;
        };
    }

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<RaceDirectorBlockEntity>> typeFor(RaceMonitorType monitorType) {
        return switch (monitorType) {
            case DIRECTOR -> RACE_DIRECTOR;
            case BOARD -> RACE_BOARD_TERMINAL;
            case TEAM -> TEAM_TERMINAL;
        };
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
