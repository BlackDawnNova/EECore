package com.endlessepoch.core.api.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Validates a multi-block structure against a pattern at a given world position.
 */
public final class MultiBlockValidator {

    private MultiBlockValidator() {}

    /**
     * Check if a pattern matches at the given controller position.
     *
     * @param level    the world
     * @param pattern  the pattern definition
     * @param controllerPos position of the controller block
     * @param facing   direction the pattern faces (north=default)
     * @return true if structure matches
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

    /** Transform pattern-local coordinates to world coordinates. */
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
