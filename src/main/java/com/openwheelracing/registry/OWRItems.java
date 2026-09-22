package com.openwheelracing.registry;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.item.PrototypeCarItem;
import com.openwheelracing.content.item.SafetyCarItem;
import com.openwheelracing.content.item.TyreItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.registries.RegistryObject;

public final class OWRItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, OpenwheelRacing.MODID);

    public static final RegistryObject<Item> CARBON_FIBER = registerSimple("carbon_fiber");
    public static final RegistryObject<Item> RUBBER = registerSimple("rubber");
    public static final RegistryObject<Item> CRUDE_RUBBER = registerSimple("crude_rubber");
    public static final RegistryObject<Item> CRUDE_OIL_BUCKET = ITEMS.register("crude_oil_bucket",
        () -> new BucketItem(OWRFluids.CRUDE_OIL.get(), new Item.Properties().setId(key("crude_oil_bucket")).craftRemainder(Items.BUCKET).stacksTo(1))
    );
    public static final RegistryObject<Item> GAS = registerSimple("gas");
    public static final RegistryObject<Item> PETROL_CAN = registerSimple("petrol_can");
    public static final RegistryObject<Item> DIESEL_CAN = registerSimple("diesel_can");
    public static final RegistryObject<Item> ASPHALT_BINDER = registerSimple("asphalt_binder");
    public static final RegistryObject<Item> PLASTIC = registerSimple("plastic");
    public static final RegistryObject<Item> RACING_ELECTRONICS = registerSimple("racing_electronics");
    public static final RegistryObject<Item> CHASSIS = registerSimple("chassis");
    public static final RegistryObject<Item> ENGINE = registerSimple("engine");
    public static final RegistryObject<Item> JACK = ITEMS.register("jack",
        () -> new Item(new Item.Properties().setId(key("jack")).stacksTo(1))
    );
    public static final RegistryObject<Item> TIRES = ITEMS.register("tires",
        () -> new TyreItem(new Item.Properties().setId(key("tires")))
    );
    public static final RegistryObject<Item> AERO_KIT = registerSimple("aero_kit");
    public static final RegistryObject<Item> FRONT_WING = registerSimple("front_wing");
    public static final RegistryObject<Item> REAR_WING = registerSimple("rear_wing");
    public static final RegistryObject<Item> GEARBOX = registerSimple("gearbox");
    public static final RegistryObject<Item> STEERING_CONTROLS = registerSimple("steering_controls");
    public static final RegistryObject<Item> PROTOTYPE_CAR_SPAWN = ITEMS.register("prototype_car_spawn",
        () -> new PrototypeCarItem(new Item.Properties().setId(key("prototype_car_spawn")))
    );
    public static final RegistryObject<Item> SAFETY_CAR_SPAWN = ITEMS.register("safety_car_spawn",
        () -> new SafetyCarItem(new Item.Properties().setId(key("safety_car_spawn")))
    );

    private OWRItems() {
    }

    public static void register(BusGroup modBus) {
        ITEMS.register(modBus);
    }

    private static RegistryObject<Item> registerSimple(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().setId(key(name))));
    }

    public static ResourceKey<Item> key(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, name));
    }
}
