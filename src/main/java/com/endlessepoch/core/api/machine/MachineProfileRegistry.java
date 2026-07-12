package com.endlessepoch.core.api.machine;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Global registry of machine profiles. Addon mods call {@link #register}.
 * 全局机器类型注册表，附属 mod 调用 register。
 */
public class MachineProfileRegistry {

    private static final Map<ResourceLocation, MachineProfile> PROFILES = new LinkedHashMap<>();

    /** Register a machine profile. / 注册机器类型。 */
    public static void register(MachineProfile profile) {
        PROFILES.put(profile.id(), profile);
    }

    /** Get all profiles in registration order. / 按注册顺序返回所有类型。 */
    public static List<MachineProfile> getAll() {
        return List.copyOf(PROFILES.values());
    }

    /** Look up a profile by ID. / 按 ID 查找。 */
    public static Optional<MachineProfile> get(ResourceLocation id) {
        return Optional.ofNullable(PROFILES.get(id));
    }
}
