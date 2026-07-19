package com.endlessepoch.core.api.multiblock;

import com.endlessepoch.core.nova.block.part.PartBlock;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Functional part categories, matched by PartType path suffix — the same convention
 * that resolves abilities. Any part registered through registerPartBlock (built-in,
 * creative, or addon "hv_input_bus" variants) matches its category automatically, so
 * machine structures bind categories instead of enumerating blocks.
 * 功能部件类别，按 PartType 路径后缀匹配——与能力解析同一套约定。
 * 经 registerPartBlock 注册的任何部件（内置、创造、附属分级变体）自动归类，
 * 机器结构按类绑定，无需逐个点名方块。
 */
public enum PartCategory {
    ITEM_INPUT_BUS("input_bus"),
    ITEM_OUTPUT_BUS("output_bus"),
    INPUT_BIN("input_bin"),
    INPUT_ASSEMBLY("input_assembly"),
    OUTPUT_ASSEMBLY("output_assembly"),
    FLUID_INPUT("fluid_input"),
    FLUID_OUTPUT("fluid_output"),
    ENERGY_INPUT("energy_input"),
    ENERGY_OUTPUT("energy_output"),
    PARALLEL_HATCH("parallel_hatch"),
    /** Any non-casing part. / 任意非外壳功能部件。 */
    ANY_FUNCTIONAL(null);

    private final String suffix;

    PartCategory(String suffix) { this.suffix = suffix; }

    /** Does this part type belong to the category? / 该部件类型是否属于此类别。 */
    public boolean matches(PartType type) {
        String p = type.getId().getPath();
        if (suffix == null) return !p.endsWith("casing");
        return p.endsWith(suffix);
    }

    /** Does this block belong to the category? / 该方块是否属于此类别。 */
    public boolean matches(Block block) {
        return block instanceof PartBlock pb && matches(pb.getPartType());
    }

    /**
     * All currently registered part blocks in this category. Call after block
     * registration (commonSetup or later). / 当前已注册的本类别部件方块。
     * 须在方块注册完成后调用（commonSetup 起）。
     */
    public List<Block> resolve() {
        List<Block> out = new ArrayList<>();
        for (var sup : com.endlessepoch.core.registry.Blocks.PART_BLOCKS) {
            Block b = sup.get();
            if (matches(b)) out.add(b);
        }
        return out;
    }
}
