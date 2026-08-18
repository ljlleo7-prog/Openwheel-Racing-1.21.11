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
import com.openwheelracing.content.block.entity.CarWorkstationType;
import com.openwheelracing.content.block.entity.RaceMonitorType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class OWRBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, OpenwheelRacing.MODID);

    public static final DeferredHolder<Block, Block> CAR_ASSEMBLY_WORKSTATION = registerCarStation("car_assembly_workstation", CarWorkstationType.LEGACY);
    public static final DeferredHolder<Block, Block> CAR_CONSTRUCTION_STATION = registerCarStation("car_construction_station", CarWorkstationType.CONSTRUCTION);
    public static final DeferredHolder<Block, Block> CAR_SETUP_STATION = registerCarStation("car_setup_station", CarWorkstationType.SETUP);
    public static final DeferredHolder<Block, Block> CAR_LIVERY_STATION = registerCarStation("car_livery_station", CarWorkstationType.LIVERY);
    public static final DeferredHolder<Block, Block> CAR_PARTS_REPLACEMENT_STATION = BLOCKS.register("car_parts_replacement_station",
        () -> new CarPartsReplacementWorkstationBlock(BlockBehaviour.Properties.of()
            .setId(key("car_parts_replacement_station"))
            .mapColor(MapColor.METAL)
            .strength(3.5f, 6.0f)
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> REFINERY = BLOCKS.register("refinery",
        () -> new RefineryBlock(BlockBehaviour.Properties.of()
            .setId(key("refinery"))
            .mapColor(MapColor.METAL)
            .strength(3.5f, 6.0f)
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> RACE_DIRECTOR = registerRaceMonitor("race_director", RaceMonitorType.DIRECTOR);
    public static final DeferredHolder<Block, Block> RACE_BOARD_TERMINAL = registerRaceMonitor("race_board_terminal", RaceMonitorType.BOARD);
    public static final DeferredHolder<Block, Block> TEAM_TERMINAL = registerRaceMonitor("team_terminal", RaceMonitorType.TEAM);

    public static final DeferredHolder<Block, LiquidBlock> CRUDE_OIL_DEPOSIT = BLOCKS.register("crude_oil_deposit",
        () -> new CrudeOilBlock(BlockBehaviour.Properties.of()
            .setId(key("crude_oil_deposit"))
            .mapColor(MapColor.COLOR_BLACK)
            .noCollision()
            .strength(100.0f)
            .noLootTable())
    );

    public static final DeferredHolder<Block, Block> ASPHALT_TRACK = registerSimpleBlock("asphalt_track", MapColor.COLOR_BLACK, 2.4f, 6.0f);
    public static final DeferredHolder<Block, Block> ASPHALT_TRACK_SLAB = registerSlabBlock("asphalt_track_slab", MapColor.COLOR_BLACK, 2.4f, 6.0f);
    public static final DeferredHolder<Block, Block> KERB = registerDirectionalBlock("kerb", MapColor.COLOR_RED, 2.0f, 6.0f);
    public static final DeferredHolder<Block, Block> BARRIER = registerDrySimpleBlock("barrier", MapColor.METAL, 4.0f, 8.0f);
    public static final DeferredHolder<Block, Block> PIT_LANE = registerSimpleBlock("pit_lane", MapColor.COLOR_GRAY, 2.4f, 6.0f);
    public static final DeferredHolder<Block, Block> PIT_LANE_SLAB = registerSlabBlock("pit_lane_slab", MapColor.COLOR_GRAY, 2.4f, 6.0f);
    public static final DeferredHolder<Block, Block> PIT_STOP_MARK = registerSimpleBlock("pit_stop_mark", MapColor.COLOR_GRAY, 2.4f, 6.0f);
    public static final DeferredHolder<Block, Block> START_FINISH = registerLapMarker("start_finish", true, MapColor.SNOW);
    public static final DeferredHolder<Block, Block> CHECKPOINT = registerLapMarker("checkpoint", false, MapColor.COLOR_LIGHT_BLUE);

    public static final DeferredHolder<Item, Item> CAR_ASSEMBLY_WORKSTATION_ITEM = registerBlockItem("car_assembly_workstation", CAR_ASSEMBLY_WORKSTATION);
    public static final DeferredHolder<Item, Item> CAR_CONSTRUCTION_STATION_ITEM = registerBlockItem("car_construction_station", CAR_CONSTRUCTION_STATION);
    public static final DeferredHolder<Item, Item> CAR_SETUP_STATION_ITEM = registerBlockItem("car_setup_station", CAR_SETUP_STATION);
    public static final DeferredHolder<Item, Item> CAR_LIVERY_STATION_ITEM = registerBlockItem("car_livery_station", CAR_LIVERY_STATION);
    public static final DeferredHolder<Item, Item> CAR_PARTS_REPLACEMENT_STATION_ITEM = registerBlockItem("car_parts_replacement_station", CAR_PARTS_REPLACEMENT_STATION);
    public static final DeferredHolder<Item, Item> REFINERY_ITEM = registerBlockItem("refinery", REFINERY);
    public static final DeferredHolder<Item, Item> RACE_DIRECTOR_ITEM = registerBlockItem("race_director", RACE_DIRECTOR);
    public static final DeferredHolder<Item, Item> RACE_BOARD_TERMINAL_ITEM = registerBlockItem("race_board_terminal", RACE_BOARD_TERMINAL);
    public static final DeferredHolder<Item, Item> TEAM_TERMINAL_ITEM = registerBlockItem("team_terminal", TEAM_TERMINAL);
    public static final DeferredHolder<Item, Item> ASPHALT_TRACK_ITEM = registerBlockItem("asphalt_track", ASPHALT_TRACK);
    public static final DeferredHolder<Item, Item> ASPHALT_TRACK_SLAB_ITEM = registerBlockItem("asphalt_track_slab", ASPHALT_TRACK_SLAB);
    public static final DeferredHolder<Item, Item> KERB_ITEM = registerBlockItem("kerb", KERB);
    public static final DeferredHolder<Item, Item> BARRIER_ITEM = registerBlockItem("barrier", BARRIER);
    public static final DeferredHolder<Item, Item> PIT_LANE_ITEM = registerBlockItem("pit_lane", PIT_LANE);
    public static final DeferredHolder<Item, Item> PIT_LANE_SLAB_ITEM = registerBlockItem("pit_lane_slab", PIT_LANE_SLAB);
    public static final DeferredHolder<Item, Item> PIT_STOP_MARK_ITEM = registerBlockItem("pit_stop_mark", PIT_STOP_MARK);
    public static final DeferredHolder<Item, Item> START_FINISH_ITEM = registerBlockItem("start_finish", START_FINISH);
    public static final DeferredHolder<Item, Item> CHECKPOINT_ITEM = registerBlockItem("checkpoint", CHECKPOINT);

    private OWRBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }

    private static DeferredHolder<Block, Block> registerRaceMonitor(String name, RaceMonitorType monitorType) {
        return BLOCKS.register(name, () -> new RaceDirectorBlock(BlockBehaviour.Properties.of()
            .setId(key(name))
            .mapColor(MapColor.METAL)
            .strength(3.5f, 6.0f)
            .requiresCorrectToolForDrops(), monitorType)
        );
    }

    private static DeferredHolder<Block, Block> registerCarStation(String name, CarWorkstationType workstationType) {
        return BLOCKS.register(name, () -> new CarAssemblyWorkstationBlock(BlockBehaviour.Properties.of()
            .setId(key(name))
            .mapColor(MapColor.METAL)
            .strength(3.5f, 6.0f)
            .requiresCorrectToolForDrops(), workstationType)
        );
    }

    private static DeferredHolder<Block, Block> registerSimpleBlock(String name, MapColor mapColor, float destroyTime, float explosionResistance) {
        return BLOCKS.register(name, () -> new WettableTrackBlock(BlockBehaviour.Properties.of()
            .setId(key(name))
            .mapColor(mapColor)
            .strength(destroyTime, explosionResistance)
            .requiresCorrectToolForDrops())
        );
    }

    private static DeferredHolder<Block, Block> registerDrySimpleBlock(String name, MapColor mapColor, float destroyTime, float explosionResistance) {
        return BLOCKS.register(name, () -> new Block(BlockBehaviour.Properties.of()
            .setId(key(name))
            .mapColor(mapColor)
            .strength(destroyTime, explosionResistance)
            .requiresCorrectToolForDrops())
        );
    }

    private static DeferredHolder<Block, Block> registerSlabBlock(String name, MapColor mapColor, float destroyTime, float explosionResistance) {
        return BLOCKS.register(name, () -> new WettableTrackSlabBlock(BlockBehaviour.Properties.of()
            .setId(key(name))
            .mapColor(mapColor)
            .strength(destroyTime, explosionResistance)
            .requiresCorrectToolForDrops())
        );
    }

    private static DeferredHolder<Block, Block> registerDirectionalBlock(String name, MapColor mapColor, float destroyTime, float explosionResistance) {
        return BLOCKS.register(name, () -> new DirectionalTrackBlock(BlockBehaviour.Properties.of()
            .setId(key(name))
            .mapColor(mapColor)
            .strength(destroyTime, explosionResistance)
            .requiresCorrectToolForDrops())
        );
    }

    private static DeferredHolder<Block, Block> registerLapMarker(String name, boolean startFinish, MapColor mapColor) {
        return BLOCKS.register(name, () -> new LapMarkerBlock(startFinish, BlockBehaviour.Properties.of()
            .setId(key(name))
            .mapColor(mapColor)
            .strength(2.0f, 6.0f)
            .requiresCorrectToolForDrops())
        );
    }

    private static DeferredHolder<Item, Item> registerBlockItem(String name, DeferredHolder<Block, ? extends Block> block) {
        return OWRItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().setId(OWRItems.key(name))));
    }

    private static ResourceKey<Block> key(String name) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, name));
    }
}
