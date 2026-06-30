package com.endlessepoch.core.api.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Handles multiblock formation and breakup, including owner stamping.
 * <p>
 * Subclasses/modules can override formation behavior by extending this class.
 * <p>
 * 处理多方块结构的形成与解体，包括所有者标记。
 * <p>
 * 子类/模块可以通过继承此类来覆盖形成行为。
 */
public class MultiBlockFormHandler {

    /**
     * Attempt to form a multiblock around a controller BE.
     * <p>
     * 尝试在控制器方块实体周围形成多方块结构。
     *
     * @param controllerBE the controller block entity / 控制器方块实体
     * @param pattern      the pattern to match / 要匹配的模式
     * @param facing       direction the controller faces / 控制器朝向的方向
     * @param player       the player initiating formation (may be null for auto-recheck) / 发起形成的玩家（自动重新检查时可为 null）
     * @return true if formed successfully / 若成功形成则返回 true
     */
    public static boolean tryForm(BlockEntity controllerBE, MultiBlockPattern pattern,
                                   Direction facing, Player player) {
        if (!(controllerBE instanceof IMultiBlockController controller)) return false;
        Level level = controllerBE.getLevel();
        BlockPos pos = controllerBE.getBlockPos();

        if (controller.isFormed()) return true;

        if (!MultiBlockValidator.validate(level, pattern, pos, facing)) return false;

        if (player != null) {
            controller.stampOwner(player.getUUID(), player.getName().getString());
        }
        controller.onMultiblockFormed();
        return true;
    }

    /**
     * Handle a formed multiblock being broken.
     * Posts {@link MultiBlockBreakEvent} on NeoForge EVENT_BUS so other mods can react.
     * <p>
     * 处理已形成的多方块结构被破坏的情况。
     * 在 NeoForge EVENT_BUS 上发布 {@link MultiBlockBreakEvent}，以便其他模组能够响应。
     */
    public static void notifyBreak(IMultiBlockController controller, BlockPos pos, Level level) {
        controller.onMultiblockBroken();
        if (level != null && !level.isClientSide()) {
            NeoForge.EVENT_BUS.post(new MultiBlockBreakEvent(controller, pos, level));
        }
    }
}
