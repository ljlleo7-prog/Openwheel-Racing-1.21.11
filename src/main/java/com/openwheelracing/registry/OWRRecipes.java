package com.openwheelracing.registry;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.recipe.CarAssemblyRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class OWRRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, OpenwheelRacing.MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, OpenwheelRacing.MODID);

    public static final RegistryObject<RecipeSerializer<CarAssemblyRecipe>> CAR_ASSEMBLY_SERIALIZER = RECIPE_SERIALIZERS.register("car_assembly", CarAssemblyRecipe.Serializer::new);
    public static final RegistryObject<RecipeType<CarAssemblyRecipe>> CAR_ASSEMBLY_TYPE = RECIPE_TYPES.register("car_assembly",
        () -> RecipeType.simple(Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "car_assembly"))
    );

    private OWRRecipes() {
    }

    public static void register(BusGroup modBus) {
        RECIPE_SERIALIZERS.register(modBus);
        RECIPE_TYPES.register(modBus);
    }
}
