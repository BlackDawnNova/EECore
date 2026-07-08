package com.endlessepoch.core.api.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * Tracks all block positions belonging to formed multiblocks.
 * On block break, checks if the broken block was part of a formed structure.
 * 记录所有已成形多方块结构的方块位置，破坏时触发检测。
 */
public final class MultiBlockBreakDetector {

    /** Broken block position → controller position / 破坏位置 → 控制器位置 */
    private static final Map<BlockPos, BlockPos> FORMED_BLOCKS = new HashMap<>();

    private MultiBlockBreakDetector() {}

    /**
     * Stamp all blocks in a formed structure. / 成形时标记结构内全部方块。
     */
    public static void stamp(ServerLevel level, MultiBlockPattern pattern, BlockPos controllerPos,
                             net.minecraft.core.Direction facing) {
        for (int y = 0; y < pattern.height; y++)
            for (int z = 0; z < pattern.depth; z++)
                for (int x = 0; x < pattern.width; x++) {
                    if (pattern.getChar(x, y, z) == 'A' || pattern.getChar(x, y, z) == ' ') continue;
                    int rx = x - pattern.controllerX, ry = y - pattern.controllerY, rz = z - pattern.controllerZ;
                    BlockPos wp = switch (facing) {
                        case NORTH -> controllerPos.offset(rx, ry, rz);
                        case SOUTH -> controllerPos.offset(-rx, ry, -rz);
                        case EAST  -> controllerPos.offset(-rz, ry, rx);
                        case WEST  -> controllerPos.offset(rz, ry, -rx);
                        default    -> controllerPos.offset(rx, ry, rz);
                    };
                    FORMED_BLOCKS.put(wp, controllerPos);
                }
    }

    /**
     * Clear all blocks belonging to a controller. / 破碎时清除该控制器所有方块标记。
     */
    public static void clear(BlockPos controllerPos) {
        FORMED_BLOCKS.values().removeIf(p -> p.equals(controllerPos));
    }

    /**
     * Check if a broken block belongs to a formed structure.
     * Returns controller position if found, null otherwise.
     * 检查破坏位置是否属于已成形结构，返回控制器位置。
     */
    public static BlockPos check(BlockPos brokenPos) {
        return FORMED_BLOCKS.get(brokenPos);
    }
}
