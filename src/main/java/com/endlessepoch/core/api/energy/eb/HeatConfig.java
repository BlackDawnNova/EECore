package com.endlessepoch.core.api.energy.eb;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable heat profile for a recipe type.
 * Only {@code maxHeat} is stored here — heatUpRate and coolDownRate are global Config values.
 * <p>
 * 配方热量配置。只存 maxHeat，heatUpRate/coolDownRate 从全局 Config 读取。
 *
 * @param recipeId     target recipe identifier / 目标配方 ID
 * @param baseDuration base processing time in ticks / 基础加工时间(tick)
 * @param maxHeat      heat ceiling — current heat cannot exceed this / 最大热度上限
 */
public record HeatConfig(
        ResourceLocation recipeId,
        int baseDuration,
        double maxHeat
) {}
