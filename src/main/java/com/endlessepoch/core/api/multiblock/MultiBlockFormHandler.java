package com.endlessepoch.core.api.multiblock;

import com.endlessepoch.core.nova.block.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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

        if (pattern.isFrameBased()) {
            var fr = MultiBlockValidator.validateFrame(level, pattern, pos, facing);
            if (fr == null) { if (controller.isFormed()) controller.onMultiblockBroken(); return false; }
            if (!controller.isFormed()) {
                if (player != null) controller.stampOwner(player.getUUID(), player.getName().getString());
                ResourceLocation machineId = controllerBE instanceof MachineControllerBlockEntity mcbe ? mcbe.getMachineId() : null;
                for (int x = 0; x < fr.width(); x++)
                    for (int y = 0; y < fr.height(); y++)
                        for (int z = 0; z < fr.depth(); z++) {
                            BlockPos wp = pos.offset(fr.originX(), fr.originY(), fr.originZ()).offset(x, y, z);
                            BlockEntity be = level.getBlockEntity(wp);
                            if (be instanceof IPart part && machineId != null)
                                part.onFormed(machineId, pos);
                        }
                BlockPos frOrigin = pos.offset(fr.originX(), fr.originY(), fr.originZ());
                if (level instanceof net.minecraft.server.level.ServerLevel sl)
                    MultiBlockBreakDetector.stampFrame(sl, frOrigin, pos, fr.width(), fr.height(), fr.depth(), facing, fr.shellPositions());
                controller.onMultiblockFormed();
            }
            return true;
        }

        if (!MultiBlockValidator.validate(level, pattern, pos, facing)) {
            if (controller.isFormed()) controller.onMultiblockBroken();
            return false;
        }

        if (!controller.isFormed()) {
            if (player != null) {
                controller.stampOwner(player.getUUID(), player.getName().getString());
            }

            // Get machineId for part binding / 获取 machineId
            ResourceLocation machineId = null;
            if (controllerBE instanceof MachineControllerBlockEntity mcbe)
                machineId = mcbe.getMachineId();

            // Bind parts BEFORE onMultiblockFormed so scanParts finds them / 先绑部件再成型
            for (int y = 0; y < pattern.height; y++)
                for (int z = 0; z < pattern.depth; z++)
                    for (int x = 0; x < pattern.width; x++) {
                        if (pattern.getChar(x, y, z) == 'A' || pattern.getChar(x, y, z) == ' ') continue;
                        int rx = x - pattern.controllerX, ry = y - pattern.controllerY, rz = z - pattern.controllerZ;
                        BlockPos wp = switch (facing) {
                            case NORTH -> pos.offset(rx, ry, rz);
                            case SOUTH -> pos.offset(-rx, ry, -rz);
                            case EAST  -> pos.offset(-rz, ry, rx);
                            case WEST  -> pos.offset(rz, ry, -rx);
                            default    -> pos.offset(rx, ry, rz);
                        };
                        BlockEntity be = level.getBlockEntity(wp);
                        if (be instanceof com.endlessepoch.core.api.multiblock.IPart part && machineId != null)
                            part.onFormed(machineId, pos);
                    }
            if (level instanceof net.minecraft.server.level.ServerLevel sl)
                MultiBlockBreakDetector.stamp(sl, pattern, pos, facing);

            controller.onMultiblockFormed(); // after parts are formed / 部件就绪后再成型
        }
        return true;
    }

    public static void notifyBreak(IMultiBlockController controller, BlockPos pos, Level level) {
        MultiBlockBreakDetector.clear(pos);
        if (level != null && !level.isClientSide()) {
            NeoForge.EVENT_BUS.post(new MultiBlockBreakEvent(controller, pos, level));
        }
    }
}
