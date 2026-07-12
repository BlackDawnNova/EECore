package com.endlessepoch.core.api.machine;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;

/**
 * Machine processing mode mapped to a vanilla recipe type.
 * 机器处理模式，映射到原版配方类型。
 */
public record MachineProfile(
        ResourceLocation id,
        RecipeType<?> recipeType,
        Item iconItem,
        String translationKey
) {
    public static MachineProfile of(String modId, String path, RecipeType<?> recipeType,
                                     ItemLike icon, String key) {
        return new MachineProfile(
                ResourceLocation.fromNamespaceAndPath(modId, path),
                recipeType, icon.asItem(), key);
    }

    /** Get localized display name. / 获取本地化名称。 */
    public Component displayName() {
        return Component.translatable(translationKey);
    }
}
