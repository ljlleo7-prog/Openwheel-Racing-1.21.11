package com.openwheelracing.registry;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.block.CarAssemblyWorkstationBlock;
import com.openwheelracing.content.block.CarPartsReplacementWorkstationBlock;
import com.openwheelracing.content.block.CrudeOilBlock;
import com.openwheelracing.content.block.DirectionalTrackBlock;
import com.openwheelracing.content.block.LapMarkerBlock;
import com.openwheelracing.content.block.WettableTrackBlock;
import com.openwheelracing.content.block.WettableTrackSlabBlock;
import com.openwheelracing.content.block.RaceDirectorBlock;
import com.openwheelracing.content.block.RefineryBlock;
import com.openwheelracing.content.block.RaceLightBlock;
import com.openwheelracing.content.race.RaceLightType;
import com.openwheelracing.content.block.entity.CarWorkstationType;
import com.openwheelracing.content.block.entity.RaceMonitorType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.registries.RegistryObject;

public final class OWRBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, OpenwheelRacing.MODID);

    public static final RegistryObject<Block> CAR_ASSEMBLY_WORKSTATION = registerCarStation("car_assembly_workstation", CarWorkstationType.LEGACY);
    public static final RegistryObject<Block> CAR_CONSTRUCTION_STATION = registerCarStation("car_construction_station", CarWorkstationType.CONSTRUCTION);
    public static final RegistryObject<Block> CAR_SETUP_STATION = registerCarStation("car_setup_station", CarWorkstationType.SETUP);
    public static final RegistryObject<Block> CAR_LIVERY_STATION = registerCarStation("car_livery_station", CarWorkstationType.LIVERY);
    public static final RegistryObject<Block> CAR_PARTS_REPLACEMENT_STATION = BLOCKS.register("car_parts_replacement_station",
        () -> new CarPartsReplacementWorkstationBlock(BlockBehaviour.Properties.of()
            .setId(key("car_parts_replacement_station"))
            .mapColor(MapColor.METAL)
            .strength(3.5f, 6.0f)
            .requiresCorrectToolForDrops())
    );

    public static final RegistryObject<Block> REFINERY = BLOCKS.register("refinery",
        () -> new RefineryBlock(BlockBehaviour.Properties.of()
            .setId(key("refinery"))
            .mapColor(MapColor.METAL)
            .strength(3.5f, 6.0f)
            .requiresCorrectToolForDrops())
    );

    public static final RegistryObject<Block> RACE_DIRECTOR = registerRaceMonitor("race_director", RaceMonitorType.DIRECTOR);
    public static final RegistryObject<Block> RACE_BOARD_TERMINAL = registerRaceMonitor("race_board_terminal", RaceMonitorType.BOARD);
    public static final RegistryObject<Block> TEAM_TERMINAL = registerRaceMonitor("team_terminal", RaceMonitorType.TEAM);
    public static final RegistryObject<Block> FLAG_LIGHT = registerRaceLight("flag_light", RaceLightType.FLAG);
    public static final RegistryObject<Block> STARTING_LIGHT = registerRaceLight("starting_light", RaceLightType.START);
    public static final RegistryObject<Block> PIT_LIGHT = registerRaceLight("pit_light", RaceLightType.PIT);

    public static final RegistryObject<LiquidBlock> CRUDE_OIL_DEPOSIT = BLOCKS.register("crude_oil_deposit",
        () -> new CrudeOilBlock(BlockBehaviour.Properties.of()
            .setId(key("crude_oil_deposit"))
            .mapColor(MapColor.COLOR_BLACK)
            .noCollision()
            .strength(100.0f)
            .noLootTable())
    );

    public static final RegistryObject<Block> ASPHALT_TRACK = registerSimpleBlock("asphalt_track", MapColor.COLOR_BLACK, 2.4f, 6.0f);
    public static final RegistryObject<Block> ASPHALT_TRACK_SLAB = registerSlabBlock("asphalt_track_slab", MapColor.COLOR_BLACK, 2.4f, 6.0f);
    public static final RegistryObject<Block> KERB = registerDirectionalBlock("kerb", MapColor.COLOR_RED, 2.0f, 6.0f);
    public static final RegistryObject<Block> BARRIER = registerDrySimpleBlock("barrier", MapColor.METAL, 4.0f, 8.0f);
    public static final RegistryObject<Block> PIT_LANE = registerSimpleBlock("pit_lane", MapColor.COLOR_GRAY, 2.4f, 6.0f);
    public static final RegistryObject<Block> PIT_LANE_SLAB = registerSlabBlock("pit_lane_slab", MapColor.COLOR_GRAY, 2.4f, 6.0f);
    public static final RegistryObject<Block> PIT_STOP_MARK = registerSimpleBlock("pit_stop_mark", MapColor.COLOR_GRAY, 2.4f, 6.0f);
    public static final RegistryObject<Block> START_FINISH = registerLapMarker("start_finish", true, MapColor.SNOW);
    public static final RegistryObject<Block> CHECKPOINT = registerLapMarker("checkpoint", false, MapColor.COLOR_LIGHT_BLUE);

    public static final RegistryObject<Item> CAR_ASSEMBLY_WORKSTATION_ITEM = registerBlockItem("car_assembly_workstation", CAR_ASSEMBLY_WORKSTATION);
    public static final RegistryObject<Item> CAR_CONSTRUCTION_STATION_ITEM = registerBlockItem("car_construction_station", CAR_CONSTRUCTION_STATION);
    public static final RegistryObject<Item> CAR_SETUP_STATION_ITEM = registerBlockItem("car_setup_station", CAR_SETUP_STATION);
    public static final RegistryObject<Item> CAR_LIVERY_STATION_ITEM = registerBlockItem("car_livery_station", CAR_LIVERY_STATION);
    public static final RegistryObject<Item> CAR_PARTS_REPLACEMENT_STATION_ITEM = registerBlockItem("car_parts_replacement_station", CAR_PARTS_REPLACEMENT_STATION);
    public static final RegistryObject<Item> REFINERY_ITEM = registerBlockItem("refinery", REFINERY);
    public static final RegistryObject<Item> RACE_DIRECTOR_ITEM = registerBlockItem("race_director", RACE_DIRECTOR);
    public static final RegistryObject<Item> RACE_BOARD_TERMINAL_ITEM = registerBlockItem("race_board_terminal", RACE_BOARD_TERMINAL);
    public static final RegistryObject<Item> TEAM_TERMINAL_ITEM = registerBlockItem("team_terminal", TEAM_TERMINAL);
    public static final RegistryObject<Item> FLAG_LIGHT_ITEM = registerBlockItem("flag_light", FLAG_LIGHT);
    public static final RegistryObject<Item> STARTING_LIGHT_ITEM = registerBlockItem("starting_light", STARTING_LIGHT);
    public static final RegistryObject<Item> PIT_LIGHT_ITEM = registerBlockItem("pit_light", PIT_LIGHT);
    public static final RegistryObject<Item> ASPHALT_TRACK_ITEM = registerBlockItem("asphalt_track", ASPHALT_TRACK);
    public static final RegistryObject<Item> ASPHALT_TRACK_SLAB_ITEM = registerBlockItem("asphalt_track_slab", ASPHALT_TRACK_SLAB);
    public static final RegistryObject<Item> KERB_ITEM = registerBlockItem("kerb", KERB);
    public static final RegistryObject<Item> BARRIER_ITEM = registerBlockItem("barrier", BARRIER);
    public static final RegistryObject<Item> PIT_LANE_ITEM = registerBlockItem("pit_lane", PIT_LANE);
    public static final RegistryObject<Item> PIT_LANE_SLAB_ITEM = registerBlockItem("pit_lane_slab", PIT_LANE_SLAB);
    public static final RegistryObject<Item> PIT_STOP_MARK_ITEM = registerBlockItem("pit_stop_mark", PIT_STOP_MARK);
    public static final RegistryObject<Item> START_FINISH_ITEM = registerBlockItem("start_finish", START_FINISH);
    public static final RegistryObject<Item> CHECKPOINT_ITEM = registerBlockItem("checkpoint", CHECKPOINT);

    private OWRBlocks() {
    }

    public static void register(BusGroup modBus) {
        BLOCKS.register(modBus);
    }

    private static RegistryObject<Block> registerRaceMonitor(String name, RaceMonitorType monitorType) {
        return BLOCKS.register(name, () -> new RaceDirectorBlock(BlockBehaviour.Properties.of()
            .setId(key(name))
            .mapColor(MapColor.METAL)
            .strength(3.5f, 6.0f)
            .requiresCorrectToolForDrops(), monitorType)
        );
    }

    private static RegistryObject<Block> registerRaceLight(String name, RaceLightType type) {
        return BLOCKS.register(name, () -> new RaceLightBlock(BlockBehaviour.Properties.of().setId(key(name)).mapColor(MapColor.METAL)
            .strength(3.5f, 6.0f).requiresCorrectToolForDrops(), type));
    }

    private static RegistryObject<Block> registerCarStation(String name, CarWorkstationType workstationType) {
        return BLOCKS.register(name, () -> new CarAssemblyWorkstationBlock(BlockBehaviour.Properties.of()
            .setId(key(name))
            .mapColor(MapColor.METAL)
            .strength(3.5f, 6.0f)
            .requiresCorrectToolForDrops(), workstationType)
        );
    }

    private static RegistryObject<Block> registerSimpleBlock(String name, MapColor mapColor, float destroyTime, float explosionResistance) {
        return BLOCKS.register(name, () -> new WettableTrackBlock(BlockBehaviour.Properties.of()
            .setId(key(name))
            .mapColor(mapColor)
            .strength(destroyTime, explosionResistance)
            .requiresCorrectToolForDrops())
        );
    }

    private static RegistryObject<Block> registerDrySimpleBlock(String name, MapColor mapColor, float destroyTime, float explosionResistance) {
        return BLOCKS.register(name, () -> new Block(BlockBehaviour.Properties.of()
            .setId(key(name))
            .mapColor(mapColor)
            .strength(destroyTime, explosionResistance)
            .requiresCorrectToolForDrops())
        );
    }

    private static RegistryObject<Block> registerSlabBlock(String name, MapColor mapColor, float destroyTime, float explosionResistance) {
        return BLOCKS.register(name, () -> new WettableTrackSlabBlock(BlockBehaviour.Properties.of()
            .setId(key(name))
            .mapColor(mapColor)
            .strength(destroyTime, explosionResistance)
            .requiresCorrectToolForDrops())
        );
    }

    private static RegistryObject<Block> registerDirectionalBlock(String name, MapColor mapColor, float destroyTime, float explosionResistance) {
        return BLOCKS.register(name, () -> new DirectionalTrackBlock(BlockBehaviour.Properties.of()
            .setId(key(name))
            .mapColor(mapColor)
            .strength(destroyTime, explosionResistance)
            .requiresCorrectToolForDrops())
        );
    }

    private static RegistryObject<Block> registerLapMarker(String name, boolean startFinish, MapColor mapColor) {
        return BLOCKS.register(name, () -> new LapMarkerBlock(startFinish, BlockBehaviour.Properties.of()
            .setId(key(name))
            .mapColor(mapColor)
            .strength(2.0f, 6.0f)
            .requiresCorrectToolForDrops())
        );
    }

    private static RegistryObject<Item> registerBlockItem(String name, RegistryObject<? extends Block> block) {
        return OWRItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().setId(OWRItems.key(name))));
    }

    private static ResourceKey<Block> key(String name) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, name));
    }
}
