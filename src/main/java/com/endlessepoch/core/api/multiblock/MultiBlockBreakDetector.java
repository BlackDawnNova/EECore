package com.endlessepoch.core.api.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.world.level.block.Block;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MultiBlockBreakDetector {

    private static final Map<BlockPos, java.util.Set<BlockPos>> FORMED_BLOCKS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, StructureEntry> STRUCTURES = new ConcurrentHashMap<>();

    private MultiBlockBreakDetector() {}

    public static void stamp(ServerLevel level, MultiBlockPattern pattern, BlockPos ctrl, Direction facing) {
        for (int y = 0; y < pattern.height; y++)
            for (int z = 0; z < pattern.depth; z++)
                for (int x = 0; x < pattern.width; x++) {
                    if (pattern.getChar(x, y, z) == 'A' || pattern.getChar(x, y, z) == ' ') continue;
                    int rx = x - pattern.controllerX, ry = y - pattern.controllerY, rz = z - pattern.controllerZ;
                    BlockPos wp = switch (facing) {
                        case NORTH -> ctrl.offset(rx, ry, rz);
                        case SOUTH -> ctrl.offset(-rx, ry, -rz);
                        case EAST  -> ctrl.offset(-rz, ry, rx);
                        case WEST  -> ctrl.offset(rz, ry, -rx);
                        default    -> ctrl.offset(rx, ry, rz);
                    };
                    FORMED_BLOCKS.computeIfAbsent(wp, k -> ConcurrentHashMap.newKeySet()).add(ctrl);
                }
    }

    public static void stampFrame(ServerLevel level, BlockPos origin, BlockPos ctrl,
                                   int w, int h, int d, Direction facing) {
        stampFrame(level, origin, ctrl, w, h, d, facing, null);
    }

    public static void stampFrame(ServerLevel level, BlockPos origin, BlockPos ctrl,
                                   int w, int h, int d, Direction facing,
                                   java.util.Set<BlockPos> shellPositions) {
        java.util.Set<BlockPos> shellCopy = null;
        if (shellPositions != null) shellCopy = new java.util.HashSet<>(shellPositions);
        STRUCTURES.put(ctrl, new StructureEntry(origin, w, h, d, true, shellCopy));
    }

    public static void clear(BlockPos ctrl) {
        for (var set : FORMED_BLOCKS.values()) set.remove(ctrl);
        FORMED_BLOCKS.values().removeIf(Set::isEmpty);
        STRUCTURES.remove(ctrl);
    }

    /** Returns all controllers whose structure includes this position. / 所有包含此位置的控制器。 */
    public static java.util.List<BlockPos> findControllers(BlockPos pos) {
        java.util.List<BlockPos> result = new java.util.ArrayList<>();
        java.util.Set<BlockPos> ctrls = FORMED_BLOCKS.get(pos);
        if (ctrls != null) result.addAll(ctrls);
        for (var e : STRUCTURES.entrySet()) {
            StructureEntry se = e.getValue();
            if (pos.getX() >= se.origin.getX() && pos.getX() < se.origin.getX() + se.w
                    && pos.getY() >= se.origin.getY() && pos.getY() < se.origin.getY() + se.h
                    && pos.getZ() >= se.origin.getZ() && pos.getZ() < se.origin.getZ() + se.d)
                result.add(e.getKey());
        }
        return result;
    }

    public static BlockPos findController(BlockPos pos) {
        java.util.List<BlockPos> list = findControllers(pos);
        return list.isEmpty() ? null : list.get(0);
    }

    public static boolean isFrameStructure(BlockPos ctrl) {
        StructureEntry se = STRUCTURES.get(ctrl);
        return se != null && se.frameBased;
    }

    public static boolean isOnFrameShell(BlockPos pos, BlockPos ctrl) {
        StructureEntry se = STRUCTURES.get(ctrl);
        if (se == null || !se.frameBased) return true;
        if (se.shellBlocks != null) return se.shellBlocks.contains(pos);
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        int ox = se.origin.getX(), oy = se.origin.getY(), oz = se.origin.getZ();
        return x == ox || x == ox + se.w - 1 || y == oy || y == oy + se.h - 1 || z == oz || z == oz + se.d - 1;
    }

    private static void handleBlockChange(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        if (level.isClientSide()) return;
        for (BlockPos ctrl : findControllers(pos)) {
            var be = level.getBlockEntity(ctrl);
            if (!(be instanceof com.endlessepoch.core.nova.block.MachineControllerBlockEntity mc)) continue;
            if (!mc.isFormed()) continue;
            if (isFrameStructure(ctrl) && !isOnFrameShell(pos, ctrl)) continue;
            mc.scheduleStructureCheck();
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        handleBlockChange(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onNeighborNotify(net.neoforged.neoforge.event.level.BlockEvent.NeighborNotifyEvent event) {
        handleBlockChange(event.getLevel(), event.getPos());
    }

    private record StructureEntry(BlockPos origin, int w, int h, int d, boolean frameBased,
                                  java.util.Set<BlockPos> shellBlocks) {}
}
