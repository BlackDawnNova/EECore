package com.endlessepoch.core.api.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;

public class MultiBlockFormHandler {

    public static boolean tryForm(BlockEntity controllerBE, MultiBlockPattern pattern,
                                   Direction facing, Player player) {
        if (!(controllerBE instanceof IMultiBlockController controller)) return false;
        Level level = controllerBE.getLevel();
        BlockPos pos = controllerBE.getBlockPos();

        // Always validate — don't trust cached formed flag / 始终验证，不信任缓存的 formed 标记
        if (!MultiBlockValidator.validate(level, pattern, pos, facing)) {
            if (controller.isFormed()) controller.onMultiblockBroken();
            return false;
        }

        if (!controller.isFormed()) {
            if (player != null) {
                controller.stampOwner(player.getUUID(), player.getName().getString());
            }
            controller.onMultiblockFormed();
        }
        return true;
    }

    public static void notifyBreak(IMultiBlockController controller, BlockPos pos, Level level) {
        // Caller already called onMultiblockBroken — just fire event / 仅触发事件
        if (level != null && !level.isClientSide()) {
            NeoForge.EVENT_BUS.post(new MultiBlockBreakEvent(controller, pos, level));
        }
    }
}
