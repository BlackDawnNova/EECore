package com.endlessepoch.core.api.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Arrays;

/**
 * Immutable, thread-safe recipe data snapshot. All fields are long for EB event pipeline.
 * Constructed on the main thread, read by any background thread — no locks needed.
 * 不可变配方快照，全 long 字段支持 EB 事件管线。主线程构造，后台线程安全读取。
 */
public record RecipeSnapshot(
        long recipeIdHash,      // ResourceLocation hash / 配方ID哈希
        long[] inputItemIds,    // matched ingredient item IDs / 匹配输入物品ID
        long[] outputItemIds,   // result item IDs / 产物ID
        long[] outputCounts,    // result counts / 产物数量
        long energyCost,        // energy per operation (Ω) / 单次能耗
        long durationTicks,     // processing time / 处理耗时
        long voltageValue        // required voltage biginteger value / 需求电压BigInteger值
) {

    /** Create from an ItemStack result list / 从结果列表创建 */
    public static long[] itemIdsFrom(ItemStack... stacks) {
        return Arrays.stream(stacks)
                .mapToLong(s -> s.isEmpty() ? 0L : (long) net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(s.getItem()))
                .toArray();
    }

    public static long[] countsFrom(ItemStack... stacks) {
        return Arrays.stream(stacks).mapToLong(ItemStack::getCount).toArray();
    }

    public static long[] ingredientItemIds(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems())
                .mapToLong(s -> (long) net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(s.getItem()))
                .distinct().toArray();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RecipeSnapshot that)) return false;
        return recipeIdHash == that.recipeIdHash;
    }

    @Override
    public int hashCode() { return Long.hashCode(recipeIdHash); }
}
