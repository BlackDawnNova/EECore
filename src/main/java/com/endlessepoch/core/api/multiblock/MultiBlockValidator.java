package com.endlessepoch.core.api.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.LinkedHashMap;
import java.util.Map;

/** Validates a pattern at a world position with tag count enforcement. / 验证多方块结构 */
public final class MultiBlockValidator {
    private MultiBlockValidator() {}

    public static boolean validate(Level level, MultiBlockPattern pattern,
                                   BlockPos controllerPos, Direction facing) {
        BlockPos origin = controllerPos.offset(
                -pattern.controllerX, -pattern.controllerY, -pattern.controllerZ);
        int w = pattern.width, d = pattern.depth;
        Map<String, Integer> tagCounts = new LinkedHashMap<>();
        Map<String, Integer> tagLimits = new LinkedHashMap<>();

        for (int y = 0; y < pattern.height; y++)
            for (int z = 0; z < d; z++)
                for (int x = 0; x < w; x++) {
                    char expected = pattern.getChar(x, y, z);
                    if (expected == ' ' || expected == '_') continue;
                    BlockState required = pattern.getExpectedState(x, y, z);
                    if (required == null) continue;
                    BlockPos worldPos = transform(origin, x, y, z, w, d, facing);
                    if (!level.getBlockState(worldPos).getBlock().equals(required.getBlock())) return false;
                    for (String tag : pattern.getTags(expected)) {
                        int mc = TagDefRegistry.getMaxCount(tag);
                        if (mc > 0) { tagCounts.merge(tag, 1, Integer::sum); tagLimits.put(tag, mc); }
                    }
                }
        for (var e : tagCounts.entrySet()) {
            Integer limit = tagLimits.get(e.getKey());
            if (limit != null && e.getValue() > limit) return false;
        }
        return true;
    }

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
