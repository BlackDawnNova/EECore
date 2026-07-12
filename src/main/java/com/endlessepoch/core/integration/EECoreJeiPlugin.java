package com.endlessepoch.core.integration;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.machine.MachineProfileRegistry;
import com.endlessepoch.core.api.recipe.MachineRecipe;
import com.endlessepoch.core.registry.EECoreRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * JEI integration: custom machine recipe category + catalysts.
 * JEI 集成：自定义机器配方分类 + 催化剂。
 */
@JeiPlugin
public class EECoreJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration r) {
        r.addRecipeCategories(new MachineRecipeCategory(
                r.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration r) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;

        List<RecipeHolder<MachineRecipe>> holders =
                level.getRecipeManager().getAllRecipesFor(EECoreRecipeTypes.MACHINE.get());
        List<MachineRecipe> recipes = holders.stream()
                .map(RecipeHolder::value)
                .toList();
        r.addRecipes(MachineRecipeCategory.TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration r) {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "creative_test"));
        var stack = new ItemStack(item);

        for (var profile : MachineProfileRegistry.getAll()) {
            var rt = profile.recipeType();
            if (rt == net.minecraft.world.item.crafting.RecipeType.SMELTING)
                r.addRecipeCatalyst(stack, RecipeTypes.SMELTING);
            if (rt == net.minecraft.world.item.crafting.RecipeType.BLASTING)
                r.addRecipeCatalyst(stack, RecipeTypes.BLASTING);
            if (rt == net.minecraft.world.item.crafting.RecipeType.SMOKING)
                r.addRecipeCatalyst(stack, RecipeTypes.SMOKING);
            if (rt == net.minecraft.world.item.crafting.RecipeType.CAMPFIRE_COOKING)
                r.addRecipeCatalyst(stack, RecipeTypes.CAMPFIRE_COOKING);
        }

        r.addRecipeCatalyst(stack, MachineRecipeCategory.TYPE);
    }
}
