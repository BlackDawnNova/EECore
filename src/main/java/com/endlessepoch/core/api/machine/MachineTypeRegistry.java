package com.endlessepoch.core.api.machine;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Global registry of machine types. Addon mods call {@link #register}.
 * Replaces the old MachineProfile/MachineTypeRegistry.
 * <p>
 * 全局机器类型注册表，附属Mod调用register注册自定义机器类型。
 */
public class MachineTypeRegistry {

    private static final Map<ResourceLocation, MachineType> TYPES = new LinkedHashMap<>();

    public static void register(MachineType type) {
        TYPES.put(type.id(), type);
    }

    public static List<MachineType> getAll() {
        return List.copyOf(TYPES.values());
    }

    public static Optional<MachineType> get(ResourceLocation id) {
        return Optional.ofNullable(TYPES.get(id));
    }
}
