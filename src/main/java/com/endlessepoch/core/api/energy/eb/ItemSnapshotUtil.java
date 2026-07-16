package com.endlessepoch.core.api.energy.eb;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** ItemStack → long triple (itemId, count, nbtHash). / 物品→long三元组。 */
public final class ItemSnapshotUtil {
    private ItemSnapshotUtil() {}

    public static long itemId(ItemStack stack) {
        return stack.isEmpty() ? 0L : (long) BuiltInRegistries.ITEM.getId(stack.getItem());
    }

    public static long count(ItemStack stack) {
        return stack.getCount();
    }

    public static long nbtHash(ItemStack stack) {
        var patch = stack.getComponentsPatch();
        return patch.isEmpty() ? 0L : (long) patch.hashCode();
    }
}
