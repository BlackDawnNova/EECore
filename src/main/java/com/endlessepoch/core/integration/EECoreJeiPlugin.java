package com.endlessepoch.core.integration;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.recipe.BoilerRecipe;
import com.endlessepoch.core.api.recipe.MachineRecipe;
import com.endlessepoch.core.registry.EECoreRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

@JeiPlugin
public class EECoreJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration r) {
        var helper = r.getJeiHelpers().getGuiHelper();
        r.addRecipeCategories(new MachineRecipeCategory(helper));
        r.addRecipeCategories(new BoilerRecipeCategory(helper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration r) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;

        List<RecipeHolder<MachineRecipe>> machineHolders =
                level.getRecipeManager().getAllRecipesFor(EECoreRecipeTypes.MACHINE.get());
        r.addRecipes(MachineRecipeCategory.TYPE,
                machineHolders.stream().map(RecipeHolder::value).toList());

        List<RecipeHolder<BoilerRecipe>> boilerHolders =
                level.getRecipeManager().getAllRecipesFor(EECoreRecipeTypes.BOILER.get());
        r.addRecipes(BoilerRecipeCategory.TYPE,
                boilerHolders.stream().map(RecipeHolder::value).toList());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration r) {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "creative_test"));
        var stack = new ItemStack(item);

        r.addRecipeCatalyst(stack, MachineRecipeCategory.TYPE);
        r.addRecipeCatalyst(stack, BoilerRecipeCategory.TYPE);
    }

    @Override
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration r) {
        // Creative bus phantom slots accept JEI ghost drags / 创造总线幻影槽接收 JEI 拖拽
        r.addGhostIngredientHandler(com.endlessepoch.core.screen.BusScreen.class, new CreativeBusGhostHandler());
        // Creative fluid hatch tanks accept fluid/bucket drags / 创造流体仓罐位接收流体/桶拖拽
        r.addGhostIngredientHandler(com.endlessepoch.core.screen.HatchScreen.class, new CreativeFluidGhostHandler());
    }
}
