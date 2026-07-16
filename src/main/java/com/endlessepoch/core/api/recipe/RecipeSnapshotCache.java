package com.endlessepoch.core.api.recipe;

import com.endlessepoch.core.EECore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache: input item ID → matching MachineRecipe snapshots.
 * Preloaded on server start. Background threads read-only.
 * 线程安全缓存：输入物品ID→匹配配方快照。服务端启动时预热，后台只读。
 */
public final class RecipeSnapshotCache {

    /** item registry ID → list of recipes that accept this item as input */
    private static final Map<Long, List<RecipeSnapshot>> CACHE = new ConcurrentHashMap<>();

    private RecipeSnapshotCache() {}

    /** Background-thread safe: find recipes matching an item. / 后台线程安全：查某物品能匹配的配方。 */
    public static List<RecipeSnapshot> get(long itemRegistryId) {
        return CACHE.getOrDefault(itemRegistryId, Collections.emptyList());
    }

    /** Preload all MachineRecipes from RecipeManager. / 从 RecipeManager 预热全部配方。 */
    public static void reload(RecipeManager rm) {
        CACHE.clear();
        int count = 0;

        for (var holder : rm.getAllRecipesFor(com.endlessepoch.core.registry.EECoreRecipeTypes.MACHINE.get())) {
            if (holder.value() instanceof MachineRecipe mr) {
                // Clamp — BigInteger.longValue() truncates above 2^63 / 高电压钳位防截断
                var mv = mr.getRequiredTier().getMinVoltage();
                long voltage = mv.bitLength() >= 63 ? Long.MAX_VALUE : mv.longValue();
                var snap = new RecipeSnapshot(
                        holder.id().hashCode() & 0x7FFFFFFFFFFFFFFFL,
                        RecipeSnapshot.ingredientItemIds(mr.getIngredient()),
                        RecipeSnapshot.itemIdsFrom(mr.getResults().toArray(new net.minecraft.world.item.ItemStack[0])),
                        RecipeSnapshot.countsFrom(mr.getResults().toArray(new net.minecraft.world.item.ItemStack[0])),
                        mr.getEnergyPerTick() * mr.getProcessingTime(), // base total energy / 基础总能耗
                        mr.getProcessingTime(),
                        voltage,
                        mr.getEnergyPerTick(),
                        mr.getRequiredTier().ordinal(),
                        mr.getMaxParallel(),
                        mr.getMaxHeat()
                );
                for (long itemId : snap.inputItemIds()) {
                    CACHE.computeIfAbsent(itemId, k -> Collections.synchronizedList(new ArrayList<>())).add(snap);
                }
                count++;
            }
        }
        EECore.LOGGER.info("RecipeSnapshotCache: reloaded {} machine recipes into cache", count);
    }

    // === Event subscriber ===

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        reload(event.getServer().getRecipeManager());
    }

    public static int size() { return CACHE.size(); }
}
