package com.endlessepoch.core.api.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Validates a multi-block structure against a pattern at a given world position.
 * <p>
 * 在给定的世界位置根据模式验证多方块结构。
 */
public final class MultiBlockValidator {

    private MultiBlockValidator() {}

    /**
     * Check if a pattern matches at the given controller position.
     * <p>
     * 检查在给定控制器位置处模式是否匹配。
     *
     * @param level    the world / 世界
     * @param pattern  the pattern definition / 模式定义
     * @param controllerPos position of the controller block / 控制器方块的位置
     * @param facing   direction the pattern faces (north=default) / 模式朝向（默认为北）
     * @return true if structure matches / 若结构匹配则返回 true
     */
    public static boolean validate(Level level, MultiBlockPattern pattern,
                                   BlockPos controllerPos, Direction facing) {
        BlockPos origin = controllerPos.offset(
                -pattern.controllerX, -pattern.controllerY, -pattern.controllerZ);

        int w = pattern.width;
        int d = pattern.depth;

        for (int y = 0; y < pattern.height; y++) {
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    char expected = pattern.getChar(x, y, z);
                    if (expected == ' ' || expected == '_') continue;

                    BlockState required = pattern.getExpectedState(x, y, z);
                    if (required == null) continue;

                    BlockPos worldPos = transform(origin, x, y, z, w, d, facing);
                    BlockState actual = level.getBlockState(worldPos);

                    if (!actual.getBlock().equals(required.getBlock())) return false;
                }
            }
        }
        return true;
    }

    /**
     * Transform pattern-local coordinates to world coordinates.
     * <p>
     * 将模式局部坐标转换为世界坐标。
     */
    public static BlockPos transform(BlockPos origin, int dx, int dy, int dz,
                                     int width, int depth, Direction facing) {
        return switch (facing) {
            case NORTH -> origin.offset(dx, dy, dz);
            case SOUTH -> origin.offset(width - 1 - dx, dy, depth - 1 - dz);
            case EAST  -> origin.offset(depth - 1 - dz, dy, dx);
            case WEST  -> origin.offset(dz, dy, width - 1 - dx);
            default    -> origin.offset(dx, dy, dz);
        };
    }
}
