package com.endlessepoch.core.api.multiblock;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Pluggable visual effect bound to a multiblock machine.
 * Called every frame while the machine is formed.
 * <p>
 * 多方块机器可插拔视觉特效。机器成形后每帧调用。
 */
@FunctionalInterface
public interface IMachineEffect {
    /**
     * @param ps  PoseStack already translated to structure center / 已推到结构中心
     * @param be  The controller block entity / 控制器 BE
     * @param partialTick  frame tick delta / 帧时间差
     */
    void render(PoseStack ps, BlockEntity be, float partialTick);
}
