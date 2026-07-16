package com.endlessepoch.core.api.multiblock;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Defines what a multiblock part can do. Extensible — addon mods register custom abilities.
 * 定义部件能力，可扩展——附属 mod 注册自定义能力。
 */
public final class PartAbility {

    private static final Map<ResourceLocation, PartAbility> REGISTRY = new LinkedHashMap<>();

    // Built-in abilities / 内置能力
    public static final PartAbility ITEM_INPUT = register("item_input");
    public static final PartAbility ITEM_OUTPUT = register("item_output");
    public static final PartAbility FLUID_INPUT = register("fluid_input");
    public static final PartAbility FLUID_OUTPUT = register("fluid_output");
    public static final PartAbility ENERGY_INPUT = register("energy_input");
    public static final PartAbility ENERGY_OUTPUT = register("energy_output");
    public static final PartAbility PARALLEL = register("parallel");
    public static final PartAbility STRUCTURAL = register("structural");

    private final ResourceLocation id;

    private PartAbility(ResourceLocation id) { this.id = id; }

    public ResourceLocation getId() { return id; }

    /**
     * Register a custom ability. Call during mod init.
     * 注册自定义能力，在 mod 初始化时调用。
     */
    public static PartAbility register(String id) {
        return register(ResourceLocation.parse(id));
    }

    public static PartAbility register(ResourceLocation id) {
        return REGISTRY.computeIfAbsent(id, PartAbility::new);
    }

    public static Collection<PartAbility> values() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static PartAbility get(ResourceLocation id) {
        return REGISTRY.get(id);
    }
}
