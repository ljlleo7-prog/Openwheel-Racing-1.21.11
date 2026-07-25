package com.openwheelracing.registry;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.menu.CarAssemblyMenu;
import com.openwheelracing.content.menu.RaceDirectorMenu;
import com.openwheelracing.content.menu.RefineryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class OWRMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, OpenwheelRacing.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<CarAssemblyMenu>> CAR_ASSEMBLY = MENUS.register("car_assembly",
        () -> IMenuTypeExtension.create(CarAssemblyMenu::new)
    );

    public static final DeferredHolder<MenuType<?>, MenuType<RefineryMenu>> REFINERY = MENUS.register("refinery",
        () -> IMenuTypeExtension.create(RefineryMenu::new)
    );

    public static final DeferredHolder<MenuType<?>, MenuType<RaceDirectorMenu>> RACE_DIRECTOR = MENUS.register("race_director",
        () -> IMenuTypeExtension.create(RaceDirectorMenu::new)
    );

    private OWRMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
