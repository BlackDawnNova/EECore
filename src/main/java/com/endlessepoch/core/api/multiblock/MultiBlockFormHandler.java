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
 */
public class MultiBlockFormHandler {

    /**
     * Attempt to form a multiblock around a controller BE.
     *
     * @param controllerBE the controller block entity
     * @param pattern      the pattern to match
     * @param facing       direction the controller faces
     * @param player       the player initiating formation (may be null for auto-recheck)
     * @return true if formed successfully
     */
    public static boolean tryForm(BlockEntity controllerBE, MultiBlockPattern pattern,
                                   Direction facing, Player player) {
        if (!(controllerBE instanceof IMultiBlockController controller)) return false;
        Level level = controllerBE.getLevel();
        BlockPos pos = controllerBE.getBlockPos();

        if (controller.isFormed()) return true; // already formed, still "success"

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
     */
    public static void notifyBreak(IMultiBlockController controller, BlockPos pos, Level level) {
        controller.onMultiblockBroken();
        if (level != null && !level.isClientSide()) {
            NeoForge.EVENT_BUS.post(new MultiBlockBreakEvent(controller, pos, level));
        }
    }
}
