package com.openwheelracing.registry;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.car.CarLivery;
import com.openwheelracing.content.car.PrototypeCarSetup;
import com.openwheelracing.content.item.PrototypeCarItem;
import com.openwheelracing.content.item.SafetyCarItem;
import com.openwheelracing.content.item.TyreItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class OWRCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OpenwheelRacing.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> OPENWHEEL_RACING = CREATIVE_MODE_TABS.register("openwheel_racing", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.openwheelracing.openwheel_racing"))
        .icon(PrototypeCarItem::createWithDefaultSetup)
        .displayItems((parameters, output) -> {
            output.accept(OWRBlocks.CAR_ASSEMBLY_WORKSTATION_ITEM.get());
            output.accept(OWRBlocks.CAR_CONSTRUCTION_STATION_ITEM.get());
            output.accept(OWRBlocks.CAR_SETUP_STATION_ITEM.get());
            output.accept(OWRBlocks.CAR_LIVERY_STATION_ITEM.get());
            output.accept(OWRBlocks.CAR_PARTS_REPLACEMENT_STATION_ITEM.get());
            output.accept(OWRBlocks.REFINERY_ITEM.get());
            output.accept(OWRBlocks.RACE_DIRECTOR_ITEM.get());
            output.accept(OWRBlocks.RACE_BOARD_TERMINAL_ITEM.get());
            output.accept(OWRBlocks.TEAM_TERMINAL_ITEM.get());
            output.accept(OWRBlocks.ASPHALT_TRACK_ITEM.get());
            output.accept(OWRBlocks.ASPHALT_TRACK_SLAB_ITEM.get());
            output.accept(OWRBlocks.KERB_ITEM.get());
            output.accept(OWRBlocks.BARRIER_ITEM.get());
            output.accept(OWRBlocks.PIT_LANE_ITEM.get());
            output.accept(OWRBlocks.PIT_LANE_SLAB_ITEM.get());
            output.accept(OWRBlocks.PIT_STOP_MARK_ITEM.get());
            output.accept(OWRBlocks.START_FINISH_ITEM.get());
            output.accept(OWRBlocks.CHECKPOINT_ITEM.get());
            output.accept(OWRItems.CARBON_FIBER.get());
            output.accept(OWRItems.CRUDE_OIL_BUCKET.get());
            output.accept(OWRItems.GAS.get());
            output.accept(OWRItems.PETROL_CAN.get());
            output.accept(OWRItems.DIESEL_CAN.get());
            output.accept(OWRItems.CRUDE_RUBBER.get());
            output.accept(OWRItems.RUBBER.get());
            output.accept(OWRItems.ASPHALT_BINDER.get());
            output.accept(OWRItems.PLASTIC.get());
            output.accept(OWRItems.RACING_ELECTRONICS.get());
            output.accept(OWRItems.CHASSIS.get());
            output.accept(OWRItems.ENGINE.get());
            output.accept(OWRItems.JACK.get());
            for (int compound = 0; compound <= 4; compound++) {
                output.accept(TyreItem.create(compound));
            }
            output.accept(TyreItem.create(PrototypeCarSetup.DEFAULT.grip(), com.openwheelracing.content.car.TyreType.INTERMEDIATE, 1, 100));
            output.accept(TyreItem.create(PrototypeCarSetup.DEFAULT.grip(), com.openwheelracing.content.car.TyreType.WET, 1, 100));
            output.accept(OWRItems.AERO_KIT.get());
            output.accept(OWRItems.FRONT_WING.get());
            output.accept(OWRItems.REAR_WING.get());
            output.accept(OWRItems.GEARBOX.get());
            output.accept(OWRItems.STEERING_CONTROLS.get());
            for (int livery = 0; livery < CarLivery.count(); livery++) {
                output.accept(PrototypeCarItem.create(PrototypeCarSetup.DEFAULT, 0.0f, 0.0f, livery));
            }
            output.accept(SafetyCarItem.createDefault());
        })
        .build()
    );

    private OWRCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        CREATIVE_MODE_TABS.register(modBus);
    }
}
