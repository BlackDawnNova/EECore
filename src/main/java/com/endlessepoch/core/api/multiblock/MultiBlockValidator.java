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
                        int mc = pattern.getBlockLimit(tag, actual.getBlock());
                        if (mc <= 0) mc = TagDefRegistry.getMaxCount(tag, actual.getBlock());
                        if (mc > 0) {
                            String key = tag + "|" + actual.getBlock().hashCode();
                            tagCounts.merge(key, 1, Integer::sum);
                            tagLimits.put(key, mc);
                        }
                        // Category-total limits: count across every block in the category
                        // 类别总量上限：同类别所有方块合计计数
                        for (var ce : pattern.getCategoryLimits(tag).entrySet()) {
                            if (ce.getValue() > 0 && ce.getKey().matches(actual.getBlock())) {
                                String key = tag + "|cat:" + ce.getKey().name();
                                tagCounts.merge(key, 1, Integer::sum);
                                tagLimits.put(key, ce.getValue());
                            }
                        }
                    }
                }
        for (var e : tagCounts.entrySet()) {
            Integer limit = tagLimits.get(e.getKey());
            if (limit != null && e.getValue() > limit) return false;
        }
        return true;
    }

    public static FrameResult validateFrame(Level level, MultiBlockPattern pattern, BlockPos ctrlPos, Direction facing) {
        java.util.Set<Block> casingBlocks = new java.util.HashSet<>();
        for (char c : pattern.getDefinitions().keySet())
            if (pattern.getTags(c).contains(pattern.getCasingTag()))
                for (var bs : pattern.getAlternatives(c)) {
                    Block b = bs.getBlock();
                    if (b != net.minecraft.world.level.block.Blocks.AIR && !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath().contains("ae_interface"))
                        casingBlocks.add(b);
                }
        if (casingBlocks.isEmpty()) return null;
        casingBlocks.add(level.getBlockState(ctrlPos).getBlock());
        int mx = scan(level, ctrlPos, facing, 1, 0, 0, casingBlocks, pattern.getMaxW());
        int nx = scan(level, ctrlPos, facing, -1, 0, 0, casingBlocks, pattern.getMaxW());
        int my = scan(level, ctrlPos, facing, 0, 1, 0, casingBlocks, pattern.getMaxH());
        int ny = scan(level, ctrlPos, facing, 0, -1, 0, casingBlocks, pattern.getMaxH());
        int mz = scan(level, ctrlPos, facing, 0, 0, 1, casingBlocks, pattern.getMaxD());
        int nz = scan(level, ctrlPos, facing, 0, 0, -1, casingBlocks, pattern.getMaxD());
        int w = mx + nx + 1, h = my + ny + 1, d = mz + nz + 1;
        if (w < pattern.getMinW() || h < pattern.getMinH() || d < pattern.getMinD()) return null;
        // Map local -X/-Y/-Z offsets to world axes based on facing / 根据朝向映射本地偏移到世界轴
        int wOx, wOy, wOz;
        switch (facing) {
            case NORTH -> { wOx = -nx; wOy = -ny; wOz = -nz; }
            case SOUTH -> { wOx =  nx; wOy = -ny; wOz =  nz; }
            case EAST  -> { wOx =  nz; wOy = -ny; wOz = -nx; }
            case WEST  -> { wOx = -nz; wOy = -ny; wOz =  nx; }
            default    -> { wOx = -nx; wOy = -ny; wOz = -nz; }
        }
        BlockPos origin = ctrlPos.offset(wOx, wOy, wOz);
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) for (int z = 0; z < d; z++) {
            if (x > 0 && x < w - 1 && y > 0 && y < h - 1 && z > 0 && z < d - 1) continue;
            BlockPos wp = switch (facing) {
                case NORTH -> origin.offset(x, y, z);
                case SOUTH -> origin.offset(-x, y, -z);
                case EAST  -> origin.offset(-z, y, x);
                case WEST  -> origin.offset(z, y, -x);
                default    -> origin.offset(x, y, z);
            };
            if (!casingBlocks.contains(level.getBlockState(wp).getBlock())) return null;
        }
        return new FrameResult(w, h, d, wOx, wOy, wOz);
    }



    public record FrameResult(int width, int height, int depth, int originX, int originY, int originZ) {}

    private static int scan(Level level, BlockPos ctrl, Direction facing, int dx, int dy, int dz,
                            java.util.Set<Block> casing, int max) {
        int adx = dx, ady = dy, adz = dz;
        if (facing == Direction.EAST)  { adx = -dz; adz = dx; }
        if (facing == Direction.SOUTH) { adx = -dx; adz = -dz; }
        if (facing == Direction.WEST)  { adx = dz; adz = -dx; }
        for (int i = 1; i <= max; i++) {
            BlockPos wp = ctrl.offset(adx * i, ady * i, adz * i);
            Block b = level.getBlockState(wp).getBlock();
                if (b == net.minecraft.world.level.block.Blocks.AIR) return 0; if (casing.contains(b)) return i;
        }
        return 0;
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

    /**
     * Validate pattern against world and send missing/wrong positions to client for ghost preview.
     * Shared by ScannerControllerBlock and MachineControllerBlock.
     * 逐块验证并发送幽灵预览包——共享方法。
     */
    public static void validateAndPreview(MultiBlockPattern pat, net.minecraft.resources.ResourceLocation patternId,
                                          BlockPos controllerPos, Direction facing, Level level,
                                          net.minecraft.server.level.ServerPlayer player,
                                          boolean postFormation) {
        int w = pat.width, h = pat.height, d = pat.depth;
        var mLocal = new java.util.ArrayList<Integer>();
        var mWorld = new java.util.ArrayList<Integer>();
        var wLocal = new java.util.ArrayList<Integer>();
        var wWorld = new java.util.ArrayList<Integer>();
        for (int y = 0; y < h; y++)
            for (int z = 0; z < d; z++)
                for (int x = 0; x < w; x++) {
                    char c = pat.getChar(x, y, z);
                    if (c == 'A' || c == ' ' || c == com.endlessepoch.ecsformat.EcsFormat.CHAR_CONTROLLER) continue;
                    int rx = x - pat.controllerX, ry = y - pat.controllerY, rz = z - pat.controllerZ;
                    BlockPos worldPos = switch (facing) {
                        case NORTH -> controllerPos.offset(rx, ry, rz);
                        case SOUTH -> controllerPos.offset(-rx, ry, -rz);
                        case EAST  -> controllerPos.offset(-rz, ry, rx);
                        case WEST  -> controllerPos.offset(rz, ry, -rx);
                        default    -> controllerPos.offset(rx, ry, rz);
                    };
                    var worldState = level.getBlockState(worldPos);
                    var expected = pat.getExpectedState(x, y, z);
                    if (worldState.isAir()) {
                        mLocal.add(x); mLocal.add(y); mLocal.add(z);
                        mWorld.add(worldPos.getX()); mWorld.add(worldPos.getY()); mWorld.add(worldPos.getZ());
                    } else if (expected != null && expected.getBlock() != worldState.getBlock()) {
                        var alts = pat.getAlternatives(c);
                        boolean altMatch = false;
                        for (var alt : alts)
                            if (alt.getBlock() == worldState.getBlock()) { altMatch = true; break; }
                        if (!altMatch) {
                            wLocal.add(x); wLocal.add(y); wLocal.add(z);
                            wWorld.add(worldPos.getX()); wWorld.add(worldPos.getY()); wWorld.add(worldPos.getZ());
                        }
                    }
                }
        int MAX = 1_000_000;
        var pkt = new com.endlessepoch.core.network.SyncValidationPacket(patternId,
                toArr(mLocal, MAX), toArr(mWorld, MAX),
                toArr(wLocal, MAX), toArr(wWorld, MAX),
                controllerPos.getX(), controllerPos.getY(), controllerPos.getZ(), w, h, d,
                postFormation);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, pkt);
    }

    private static int[] toArr(java.util.List<Integer> list, int max) {
        int len = Math.min(list.size(), max);
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = list.get(i);
        return arr;
    }
}
