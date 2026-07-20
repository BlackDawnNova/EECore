package com.endlessepoch.core.api.recipe;

import com.endlessepoch.core.EECore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Thread-safe multi-type cache: RecipeType → (input item ID → matching RecipeSnapshots).
 * Preloaded on server start. Background threads read-only. Addon mods call {@link #register}
 * to make their recipe type batch-capable.
 * 多类型线程安全缓存：配方类型→(物品ID→匹配快照列表)。服务端启动预热，后台只读。
 * 附属Mod调用 register 将自己的配方类型注册为可批处理。
 */
public final class RecipeSnapshotCache {

    private static final Map<RecipeType<?>, Map<Long, List<RecipeSnapshot>>> CACHE = new ConcurrentHashMap<>();

    /** Types registered for batch processing. / 已注册可批处理的配方类型。 */
    private static final Set<RecipeType<?>> REGISTERED = ConcurrentHashMap.newKeySet();

    /** Extractor per type: (recipeId, Recipe) → RecipeSnapshot. / 每类型提取器：(配方ID, Recipe)→快照 */
    private static final Map<RecipeType<?>, BiFunction<ResourceLocation, Recipe<?>, RecipeSnapshot>> EXTRACTORS = new ConcurrentHashMap<>();

    private RecipeSnapshotCache() {}

    /**
     * Register a recipe type for batch processing. Call from mod init or common setup.
     * Extractor receives the recipe ID and the Recipe, returns RecipeSnapshot (null = skip).
     * 将配方类型注册为可批处理。在 mod init 或 common setup 调用。
     */
    @SuppressWarnings("unchecked")
    public static <T extends Recipe<?>> void register(RecipeType<T> type, BiFunction<ResourceLocation, T, RecipeSnapshot> extractor) {
        REGISTERED.add(type);
        EXTRACTORS.put(type, (BiFunction<ResourceLocation, Recipe<?>, RecipeSnapshot>) (Object) extractor);
        EECore.LOGGER.info("RecipeSnapshotCache: registered batch-capable type {}", type);
    }

    /** Whether this recipe type can use the batch pipeline. / 此配方类型是否可走批处理管线。 */
    public static boolean isBatchCapable(RecipeType<?> type) {
        return REGISTERED.contains(type);
    }

    /** Background-thread safe: find recipes matching an item under the given type. / 后台线程安全：按类型查某物品匹配的配方。 */
    public static List<RecipeSnapshot> get(RecipeType<?> type, long itemRegistryId) {
        var sub = CACHE.get(type);
        return sub != null ? sub.getOrDefault(itemRegistryId, Collections.emptyList()) : Collections.emptyList();
    }

    /** Preload all registered recipe types from RecipeManager. / 预热全部已注册类型。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void reload(RecipeManager rm) {
        CACHE.clear();
        int total = 0;

        for (var type : REGISTERED) {
            var extractor = EXTRACTORS.get(type);
            if (extractor == null) continue;
            var sub = new ConcurrentHashMap<Long, List<RecipeSnapshot>>();
            int count = 0;

            // getAllRecipesFor is generic but we store wildcards — cast is safe, type was registered
            // getAllRecipesFor 是泛型方法但 Map 存储了通配符——类型已注册，cast 安全
            for (var holder : rm.getAllRecipesFor((RecipeType) type)) {
                var snap = extractor.apply(((net.minecraft.world.item.crafting.RecipeHolder<?>) holder).id(),
                        ((net.minecraft.world.item.crafting.RecipeHolder<?>) holder).value());
                if (snap == null) continue;
                for (long itemId : snap.inputItemIds()) {
                    sub.computeIfAbsent(itemId, k -> Collections.synchronizedList(new ArrayList<>())).add(snap);
                }
                count++;
            }
            CACHE.put(type, sub);
            total += count;
            EECore.LOGGER.info("RecipeSnapshotCache: reloaded {} recipes for type {}", count, type);
        }
        EECore.LOGGER.info("RecipeSnapshotCache: reloaded {} recipes total across {} types", total, EXTRACTORS.size());
    }

    // === Event subscriber ===

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        reload(event.getServer().getRecipeManager());
    }

    public static int size() {
        int total = 0;
        for (var sub : CACHE.values()) total += sub.size();
        return total;
    }

    public static int typeCount() { return REGISTERED.size(); }
}
