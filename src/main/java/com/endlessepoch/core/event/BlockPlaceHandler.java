package com.endlessepoch.core.event;

import com.endlessepoch.core.api.multiblock.MachineRegistry;
import com.endlessepoch.core.nova.block.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.*;

/**
 * Global block-placement listener — triggers pattern re-check on ANY block
 * placed near a machine controller. Uses cached controller positions for efficiency.
 * 全局方块放置监听，用控制器位置缓存高效查找。
 */
public class BlockPlaceHandler {

    // Cached controller positions: BlockPos → world dimension registry key / 控制器位置缓存
    private static final Map<BlockPos, net.minecraft.resources.ResourceKey<Level>> controllers = new HashMap<>();
    private static int validCheckTick;

    /** Register a controller (called from BE onLoad). / 注册控制器（BE 加载时调用）。 */
    public static void registerController(BlockPos pos, net.minecraft.resources.ResourceKey<Level> dim) {
        controllers.put(pos.immutable(), dim);
    }

    /** Unregister a controller (called from BE setRemoved). / 注销控制器（BE 移除时调用）。 */
    public static void unregisterController(BlockPos pos) {
        controllers.remove(pos);
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) return;
        BlockPos pos = event.getPos();
        int maxR = getMaxRadius();
        for (var entry : controllers.entrySet()) {
            BlockPos cp = entry.getKey();
            if (!level.dimension().equals(entry.getValue())) continue;
            if (Math.abs(pos.getX() - cp.getX()) > maxR) continue;
            if (Math.abs(pos.getY() - cp.getY()) > maxR) continue;
            if (Math.abs(pos.getZ() - cp.getZ()) > maxR) continue;
            BlockEntity be = level.getBlockEntity(cp);
            if (be instanceof MachineControllerBlockEntity mc && mc.getMachineId() != null) {
                mc.schedulePatternCheck(20);
            } else {
                controllers.remove(cp);
            }
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineControllerBlockEntity mc && mc.getMachineId() != null) {
            controllers.put(pos.immutable(), level.dimension());
        }

        // Periodic cleanup every 600 ticks / 每30秒清理过期
        if (++validCheckTick > 600) {
            validCheckTick = 0;
            var server = level.getServer();
            if (server == null || !server.isRunning()) { controllers.clear(); return; }
            controllers.entrySet().removeIf(e -> {
                var w = server.getLevel(e.getValue());
                if (w == null) return true;
                BlockEntity che = w.getBlockEntity(e.getKey());
                return !(che instanceof MachineControllerBlockEntity cmc && cmc.getMachineId() != null);
            });
        }
    }

    /** Get max pattern radius for search (cached). / 获取最大 Pattern 搜索半径（缓存）。 */
    private static int cachedMaxR = 16;
    private static int cacheRefreshTick;
    static int getMaxRadius() {
        if (cacheRefreshTick++ < 600) return cachedMaxR;
        cacheRefreshTick = 0;
        int max = 16;
        for (var def : MachineRegistry.getAll()) {
            var p = def.getPattern().orElse(null);
            if (p != null) {
                int r = Math.max(Math.max(p.controllerX + 1, p.width - p.controllerX),
                        Math.max(p.controllerY + 1, p.height - p.controllerY));
                r = Math.max(r, Math.max(p.controllerZ + 1, p.depth - p.controllerZ));
                if (r > max) max = r;
            }
        }
        cachedMaxR = Math.min(max, 24);
        return cachedMaxR;
    }
}
