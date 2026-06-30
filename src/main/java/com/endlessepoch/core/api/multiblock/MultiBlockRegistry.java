package com.endlessepoch.core.api.multiblock;

import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multiblock pattern registry — per-player scanned + global mod patterns.
 * <p>
 * Scanned patterns are per-player (keyed by UUID), isolated between players.
 * Mod-registered patterns are globally shared.
 * <p>
 * 多方块结构模式注册表 —— 每个玩家独立扫描 + 全局模组注册模式。
 * <p>
 * 扫描的模式按玩家隔离（以 UUID 为键），玩家之间互不可见。
 * 模组注册的模式为全局共享。
 */
public final class MultiBlockRegistry {

    private static final Map<ResourceLocation, MultiBlockPattern> MOD_PATTERNS = new LinkedHashMap<>();
    private static final Map<UUID, Map<ResourceLocation, MultiBlockPattern>> LOCAL_PATTERNS = new ConcurrentHashMap<>();

    private MultiBlockRegistry() {}

    /**
     * Register a mod-provided pattern (called during common setup).
     * <p>
     * 注册模组提供的模式（在通用初始化阶段调用）。
     */
    public static void registerMod(ResourceLocation id, MultiBlockPattern pattern) {
        MOD_PATTERNS.put(id, pattern);
    }

    /**
     * Store a scanned pattern for a specific player.
     * Other players will not see this pattern.
     * <p>
     * 为指定玩家存储扫描到的模式。
     * 其他玩家无法看到此模式。
     */
    public static void registerLocal(UUID playerId, ResourceLocation id, MultiBlockPattern pattern) {
        LOCAL_PATTERNS.computeIfAbsent(playerId, k -> new LinkedHashMap<>()).put(id, pattern);
    }

    /**
     * Get a pattern by ID for a specific player (searches player's patterns first, then mod global).
     * <p>
     * 根据 ID 为指定玩家获取模式（优先搜索玩家本地模式，再搜索模组全局模式）。
     */
    public static Optional<MultiBlockPattern> get(UUID playerId, ResourceLocation id) {
        Map<ResourceLocation, MultiBlockPattern> playerPatterns = LOCAL_PATTERNS.get(playerId);
        if (playerPatterns != null) {
            MultiBlockPattern p = playerPatterns.get(id);
            if (p != null) return Optional.of(p);
        }
        return Optional.ofNullable(MOD_PATTERNS.get(id));
    }

    /**
     * Get a mod-global pattern by ID (no player filter).
     * <p>
     * 根据 ID 获取模组全局模式（不按玩家过滤）。
     */
    public static Optional<MultiBlockPattern> get(ResourceLocation id) {
        return Optional.ofNullable(MOD_PATTERNS.get(id));
    }

    /**
     * All patterns visible to a specific player (local scanned + mod global).
     * <p>
     * 指定玩家可见的所有模式（本地扫描 + 模组全局）。
     */
    public static Map<ResourceLocation, MultiBlockPattern> getAll(UUID playerId) {
        Map<ResourceLocation, MultiBlockPattern> all = new LinkedHashMap<>();
        Map<ResourceLocation, MultiBlockPattern> playerPatterns = LOCAL_PATTERNS.get(playerId);
        if (playerPatterns != null) all.putAll(playerPatterns);
        all.putAll(MOD_PATTERNS);
        return Collections.unmodifiableMap(all);
    }

    /**
     * ALL patterns — both local (all players) and mod global.
     * Used by the client-side Visualizer and for backward compat.
     * In multiplayer, client will only see MOD patterns (local patterns
     * stay on the server JVM). In singleplayer (integrated server),
     * everything in one JVM so scanned patterns appear too.
     * <p>
     * 所有模式 —— 包含本地（所有玩家）和模组全局。
     * 用于客户端 Visualizer 及向后兼容。
     * 在多人模式下，客户端仅能看到模组模式（本地模式保留在服务端 JVM 中）。
     * 在单人模式（集成服务端）下，所有内容在同一个 JVM 中，因此扫描的模式也会出现。
     */
    public static Map<ResourceLocation, MultiBlockPattern> getAll() {
        Map<ResourceLocation, MultiBlockPattern> all = new LinkedHashMap<>();
        for (Map<ResourceLocation, MultiBlockPattern> playerPatterns : LOCAL_PATTERNS.values()) {
            all.putAll(playerPatterns);
        }
        all.putAll(MOD_PATTERNS);
        return Collections.unmodifiableMap(all);
    }

    /**
     * Clear all scanned patterns for a specific player (called when player disconnects).
     * <p>
     * 清除指定玩家的所有扫描模式（在玩家断开连接时调用）。
     */
    public static void clearLocal(UUID playerId) {
        LOCAL_PATTERNS.remove(playerId);
    }
}
