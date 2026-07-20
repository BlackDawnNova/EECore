package com.endlessepoch.core.api.recipe;

import com.endlessepoch.core.api.tier.VoltageTier;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base for single-input multi-output machine recipes usable by the EB batch pipeline.
 * Addon mods extend this, override {@link #getType()} and {@link #getSerializer()},
 * and register their type via {@link RecipeSnapshotCache#register}.
 * <p>
 * 单输入多输出机器配方抽象基类。附属Mod继承此类，覆写 getType/getSerializer，
 * 并通过 RecipeSnapshotCache.register 注册即可接入 EB 批量管线。
 */
public abstract class AbstractMachineRecipe implements Recipe<SingleRecipeInput> {

    private final String group;
    private final Ingredient ingredient;
    private final List<ItemStack> results;
    private final int processingTime;
    private final VoltageTier requiredTier;
    private final double maxHeat;
    private final long energyPerTick;
    private final int maxParallel;
    private final int circuit;

    protected AbstractMachineRecipe(String group, Ingredient ingredient, List<ItemStack> results,
                                    int processingTime, VoltageTier requiredTier, double maxHeat,
                                    long energyPerTick, int maxParallel, int circuit) {
        this.group = group;
        this.ingredient = ingredient;
        this.results = List.copyOf(results);
        this.processingTime = processingTime;
        this.requiredTier = requiredTier;
        this.maxHeat = maxHeat;
        this.energyPerTick = energyPerTick;
        this.maxParallel = maxParallel;
        this.circuit = Math.max(0, circuit);
    }

    public double getMaxHeat() { return maxHeat; }
    public VoltageTier getRequiredTier() { return requiredTier; }
    public long getEnergyPerTick() { return energyPerTick; }
    public int getMaxParallel() { return maxParallel; }
    public int getCircuit() { return circuit; }
    public Ingredient getIngredient() { return ingredient; }
    public List<ItemStack> getResults() { return results; }
    public int getProcessingTime() { return processingTime; }
    List<ItemStack> results() { return new ArrayList<>(results); }

    @Override
    public String getGroup() { return group; }

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
}
