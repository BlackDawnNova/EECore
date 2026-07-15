package com.endlessepoch.core.api.recipe;

import com.endlessepoch.core.registry.EECoreRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Boiler recipe: fluid input → fluid output + duration.
 * Fuel is handled by the machine (any burnable item), not specified in the recipe.
 * <p>
 * 锅炉配方：流体输入→流体输出。燃料由机器自行处理。
 */
public class BoilerRecipe implements Recipe<SingleRecipeInput> {

    private final String group;
    private final FluidStack inputFluid;
    private final FluidStack outputFluid;
    private final int duration;

    public BoilerRecipe(String group, FluidStack inputFluid, FluidStack outputFluid, int duration) {
        this.group = group;
        this.inputFluid = inputFluid;
        this.outputFluid = outputFluid;
        this.duration = duration;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) { return false; }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return true; }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override public String getGroup() { return group; }
    public FluidStack getInputFluid() { return inputFluid; }
    public FluidStack getOutputFluid() { return outputFluid; }
    public int getDuration() { return duration; }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return EECoreRecipeTypes.BOILER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() { return EECoreRecipeTypes.BOILER.get(); }
}
