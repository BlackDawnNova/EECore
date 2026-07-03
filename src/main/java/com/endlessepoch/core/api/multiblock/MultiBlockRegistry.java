package com.endlessepoch.core.api.multiblock;

import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MultiBlockRegistry {

    private static final Set<net.minecraft.world.level.block.Block> CONTROLLER_BLOCKS = new LinkedHashSet<>();
    private static final Map<net.minecraft.world.level.block.Block, ResourceLocation> CONTROLLER_PATTERNS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, MultiBlockPattern> MOD_PATTERNS = new LinkedHashMap<>();
    private static final Map<UUID, Map<ResourceLocation, MultiBlockPattern>> LOCAL_PATTERNS = new ConcurrentHashMap<>();

    private MultiBlockRegistry() {}

    public static void registerControllerBlock(net.minecraft.world.level.block.Block block) {
        CONTROLLER_BLOCKS.add(block);
    }

    public static Set<net.minecraft.world.level.block.Block> getControllerBlocks() {
        return Collections.unmodifiableSet(CONTROLLER_BLOCKS);
    }

    public static void registerMod(ResourceLocation id, MultiBlockPattern pattern) {
        MOD_PATTERNS.put(id, pattern);
    }

    public static void bindControllerToPattern(net.minecraft.world.level.block.Block controller, ResourceLocation patternId) {
        CONTROLLER_PATTERNS.put(controller, patternId);
    }

    public static Optional<ResourceLocation> getPatternForController(net.minecraft.world.level.block.Block controller) {
        return Optional.ofNullable(CONTROLLER_PATTERNS.get(controller));
    }

    public static void registerLocal(UUID playerId, ResourceLocation id, MultiBlockPattern pattern) {
        LOCAL_PATTERNS.computeIfAbsent(playerId, k -> new LinkedHashMap<>()).put(id, pattern);
    }

    public static Optional<MultiBlockPattern> get(UUID playerId, ResourceLocation id) {
        Map<ResourceLocation, MultiBlockPattern> playerPatterns = LOCAL_PATTERNS.get(playerId);
        if (playerPatterns != null) {
            MultiBlockPattern p = playerPatterns.get(id);
            if (p != null) return Optional.of(p);
        }
        return Optional.ofNullable(MOD_PATTERNS.get(id));
    }

    public static Optional<MultiBlockPattern> get(ResourceLocation id) {
        return Optional.ofNullable(MOD_PATTERNS.get(id));
    }

    public static Map<ResourceLocation, MultiBlockPattern> getAll(UUID playerId) {
        Map<ResourceLocation, MultiBlockPattern> all = new LinkedHashMap<>();
        Map<ResourceLocation, MultiBlockPattern> playerPatterns = LOCAL_PATTERNS.get(playerId);
        if (playerPatterns != null) all.putAll(playerPatterns);
        all.putAll(MOD_PATTERNS);
        return Collections.unmodifiableMap(all);
    }

    public static Map<ResourceLocation, MultiBlockPattern> getAll() {
        Map<ResourceLocation, MultiBlockPattern> all = new LinkedHashMap<>();
        for (Map<ResourceLocation, MultiBlockPattern> p : LOCAL_PATTERNS.values())
            all.putAll(p);
        all.putAll(MOD_PATTERNS);
        return Collections.unmodifiableMap(all);
    }

    public static boolean removeLocal(UUID playerId, ResourceLocation id) {
        Map<ResourceLocation, MultiBlockPattern> p = LOCAL_PATTERNS.get(playerId);
        return p != null && p.remove(id) != null;
    }

    public static boolean removeMod(ResourceLocation id) {
        return MOD_PATTERNS.remove(id) != null;
    }

    public static void clearLocal(UUID playerId) {
        LOCAL_PATTERNS.remove(playerId);
    }
}
