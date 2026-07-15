package com.endlessepoch.core.api.machine;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Pluggable recipe processor. One implementation per machine type.
 * Works for both single-block machines and multiblock controllers.
 * <p>
 * 可插拔配方处理器。单方块/多方块机器通用。
 */
public interface IRecipeProcessor {
    void tick(BlockEntity be);
}
