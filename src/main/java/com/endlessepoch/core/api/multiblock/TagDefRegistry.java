package com.endlessepoch.core.api.multiblock;

import net.minecraft.world.level.block.Block;
import java.util.*;

/**
 * Code-side tag registry. Addon mods define tag → blocks + per-block limits.
 * .ecs files store only the tag name; actual blocks/limits come from this registry.
 * <p>
 * 标签代码注册表。附属模组在此定义标签对应的方块和上限。.ecs 只存标签名。
 * <p>
 * Per-block limits override tag-level maxCount. -1 = unlimited.
 * 每个方块可以单独设上限，覆盖 tag 级 maxCount。-1 = 无限。
 */
public final class TagDefRegistry {
    public record TagEntry(Set<Block> blocks, int maxCount) {}

    private static final Map<String, TagEntry> REGISTRY = new LinkedHashMap<>();
    private static final Map<String, Map<Block, Integer>> PER_BLOCK_LIMITS = new LinkedHashMap<>();

    private TagDefRegistry() {}

    /** Register tag with uniform max count for all blocks. / 注册标签，所有方块统一上限。 */
    public static void register(String tag, Set<Block> blocks, int maxCount) {
        REGISTRY.put(tag, new TagEntry(Collections.unmodifiableSet(new LinkedHashSet<>(blocks)), maxCount));
    }

    /**
     * Set per-block max count. Overrides tag-level maxCount for this block.
     * 设置单个方块上限，覆盖 tag 级 maxCount。-1 = 无限。
     */
    public static void setBlockLimit(String tag, Block block, int maxCount) {
        PER_BLOCK_LIMITS.computeIfAbsent(tag, k -> new LinkedHashMap<>()).put(block, maxCount);
    }

    public static TagEntry get(String tag) { return REGISTRY.get(tag); }

    public static Set<Block> getBlocks(String tag) {
        TagEntry e = REGISTRY.get(tag);
        return e != null ? e.blocks() : Set.of();
    }

    /** Get max count for a block within a tag. Falls back to tag-level. / 某方块在标签中的上限。 */
    public static int getMaxCount(String tag, Block block) {
        Map<Block, Integer> perBlock = PER_BLOCK_LIMITS.get(tag);
        if (perBlock != null && perBlock.containsKey(block))
            return perBlock.get(block);
        TagEntry e = REGISTRY.get(tag);
        return e != null ? e.maxCount() : 0;
    }

    /** Tag-level max count (for backward compat). / tag级上限（向后兼容）。 */
    public static int getMaxCount(String tag) {
        TagEntry e = REGISTRY.get(tag);
        return e != null ? e.maxCount() : 0;
    }

    public static void remove(String tag) { REGISTRY.remove(tag); PER_BLOCK_LIMITS.remove(tag); }
    public static void clear() { REGISTRY.clear(); PER_BLOCK_LIMITS.clear(); }
}
