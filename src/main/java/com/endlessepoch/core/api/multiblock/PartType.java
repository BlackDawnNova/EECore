package com.endlessepoch.core.api.multiblock;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Registry of part types. Built-in: bus, hatch, assembly. Addon mods register custom types.
 * 部件类型注册表。内置：总线、仓、总成。附属 mod 注册自定义类型。
 */
public final class PartType {

    private static final Map<ResourceLocation, PartType> REGISTRY = new LinkedHashMap<>();

    public static final PartType INPUT_BUS  = registerEe("input_bus",  "eecore.part.input_bus");
    public static final PartType OUTPUT_BUS = registerEe("output_bus", "eecore.part.output_bus");
    public static final PartType FLUID_INPUT  = registerEe("fluid_input",  "eecore.part.fluid_input");
    public static final PartType FLUID_OUTPUT = registerEe("fluid_output", "eecore.part.fluid_output");
    public static final PartType ENERGY_INPUT  = registerEe("energy_input",  "eecore.part.energy_input");
    public static final PartType ENERGY_OUTPUT = registerEe("energy_output", "eecore.part.energy_output");
    public static final PartType INPUT_ASSEMBLY  = registerEe("input_assembly",  "eecore.part.input_assembly");
    public static final PartType OUTPUT_ASSEMBLY = registerEe("output_assembly", "eecore.part.output_assembly");
    public static final PartType PARALLEL_HATCH = registerEe("parallel_hatch", "eecore.part.parallel_hatch");
    public static final PartType CASING = registerEe("casing", "eecore.part.casing");
    public static final PartType AE_INTERFACE = registerEe("ae_interface", "eecore.part.ae_interface");
    public static final PartType SUPERCOMPUTING_UNIT = registerEe("supercomputing_unit", "eecore.part.supercomputing_unit");
    public static final PartType PATTERN_UNIT = registerEe("pattern_unit", "eecore.part.pattern_unit");
    public static final PartType QUANTITY_UNIT = registerEe("quantity_unit", "eecore.part.quantity_unit");
    public static final PartType PARALLEL_UNIT = registerEe("parallel_unit", "eecore.part.parallel_unit");
    public static final PartType DISPATCH_ME_PORT = registerEe("dispatch_me_port", "eecore.part.dispatch_me_port");
    public static final PartType DISPATCH_CASING = registerEe("dispatch_casing", "eecore.part.dispatch_casing");

    private final ResourceLocation id;
    private final String translationKey;

    private PartType(ResourceLocation id, String key) { this.id = id; this.translationKey = key; }

    public ResourceLocation getId() { return id; }
    public String getTranslationKey() { return translationKey; }

    /** EECore internal shorthand — namespace auto-set to "eecore". / EECore 内部便捷方法。 */
    private static PartType registerEe(String path, String translationKey) {
        return register(ResourceLocation.fromNamespaceAndPath("eecore", path), translationKey);
    }

    /** Register with bare string (backward compat, namespace defaults to "minecraft"). / 字符串注册（向后兼容）。 */
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
