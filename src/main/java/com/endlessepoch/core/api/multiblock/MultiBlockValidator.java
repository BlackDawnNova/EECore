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
                                String key = tag + "|cat:" + ce.getKey().getId();
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

    /**
     * Frame-based validation: BFS from controller's back face through interior,
     * discovers shell boundaries, verifies six-face completeness and inner dimensions.
     * 框架式验证：从控制器背面BFS遍历内部空间，发现外壳边界，六面完整+内部尺寸校验。
     */
    public static FrameResult validateFrame(Level level, MultiBlockPattern pattern, BlockPos ctrlPos, Direction facing) {
        java.util.Set<Block> casingBlocks = new java.util.HashSet<>();
        for (char c : pattern.getDefinitions().keySet())
            if (pattern.getTags(c).contains(pattern.getCasingTag()))
                for (var bs : pattern.getAlternatives(c)) {
                    Block b = bs.getBlock();
                    if (b != net.minecraft.world.level.block.Blocks.AIR
                            && !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath().contains("ae_interface"))
                        casingBlocks.add(b);
                }
        if (casingBlocks.isEmpty()) return null;
        casingBlocks.add(level.getBlockState(ctrlPos).getBlock());
        casingBlocks.add(level.getBlockState(ctrlPos).getBlock());

        BlockPos entry = null;
        Direction[] dirs = {facing.getOpposite(), Direction.UP, Direction.DOWN,
                Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction d : dirs) {
            BlockPos nb = ctrlPos.relative(d);
            if (!casingBlocks.contains(level.getBlockState(nb).getBlock())) { entry = nb; break; }
        }
        if (entry == null) return null;

        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        java.util.Set<BlockPos> shell = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(entry);
        visited.add(entry);

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        shell.add(ctrlPos);
        minX = maxX = ctrlPos.getX(); minY = maxY = ctrlPos.getY(); minZ = maxZ = ctrlPos.getZ();

        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());

            for (Direction d : Direction.values()) {
                BlockPos nb = p.relative(d);
                if (visited.contains(nb)) continue;
                visited.add(nb);
                BlockState st = level.getBlockState(nb);
                if (casingBlocks.contains(st.getBlock())) {
                    shell.add(nb);
                    minX = Math.min(minX, nb.getX()); maxX = Math.max(maxX, nb.getX());
                    minY = Math.min(minY, nb.getY()); maxY = Math.max(maxY, nb.getY());
                    minZ = Math.min(minZ, nb.getZ()); maxZ = Math.max(maxZ, nb.getZ());
                } else {
                    queue.add(nb);
                }
            }
            if (visited.size() > 10000) return null;
        }

        for (BlockPos vp : visited) {
            if (shell.contains(vp)) continue;
            for (Direction d : Direction.values()) {
                BlockPos nb = vp.relative(d);
                if (!visited.contains(nb) && !shell.contains(nb)) return null;
            }
        }

        int innerW = maxX - minX - 1, innerH = maxY - minY - 1, innerD = maxZ - minZ - 1;
        if (innerW < 1) innerW = 1; if (innerH < 1) innerH = 1; if (innerD < 1) innerD = 1;
        if (innerW > pattern.getInnerW() || innerH > pattern.getInnerH() || innerD > pattern.getInnerD()) return null;

        java.util.Map<String, java.util.Map<Block, Integer>> tagBlockCounts = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.Map<PartCategory, Integer>> tagCatCounts = new java.util.LinkedHashMap<>();
        for (BlockPos wp : visited) {
            if (shell.contains(wp)) continue;
            Block b = level.getBlockState(wp).getBlock();
            if (b == net.minecraft.world.level.block.Blocks.AIR) continue;
            for (var defEntry : pattern.getDefinitions().entrySet()) {
                char c = defEntry.getKey();
                if (!pattern.getAlternatives(c).stream().anyMatch(bs -> bs.getBlock() == b)) continue;
                for (String tag : pattern.getTags(c)) {
                    int lim = pattern.getBlockLimit(tag, b);
                    if (lim > 0) {
                        tagBlockCounts.computeIfAbsent(tag, tk -> new java.util.LinkedHashMap<>()).merge(b, 1, Integer::sum);
                        if (tagBlockCounts.get(tag).get(b) > lim) return null;
                    }
                    for (var ce : pattern.getCategoryLimits(tag).entrySet()) {
                        if (ce.getValue() > 0 && ce.getKey().matches(b)) {
                            tagCatCounts.computeIfAbsent(tag, tk -> new java.util.LinkedHashMap<>()).merge(ce.getKey(), 1, Integer::sum);
                            if (tagCatCounts.get(tag).get(ce.getKey()) > ce.getValue()) return null;
                        }
                    }
                }
                break;
            }
        }

        int shellTier = -1;
        for (BlockPos sp : shell) {
            Block b = level.getBlockState(sp).getBlock();
            if (b instanceof com.endlessepoch.core.nova.block.part.PartBlock pb) {
                int t = pb.getTier();
                if (t <= 0) continue;
                if (shellTier < 0) shellTier = t;
                else if (shellTier != t) return null;
            }
        }

        int shellW = maxX - minX + 1, shellH = maxY - minY + 1, shellD = maxZ - minZ + 1;
        int wOx = minX - ctrlPos.getX(), wOy = minY - ctrlPos.getY(), wOz = minZ - ctrlPos.getZ();
        return new FrameResult(shellW, shellH, shellD, wOx, wOy, wOz, shell);
    }

    public record FrameResult(int width, int height, int depth, int originX, int originY, int originZ,
                              java.util.Set<BlockPos> shellPositions) {}

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
        if (pat.isFrameBased()) return;
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
