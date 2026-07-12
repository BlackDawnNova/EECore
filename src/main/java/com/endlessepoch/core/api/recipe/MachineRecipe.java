package com.endlessepoch.core.api.recipe;

import com.endlessepoch.core.registry.EECoreRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A single-input multi-output machine processing recipe.
 * Defined via JSON under {@code data/eecore/recipe/}.
 * <p>
 * 单输入多输出机器加工配方，通过 JSON 定义。
 */
public class MachineRecipe implements Recipe<SingleRecipeInput> {

    private final String group;
    private final Ingredient ingredient;
    private final List<ItemStack> results;
    private final int processingTime;

    public MachineRecipe(String group, Ingredient ingredient, List<ItemStack> results, int processingTime) {
        this.group = group;
        this.ingredient = ingredient;
        this.results = List.copyOf(results);
        this.processingTime = processingTime;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.getItem(0));
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return results.isEmpty() ? ItemStack.EMPTY : results.getFirst().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return true; }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return results.isEmpty() ? ItemStack.EMPTY : results.getFirst();
    }

    @Override
    public String getGroup() { return group; }

    public Ingredient getIngredient() { return ingredient; }

    public List<ItemStack> getResults() { return results; }

    public int getProcessingTime() { return processingTime; }

    List<ItemStack> results() { return new ArrayList<>(results); }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return EECoreRecipeTypes.MACHINE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() { return EECoreRecipeTypes.MACHINE.get(); }
}
