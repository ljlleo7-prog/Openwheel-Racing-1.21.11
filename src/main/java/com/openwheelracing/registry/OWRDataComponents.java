package com.openwheelracing.registry;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.car.CarLiveryColors;
import com.openwheelracing.content.car.CarLiveryTexture;
import com.openwheelracing.content.car.PrototypeCarSetup;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class OWRDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, OpenwheelRacing.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PrototypeCarSetup>> CAR_SETUP = DATA_COMPONENTS.register("car_setup",
        () -> DataComponentType.<PrototypeCarSetup>builder()
            .persistent(PrototypeCarSetup.CODEC)
            .networkSynchronized(PrototypeCarSetup.STREAM_CODEC)
            .build()
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CAR_DAMAGE = DATA_COMPONENTS.register("car_damage",
        () -> DataComponentType.<Integer>builder()
            .persistent(Codec.intRange(0, 100))
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
            .build()
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TYRE_WEAR = DATA_COMPONENTS.register("tyre_wear",
        () -> DataComponentType.<Integer>builder()
            .persistent(Codec.intRange(0, 100))
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
            .build()
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CAR_LIVERY = DATA_COMPONENTS.register("car_livery",
        () -> DataComponentType.<Integer>builder()
            .persistent(Codec.intRange(0, 9))
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
            .build()
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CarLiveryColors>> CAR_LIVERY_COLORS = DATA_COMPONENTS.register("car_livery_colors",
        () -> DataComponentType.<CarLiveryColors>builder()
            .persistent(CarLiveryColors.CODEC)
            .networkSynchronized(CarLiveryColors.STREAM_CODEC)
            .build()
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CarLiveryTexture>> CAR_LIVERY_TEXTURE = DATA_COMPONENTS.register("car_livery_texture",
        () -> DataComponentType.<CarLiveryTexture>builder()
            .persistent(CarLiveryTexture.CODEC)
            .networkSynchronized(CarLiveryTexture.STREAM_CODEC)
            .build()
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ERS_MODE = DATA_COMPONENTS.register("ers_mode",
        () -> DataComponentType.<Integer>builder()
            .persistent(Codec.intRange(0, 2))
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
            .build()
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ERS_ENERGY_PERCENT = DATA_COMPONENTS.register("ers_energy_percent",
        () -> DataComponentType.<Integer>builder()
            .persistent(Codec.intRange(0, 100))
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
            .build()
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TYRE_COMPOUND = DATA_COMPONENTS.register("tyre_compound",
        () -> DataComponentType.<Integer>builder()
            .persistent(Codec.intRange(0, 4))
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
            .build()
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TYRE_REMAINING_PERCENT = DATA_COMPONENTS.register("tyre_remaining_percent",
        () -> DataComponentType.<Integer>builder()
            .persistent(Codec.intRange(0, 100))
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
            .build()
    );

    private OWRDataComponents() {
    }

    public static void register(IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
    }
}
