package com.endlessepoch.core.api.machine;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;

/**
 * Machine type definition — binds recipe type + icon + name + processor.
 * Registered via {@link MachineTypeRegistry#register}.
 * <p>
 * 机器类型定义 — 绑定配方类型 + 图标 + 名称 + 处理器。
 */
public record MachineType(
        ResourceLocation id,
        RecipeType<?> recipeType,
        Item iconItem,
        String translationKey,
        IRecipeProcessor processor
) {
    public static MachineType of(String modId, String path, RecipeType<?> recipeType,
                                  ItemLike icon, String key, IRecipeProcessor processor) {
        return new MachineType(ResourceLocation.fromNamespaceAndPath(modId, path),
                recipeType, icon.asItem(), key, processor);
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }
}
