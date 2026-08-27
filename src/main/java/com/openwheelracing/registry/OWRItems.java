package com.openwheelracing.registry;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.item.PrototypeCarItem;
import com.openwheelracing.content.item.SafetyCarItem;
import com.openwheelracing.content.item.TyreItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class OWRItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, OpenwheelRacing.MODID);

    public static final DeferredHolder<Item, Item> CARBON_FIBER = registerSimple("carbon_fiber");
    public static final DeferredHolder<Item, Item> RUBBER = registerSimple("rubber");
    public static final DeferredHolder<Item, Item> CRUDE_RUBBER = registerSimple("crude_rubber");
    public static final DeferredHolder<Item, Item> CRUDE_OIL_BUCKET = ITEMS.register("crude_oil_bucket",
        () -> new BucketItem(OWRFluids.CRUDE_OIL.get(), new Item.Properties().setId(key("crude_oil_bucket")).craftRemainder(Items.BUCKET).stacksTo(1))
    );
    public static final DeferredHolder<Item, Item> GAS = registerSimple("gas");
    public static final DeferredHolder<Item, Item> PETROL_CAN = registerSimple("petrol_can");
    public static final DeferredHolder<Item, Item> DIESEL_CAN = registerSimple("diesel_can");
    public static final DeferredHolder<Item, Item> ASPHALT_BINDER = registerSimple("asphalt_binder");
    public static final DeferredHolder<Item, Item> PLASTIC = registerSimple("plastic");
    public static final DeferredHolder<Item, Item> RACING_ELECTRONICS = registerSimple("racing_electronics");
    public static final DeferredHolder<Item, Item> CHASSIS = registerSimple("chassis");
    public static final DeferredHolder<Item, Item> ENGINE = registerSimple("engine");
    public static final DeferredHolder<Item, Item> JACK = ITEMS.register("jack",
        () -> new Item(new Item.Properties().setId(key("jack")).stacksTo(1))
    );
    public static final DeferredHolder<Item, Item> TIRES = ITEMS.register("tires",
        () -> new TyreItem(new Item.Properties().setId(key("tires")))
    );
    public static final DeferredHolder<Item, Item> AERO_KIT = registerSimple("aero_kit");
    public static final DeferredHolder<Item, Item> FRONT_WING = registerSimple("front_wing");
    public static final DeferredHolder<Item, Item> REAR_WING = registerSimple("rear_wing");
    public static final DeferredHolder<Item, Item> GEARBOX = registerSimple("gearbox");
    public static final DeferredHolder<Item, Item> STEERING_CONTROLS = registerSimple("steering_controls");
    public static final DeferredHolder<Item, Item> PROTOTYPE_CAR_SPAWN = ITEMS.register("prototype_car_spawn",
        () -> new PrototypeCarItem(new Item.Properties().setId(key("prototype_car_spawn")))
    );
    public static final DeferredHolder<Item, Item> SAFETY_CAR_SPAWN = ITEMS.register("safety_car_spawn",
        () -> new SafetyCarItem(new Item.Properties().setId(key("safety_car_spawn")))
    );

    private OWRItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    private static DeferredHolder<Item, Item> registerSimple(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().setId(key(name))));
    }

    public static ResourceKey<Item> key(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, name));
    }
}
