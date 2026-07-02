package com.endlessepoch.core.api.multiblock;

import net.minecraft.world.level.block.Block;
import java.util.*;

/**
 * Code-side tag registry. Addon mods define tag → blocks + max count here.
 * .ecs files store only the tag name; actual blocks/limits come from this registry.
 * <p>
 * 标签代码注册表。附属模组在此定义标签对应的方块和上限。.ecs 只存标签名。
 */
public final class TagDefRegistry {
    public record TagEntry(Set<Block> blocks, int maxCount) {}

    private static final Map<String, TagEntry> REGISTRY = new LinkedHashMap<>();

    private TagDefRegistry() {}

    public static void register(String tag, Set<Block> blocks, int maxCount) {
        REGISTRY.put(tag, new TagEntry(Collections.unmodifiableSet(new LinkedHashSet<>(blocks)), maxCount));
    }

    public static TagEntry get(String tag) { return REGISTRY.get(tag); }

    public static Set<Block> getBlocks(String tag) {
        TagEntry e = REGISTRY.get(tag);
        return e != null ? e.blocks() : Set.of();
    }

    public static int getMaxCount(String tag) {
        TagEntry e = REGISTRY.get(tag);
        return e != null ? e.maxCount() : 0;
    }

    public static void remove(String tag) { REGISTRY.remove(tag); }
    public static void clear() { REGISTRY.clear(); }
}
