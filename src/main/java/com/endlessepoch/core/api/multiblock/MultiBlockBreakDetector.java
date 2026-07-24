package com.endlessepoch.core.api.multiblock;

import com.endlessepoch.core.EECore;
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
    // Per-player last preview source — used to decide whether onRemove should clear ghost.
    // 每玩家上一次幽灵预览的控制器位置——onRemove 时判断是否应该清空。
    private static final Map<UUID, BlockPos> LAST_PREVIEW_CTRL = new ConcurrentHashMap<>();

    private MultiBlockBreakDetector() {}

    /** Record that this controller was the source of the player's last ghost preview. */
    /** 记录此控制器为该玩家上一次幽灵预览的来源。 */
    public static void markPreviewSource(UUID playerId, BlockPos ctrl) {
        LAST_PREVIEW_CTRL.put(playerId, ctrl.immutable());
    }

    /** Whether destroying this controller should clear the player's ghost preview. */
    /** 破坏此控制器时是否应该清空该玩家的幽灵预览。 */
    public static boolean isLastPreviewSource(UUID playerId, BlockPos ctrl) {
        var last = LAST_PREVIEW_CTRL.get(playerId);
        return last != null && last.equals(ctrl);
    }

    public static void stamp(ServerLevel level, MultiBlockPattern pattern, BlockPos ctrl, Direction facing) {
        for (int y = 0; y < pattern.height; y++)
            for (int z = 0; z < pattern.depth; z++)
                for (int x = 0; x < pattern.width; x++) {
                    if (!pattern.isStructureCell(x, y, z)) continue;
                    int rx = x - pattern.controllerX, ry = y - pattern.controllerY, rz = z - pattern.controllerZ;
                    BlockPos wp = MultiBlockValidator.toWorld(ctrl, rx, ry, rz, facing);
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

    @SubscribeEvent
    public static void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        // BreakEvent fires before block removal — defer to end of tick when world state is current.
        // BreakEvent在方块移除前触发，推迟到tick末尾世界状态更新后再验证。
        refreshGhostIfInStructure(event.getLevel(), event.getPos(), true);
    }

    @SubscribeEvent
    public static void onBlockPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        // EntityPlaceEvent fires after placement — world state already current, can validate immediately.
        // EntityPlaceEvent在放置后触发，世界状态已更新，可立即验证。
        refreshGhostIfInStructure(event.getLevel(), event.getPos(), false);
    }

    /**
     * Re-validate ghost preview after a block change in the structure footprint.
     * isBreak=true → defer via server.execute (block not yet removed from world).
     * isBreak=false → call validateAndPreview immediately (world state is current).
     * 结构范围内方块变化后重验证幽灵预览。破坏需延迟（方块尚未移除），放置可直接调。
     */
    private static void refreshGhostIfInStructure(net.minecraft.world.level.LevelAccessor level, BlockPos pos, boolean isBreak) {
        var ctrls = findControllers(pos);
        for (BlockPos ctrl : ctrls) {
            var be = level.getBlockEntity(ctrl);
            if (!(be instanceof com.endlessepoch.core.nova.block.MachineControllerBlockEntity mc)) continue;
            var machineId = mc.getMachineId();
            if (machineId == null || !(level instanceof ServerLevel sl)) continue;
            var patOpt = MultiBlockRegistry.get(machineId);
            if (patOpt.isEmpty()) continue;
            if (!isWithinStructure(pos, ctrl, patOpt.get(), mc.getFacing())) continue;
            mc.scheduleStructureCheck();
            if (isBreak) {
                sl.getServer().execute(() -> {
                    // Controller may have been destroyed — skip if BE is gone.
                    if (!(sl.getBlockEntity(ctrl) instanceof com.endlessepoch.core.nova.block.MachineControllerBlockEntity currentMc))
                        return;
                    var owner = sl.getPlayerByUUID(currentMc.getOwnerUUID());
                    if (owner instanceof net.minecraft.server.level.ServerPlayer sp) {
                        MultiBlockValidator.validateAndPreview(patOpt.get(), machineId, ctrl,
                                currentMc.getFacing(), sl, sp, currentMc.wasEverFormed());
                    }
                });
            } else {
                var owner = sl.getPlayerByUUID(mc.getOwnerUUID());
                if (owner instanceof net.minecraft.server.level.ServerPlayer sp) {
                    MultiBlockValidator.validateAndPreview(patOpt.get(), machineId, ctrl,
                            mc.getFacing(), sl, sp, mc.wasEverFormed());
                }
            }
        }
    }

    public static boolean isWithinStructure(BlockPos pos, BlockPos ctrl,
                                              MultiBlockPattern p, Direction facing) {
        BlockPos local = MultiBlockValidator.toLocal(pos, ctrl, facing,
                p.controllerX, p.controllerY, p.controllerZ);
        return p.isStructureCell(local.getX(), local.getY(), local.getZ());
    }

    private record StructureEntry(BlockPos origin, int w, int h, int d, boolean frameBased,
                                  java.util.Set<BlockPos> shellBlocks) {}
}
