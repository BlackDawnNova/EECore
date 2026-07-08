package com.endlessepoch.core.api.multiblock;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Machine effect registry with built-in eecore:celestial (sun/moon/stars halo). / 机器特效注册表，内置日月星辰光环。
 */
public final class MachineEffectRegistry {
    private static final Map<ResourceLocation, Supplier<IMachineEffect>> EFFECTS = new LinkedHashMap<>();

    static {
        // Built-in / 内置
        register("eecore", "celestial", com.endlessepoch.core.nova.client.CelestialEffect::new);
    }

    public static void register(String namespace, String path, Supplier<IMachineEffect> factory) {
        EFFECTS.put(ResourceLocation.fromNamespaceAndPath(namespace, path), factory);
    }

    public static IMachineEffect create(ResourceLocation id) {
        var f = EFFECTS.get(id);
        return f != null ? f.get() : null;
    }
}
