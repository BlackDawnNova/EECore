package com.endlessepoch.core.api.multiblock;

import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multiblock pattern registry — per-player scanned + global mod patterns.
 * <p>
 * Scanned patterns are per-player (keyed by UUID), isolated between players.
 * Mod-registered patterns are globally shared.
 */
public final class MultiBlockRegistry {

    // Global patterns registered by mods at startup (visible to all players)
    private static final Map<ResourceLocation, MultiBlockPattern> MOD_PATTERNS = new LinkedHashMap<>();

    // Per-player scanned patterns: playerUUID → (patternId → pattern)
    private static final Map<UUID, Map<ResourceLocation, MultiBlockPattern>> LOCAL_PATTERNS = new ConcurrentHashMap<>();

    private MultiBlockRegistry() {}

    /** Register a mod-provided pattern (called during common setup). */
    public static void registerMod(ResourceLocation id, MultiBlockPattern pattern) {
        MOD_PATTERNS.put(id, pattern);
    }

    /**
     * Store a scanned pattern for a specific player.
     * Other players will not see this pattern.
     */
    public static void registerLocal(UUID playerId, ResourceLocation id, MultiBlockPattern pattern) {
        LOCAL_PATTERNS.computeIfAbsent(playerId, k -> new LinkedHashMap<>()).put(id, pattern);
    }

    /**
     * Get a pattern by ID for a specific player (searches player's patterns first, then mod global).
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
     */
    public static Optional<MultiBlockPattern> get(ResourceLocation id) {
        return Optional.ofNullable(MOD_PATTERNS.get(id));
    }

    /**
     * All patterns visible to a specific player (local scanned + mod global).
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
     */
    public static Map<ResourceLocation, MultiBlockPattern> getAll() {
        Map<ResourceLocation, MultiBlockPattern> all = new LinkedHashMap<>();
        for (Map<ResourceLocation, MultiBlockPattern> playerPatterns : LOCAL_PATTERNS.values()) {
            all.putAll(playerPatterns);
        }
        all.putAll(MOD_PATTERNS);
        return Collections.unmodifiableMap(all);
    }

    /** Clear all scanned patterns for a specific player (called when player disconnects). */
    public static void clearLocal(UUID playerId) {
        LOCAL_PATTERNS.remove(playerId);
    }
}
