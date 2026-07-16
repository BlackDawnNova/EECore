package com.endlessepoch.core.api.recipe;

import com.endlessepoch.core.api.tier.VoltageTier;
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
    private final VoltageTier requiredTier;
    private final double maxHeat; // heat ceiling for this recipe / 该配方热量天花板
    private final long energyPerTick; // Ω per tick, 0 = no energy cost / 每 tick 能耗，0 表示不耗电
    private final int maxParallel; // recipe-level parallel cap / 配方级并行上限

    public MachineRecipe(String group, Ingredient ingredient, List<ItemStack> results, int processingTime,
                         VoltageTier requiredTier, double maxHeat, long energyPerTick, int maxParallel) {
        this.group = group;
        this.ingredient = ingredient;
        this.results = List.copyOf(results);
        this.processingTime = processingTime;
        this.requiredTier = requiredTier;
        this.maxHeat = maxHeat;
        this.energyPerTick = energyPerTick;
        this.maxParallel = maxParallel;
    }

    /** Heat ceiling — heat never exceeds this. / 热量天花板 */
    public double getMaxHeat() { return maxHeat; }

    /** Minimum voltage required, or ELV if unset. / 最低需求电压，未设置时默认 ELV。 */
    public VoltageTier getRequiredTier() { return requiredTier; }

    /** Ω consumed per tick at base tier, 0 = free. / 基础电压下每 tick 消耗 Ω，0 表示免费 */
    public long getEnergyPerTick() { return energyPerTick; }

    /** Recipe-level max parallel operations. / 配方级最大并行数 */
    public int getMaxParallel() { return maxParallel; }

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
