package com.endlessepoch.core.api.recipe;

import com.endlessepoch.core.api.tier.VoltageTier;
import com.endlessepoch.core.registry.EECoreRecipeTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;

/**
 * EECore built-in machine recipe for internal testing (type={@code eecore:machine}).
 * Addon mods extend {@link AbstractMachineRecipe} to define their own recipe types.
 * <p>
 * EECore 内部测试用机器配方 (type=eecore:machine)。
 * 附属Mod 继承 AbstractMachineRecipe 定义自己的配方类型。
 */
public class MachineRecipe extends AbstractMachineRecipe {

    public MachineRecipe(String group, Ingredient ingredient, List<ItemStack> results, int processingTime,
                         VoltageTier requiredTier, double maxHeat, long energyPerTick, int maxParallel, int circuit) {
        super(group, ingredient, results, processingTime, requiredTier, maxHeat, energyPerTick, maxParallel, circuit);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return EECoreRecipeTypes.MACHINE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() { return EECoreRecipeTypes.MACHINE.get(); }
}
