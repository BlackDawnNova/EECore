package com.endlessepoch.core.api.multiblock;

import com.endlessepoch.core.nova.block.part.PartBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.*;

/**
 * Functional part categories, matched by PartType path suffix.
 * Built-in categories cover standard parts; addon mods call {@link #register}.
 * 功能部件类别，按 PartType 路径后缀匹配。内置涵盖标准部件；附属Mod调用 register 注册。
 */
public final class PartCategory {

    private static final Map<ResourceLocation, PartCategory> REGISTRY = new LinkedHashMap<>();

    public static final PartCategory ITEM_INPUT_BUS  = registerEe("input_bus");
    public static final PartCategory ITEM_OUTPUT_BUS = registerEe("output_bus");
    public static final PartCategory INPUT_BIN       = registerEe("input_bin");
    public static final PartCategory INPUT_ASSEMBLY  = registerEe("input_assembly");
    public static final PartCategory OUTPUT_ASSEMBLY = registerEe("output_assembly");
    public static final PartCategory FLUID_INPUT     = registerEe("fluid_input");
    public static final PartCategory FLUID_OUTPUT    = registerEe("fluid_output");
    public static final PartCategory ENERGY_INPUT    = registerEe("energy_input");
    public static final PartCategory ENERGY_OUTPUT   = registerEe("energy_output");
    public static final PartCategory PARALLEL_HATCH  = registerEe("parallel_hatch");
    public static final PartCategory DISPATCH_UNIT   = registerEe("supercomputing_unit", "pattern_unit", "quantity_unit", "parallel_unit");
    public static final PartCategory QUANTITY_UNIT_CAT = registerEe("quantity_unit");
    public static final PartCategory ANY_FUNCTIONAL  = register(ResourceLocation.fromNamespaceAndPath("eecore", "any_functional"));

    private final ResourceLocation id;
    private final String[] suffixes;

    private PartCategory(ResourceLocation id, String... suffixes) {
        this.id = id; this.suffixes = suffixes;
    }

    private static PartCategory registerEe(String... suffixes) {
        return register(ResourceLocation.fromNamespaceAndPath("eecore",
                suffixes.length == 1 ? suffixes[0] : "dispatch_unit"), suffixes);
    }

    public static PartCategory register(ResourceLocation id, String... suffixes) {
        PartCategory cat = new PartCategory(id, suffixes);
        REGISTRY.put(id, cat);
        return cat;
    }

    public ResourceLocation getId() { return id; }

    public boolean matches(PartType type) {
        String p = type.getId().getPath();
        if (suffixes == null || suffixes.length == 0) return !p.contains("casing");
        for (String s : suffixes) if (p.endsWith(s)) return true;
        return false;
    }

    public boolean matches(Block block) {
        return block instanceof PartBlock pb && matches(pb.getPartType());
    }

    public List<Block> resolve() {
        List<Block> out = new ArrayList<>();
        for (var sup : com.endlessepoch.core.registry.Blocks.PART_BLOCKS) {
            Block b = sup.get();
            if (matches(b)) out.add(b);
        }
        return out;
    }

    public static Collection<PartCategory> values() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }
}
