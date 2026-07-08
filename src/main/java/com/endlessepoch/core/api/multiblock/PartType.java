package com.endlessepoch.core.api.multiblock;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Registry of part types. Built-in: bus, hatch, assembly. Addon mods register custom types.
 * 部件类型注册表。内置：总线、仓、总成。附属 mod 注册自定义类型。
 */
public final class PartType {

    private static final Map<ResourceLocation, PartType> REGISTRY = new LinkedHashMap<>();

    public static final PartType INPUT_BUS  = register("input_bus",  "eecore.part.input_bus");
    public static final PartType OUTPUT_BUS = register("output_bus", "eecore.part.output_bus");
    public static final PartType INPUT_HATCH  = register("input_hatch",  "eecore.part.input_hatch");
    public static final PartType OUTPUT_HATCH = register("output_hatch", "eecore.part.output_hatch");
    public static final PartType INPUT_ASSEMBLY  = register("input_assembly",  "eecore.part.input_assembly");
    public static final PartType OUTPUT_ASSEMBLY = register("output_assembly", "eecore.part.output_assembly");
    public static final PartType CASING = register("casing", "eecore.part.casing");

    private final ResourceLocation id;
    private final String translationKey;

    private PartType(ResourceLocation id, String key) { this.id = id; this.translationKey = key; }

    public ResourceLocation getId() { return id; }
    public String getTranslationKey() { return translationKey; }

    public static PartType register(String id, String translationKey) {
        return register(ResourceLocation.parse(id), translationKey);
    }

    public static PartType register(ResourceLocation id, String key) {
        PartType pt = new PartType(id, key);
        REGISTRY.put(id, pt);
        return pt;
    }

    public static PartType get(ResourceLocation id) { return REGISTRY.get(id); }
    public static Collection<PartType> values() { return Collections.unmodifiableCollection(REGISTRY.values()); }

    static { /* forces static init of built-in types */ }
}
