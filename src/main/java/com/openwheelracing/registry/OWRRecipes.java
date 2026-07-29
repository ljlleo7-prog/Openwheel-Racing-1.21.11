package com.openwheelracing.registry;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.recipe.CarAssemblyRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class OWRRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, OpenwheelRacing.MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, OpenwheelRacing.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CarAssemblyRecipe>> CAR_ASSEMBLY_SERIALIZER = RECIPE_SERIALIZERS.register("car_assembly", CarAssemblyRecipe.Serializer::new);
    public static final DeferredHolder<RecipeType<?>, RecipeType<CarAssemblyRecipe>> CAR_ASSEMBLY_TYPE = RECIPE_TYPES.register("car_assembly",
        () -> RecipeType.simple(Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "car_assembly"))
    );

    private OWRRecipes() {
    }

    public static void register(IEventBus modBus) {
        RECIPE_SERIALIZERS.register(modBus);
        RECIPE_TYPES.register(modBus);
    }
}
