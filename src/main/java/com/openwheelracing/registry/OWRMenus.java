package com.openwheelracing.registry;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.block.entity.RaceMonitorType;
import com.openwheelracing.content.menu.CarAssemblyMenu;
import com.openwheelracing.content.menu.CarPartsReplacementMenu;
import com.openwheelracing.content.menu.RaceDirectorMenu;
import com.openwheelracing.content.menu.RefineryMenu;
import com.openwheelracing.content.menu.RaceLightMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class OWRMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, OpenwheelRacing.MODID);

    public static final RegistryObject<MenuType<CarAssemblyMenu>> CAR_ASSEMBLY = MENUS.register("car_assembly",
        () -> new MenuType<>((IContainerFactory<CarAssemblyMenu>) CarAssemblyMenu::new, FeatureFlags.DEFAULT_FLAGS)
    );

    public static final RegistryObject<MenuType<CarPartsReplacementMenu>> CAR_PARTS_REPLACEMENT = MENUS.register("car_parts_replacement",
        () -> new MenuType<>((IContainerFactory<CarPartsReplacementMenu>) CarPartsReplacementMenu::new, FeatureFlags.DEFAULT_FLAGS)
    );

    public static final RegistryObject<MenuType<RefineryMenu>> REFINERY = MENUS.register("refinery",
        () -> new MenuType<>((IContainerFactory<RefineryMenu>) RefineryMenu::new, FeatureFlags.DEFAULT_FLAGS)
    );

    public static final RegistryObject<MenuType<RaceDirectorMenu>> RACE_DIRECTOR = MENUS.register("race_director",
        () -> new MenuType<>((IContainerFactory<RaceDirectorMenu>) (containerId, playerInventory, extraData) -> new RaceDirectorMenu(containerId, playerInventory, RaceMonitorType.DIRECTOR), FeatureFlags.DEFAULT_FLAGS)
    );

    public static final RegistryObject<MenuType<RaceDirectorMenu>> RACE_BOARD_TERMINAL = MENUS.register("race_board_terminal",
        () -> new MenuType<>((IContainerFactory<RaceDirectorMenu>) (containerId, playerInventory, extraData) -> new RaceDirectorMenu(containerId, playerInventory, RaceMonitorType.BOARD), FeatureFlags.DEFAULT_FLAGS)
    );

    public static final RegistryObject<MenuType<RaceDirectorMenu>> TEAM_TERMINAL = MENUS.register("team_terminal",
        () -> new MenuType<>((IContainerFactory<RaceDirectorMenu>) (containerId, playerInventory, extraData) -> new RaceDirectorMenu(containerId, playerInventory, RaceMonitorType.TEAM), FeatureFlags.DEFAULT_FLAGS)
    );
    public static final RegistryObject<MenuType<RaceLightMenu>> RACE_LIGHT = MENUS.register("race_light",
        () -> new MenuType<>((IContainerFactory<RaceLightMenu>) RaceLightMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static RegistryObject<MenuType<RaceDirectorMenu>> typeFor(RaceMonitorType monitorType) {
        return switch (monitorType) {
            case DIRECTOR -> RACE_DIRECTOR;
            case BOARD -> RACE_BOARD_TERMINAL;
            case TEAM -> TEAM_TERMINAL;
        };
    }

    private OWRMenus() {
    }

    public static void register(BusGroup modBus) {
        MENUS.register(modBus);
    }
}
