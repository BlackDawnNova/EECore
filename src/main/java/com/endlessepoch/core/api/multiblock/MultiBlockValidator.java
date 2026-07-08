package com.endlessepoch.core.api.multiblock;

import com.endlessepoch.ecsformat.EcsFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class MultiBlockValidator {
    private MultiBlockValidator() {}

    public static boolean validate(Level level, MultiBlockPattern pattern,
                                   BlockPos controllerPos, Direction facing) {
        int cx = pattern.controllerX, cy = pattern.controllerY, cz = pattern.controllerZ;
        int w = pattern.width, d = pattern.depth;
        // Rotate origin with facing so controller cell always maps to controllerPos / 随朝向旋转原点，使控制器位置始终正确映射
        BlockPos origin = switch (facing) {
            case NORTH -> controllerPos.offset(-cx, -cy, -cz);
            case SOUTH -> controllerPos.offset(-(w - 1 - cx), -cy, -(d - 1 - cz));
            case EAST  -> controllerPos.offset(-(d - 1 - cz), -cy, -cx);
            case WEST  -> controllerPos.offset(-cz, -cy, -(w - 1 - cx));
            default    -> controllerPos.offset(-cx, -cy, -cz);
        };

        Map<String, Integer> tagCounts = new LinkedHashMap<>();
        Map<String, Integer> tagLimits = new LinkedHashMap<>();

        for (int y = 0; y < pattern.height; y++)
            for (int z = 0; z < d; z++)
                for (int x = 0; x < w; x++) {
                    char expected = pattern.getChar(x, y, z);
                    if (expected == ' ' || expected == '_' || expected == 'A') continue;
                    BlockState required = pattern.getExpectedState(x, y, z);
                    if (required == null) continue;
                    BlockPos worldPos = transform(origin, x, y, z, w, d, facing);
                    BlockState actual = level.getBlockState(worldPos);

                    // K position: any registered controller block is valid / K位：任意注册控制器都合法
                    if (expected == EcsFormat.CHAR_CONTROLLER) {
                        boolean isCtrl = false;
                        for (Block cb : MultiBlockRegistry.getControllerBlocks())
                            if (actual.getBlock() == cb) { isCtrl = true; break; }
                        if (!isCtrl) return false;
                    } else if (!actual.getBlock().equals(required.getBlock())) {
                        Set<BlockState> alts = pattern.getAlternatives(expected);
                        boolean matched = false;
                        for (BlockState alt : alts)
                            if (actual.getBlock().equals(alt.getBlock())) { matched = true; break; }
                        if (!matched) return false;
                    }

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
