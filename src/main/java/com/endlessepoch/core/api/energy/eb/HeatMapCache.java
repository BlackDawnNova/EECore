package com.endlessepoch.core.api.energy.eb;

import com.endlessepoch.core.EECore;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Global cache mapping recipe-type IDs to default {@link HeatConfig}.
 * Used as fallback for vanilla recipes (furnace/blast_furnace etc.)
 * that don't carry their own maxHeat via {@code MachineRecipe}.
 * MachineRecipe provides maxHeat directly from JSON — no cache lookup needed.
 * <p>
 * 全局热能缓存，给没有自定义 maxHeat 的配方（原版熔炉等）提供默认值。
 * MachineRecipe 从 JSON 自带 maxHeat，不走此缓存。
 */
@EventBusSubscriber(modid = EECore.MOD_ID)
public final class HeatMapCache {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<ResourceLocation, HeatConfig> CACHE = new ConcurrentHashMap<>();
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();

    static {
        CACHE.put(ResourceLocation.fromNamespaceAndPath("eecore", "furnace"),
                new HeatConfig(ResourceLocation.fromNamespaceAndPath("eecore", "furnace"), 200, 10.0));
        CACHE.put(ResourceLocation.fromNamespaceAndPath("eecore", "blast_furnace"),
                new HeatConfig(ResourceLocation.fromNamespaceAndPath("eecore", "blast_furnace"), 400, 15.0));
        CACHE.put(ResourceLocation.fromNamespaceAndPath("eecore", "machine"),
                new HeatConfig(ResourceLocation.fromNamespaceAndPath("eecore", "machine"), 300, 12.0));
        CACHE.put(ResourceLocation.fromNamespaceAndPath("eecore", "boiler"),
                new HeatConfig(ResourceLocation.fromNamespaceAndPath("eecore", "boiler"), 250, 8.0));
    }

    private HeatMapCache() {}

    /** Read-locked lookup. / 读锁查询 */
    public static HeatConfig get(ResourceLocation recipeId) {
        LOCK.readLock().lock();
        try { return CACHE.get(recipeId); }
        finally { LOCK.readLock().unlock(); }
    }

    /** Write-locked insert. / 写锁插入 */
    public static void put(ResourceLocation recipeId, HeatConfig config) {
        LOCK.writeLock().lock();
        try { CACHE.put(recipeId, config); }
        finally { LOCK.writeLock().unlock(); }
    }

    /** Atomic replace. / 原子替换 */
    public static void reload(Map<ResourceLocation, HeatConfig> newMap) {
        LOCK.writeLock().lock();
        try { CACHE.clear(); CACHE.putAll(newMap); }
        finally { LOCK.writeLock().unlock(); }
    }

    public static void clear() {
        LOCK.writeLock().lock();
        try { CACHE.clear(); }
        finally { LOCK.writeLock().unlock(); }
    }

    @SubscribeEvent
    static void onServerStarting(ServerAboutToStartEvent event) {
        LOGGER.info("[EB-HeatMap] {} default heat configs loaded", CACHE.size());
    }
}
