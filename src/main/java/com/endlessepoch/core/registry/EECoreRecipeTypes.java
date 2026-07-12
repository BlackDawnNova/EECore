package com.endlessepoch.core.registry;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.recipe.MachineRecipe;
import com.endlessepoch.core.api.recipe.MachineRecipeSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Custom recipe type and serializer registrations.
 * <p>
 * 自定义配方类型和序列化器注册。
 */
public class EECoreRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, EECore.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, EECore.MOD_ID);

    public static final Supplier<RecipeType<MachineRecipe>> MACHINE =
            RECIPE_TYPES.register("machine",
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "machine")));
    public static final Supplier<RecipeSerializer<MachineRecipe>> MACHINE_SERIALIZER =
            RECIPE_SERIALIZERS.register("machine", MachineRecipeSerializer::new);
}
