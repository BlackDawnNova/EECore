package com.endlessepoch.core.registry;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.PartType;
import com.endlessepoch.core.block.creative.CreativeConsumerBlock;
import com.endlessepoch.core.block.creative.CreativeGeneratorBlock;
import com.endlessepoch.core.nova.block.ScannerBoundaryBlock;
import com.endlessepoch.core.nova.block.MachineControllerBlock;
import com.endlessepoch.core.nova.block.ScannerControllerBlock;
import com.endlessepoch.core.nova.block.TransmitterTestBlock;
import com.endlessepoch.core.nova.block.part.CasingBlock;
import com.endlessepoch.core.nova.block.part.PartBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Block registry for EECore.
 * Holds DeferredRegister and supplier entries for all mod blocks.
 * <p>
 * EECore 方块注册表，包含所有模组方块的延迟注册与供应器。
 */
public class Blocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, EECore.MOD_ID);

    public static final Supplier<Block> CREATIVE_GENERATOR = BLOCKS.register(
            "creative_generator",
            () -> new CreativeGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(-1.0f, 3600000.0f)
                    .noLootTable()
                    .noOcclusion()
            )
    );

    public static final Supplier<Block> CREATIVE_CONSUMER = BLOCKS.register(
            "creative_consumer",
            () -> new CreativeConsumerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(-1.0f, 3600000.0f)
                    .noLootTable()
                    .noOcclusion()
            )
    );

    public static final Supplier<Block> SCANNER_CONTROLLER = BLOCKS.register(
            "scanner_controller",
            () -> new ScannerControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.0f)
                    .noOcclusion()
            )
    );

    public static final Supplier<Block> TEST_TRANSMITTER = BLOCKS.register(
            "test_transmitter",
            () -> new TransmitterTestBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(2.0f)
                    .noOcclusion()
            )
    );

    public static final Supplier<Block> MACHINE_CONTROLLER = BLOCKS.register(
            "machine_controller",
            () -> new MachineControllerBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.0f)
                    .noOcclusion()
            )
    );

    public static final Supplier<Block> SCANNER_BOUNDARY = BLOCKS.register(
            "scanner_boundary",
            () -> new ScannerBoundaryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(1.0f)
                    .noOcclusion()
                    .noCollission()
                    .replaceable()
                    .noLootTable()
            )
    );

    // Multiblock parts / 多方块部件（list 在构造器链最前保证初始化顺序）

    /** Accumulated part block suppliers for BlockEntity builder. / 部件方块供应器累积列表。 */
    public static final List<Supplier<? extends Block>> PART_BLOCKS = new ArrayList<>();

    /** Casing tag entries deferred to avoid circular Items class load. / 延迟写入避免循环类加载。 */
    private static final List<String[]> DEFERRED_TAGS = new ArrayList<>();

    /** Called after Items is fully loaded — flush deferred casing tags. / 待 Items 就绪后批量写入标签。 */
    public static void flushCasingTags() {
        for (String[] e : DEFERRED_TAGS)
            Items.addToTag(e[0], e[1]);
        DEFERRED_TAGS.clear();
    }

    // Voltage-tier machine casings / 电压等级机器外壳

    public static final Supplier<? extends Block> ELV_MACHINE_CASING = registerCasing("elv", MapColor.COLOR_GRAY);
    public static final Supplier<? extends Block> LV_MACHINE_CASING  = registerCasing("lv",  MapColor.COLOR_GREEN);
    public static final Supplier<? extends Block> MV_MACHINE_CASING  = registerCasing("mv",  MapColor.COLOR_BLUE);
    public static final Supplier<? extends Block> HV_MACHINE_CASING  = registerCasing("hv",  MapColor.COLOR_ORANGE);
    public static final Supplier<? extends Block> EHV_MACHINE_CASING = registerCasing("ehv", MapColor.COLOR_PURPLE);
    public static final Supplier<? extends Block> UHV_MACHINE_CASING = registerCasing("uhv", MapColor.COLOR_RED);
    public static final Supplier<? extends Block> PHV_MACHINE_CASING = registerCasing("phv", MapColor.COLOR_CYAN);
    public static final Supplier<? extends Block> XHV_MACHINE_CASING = registerCasing("xhv", MapColor.COLOR_YELLOW);
    public static final Supplier<? extends Block> PLV_MACHINE_CASING = registerCasing("plv", MapColor.COLOR_PINK);
    public static final Supplier<? extends Block> SV_MACHINE_CASING  = registerCasing("sv",  MapColor.SNOW);
    public static final Supplier<? extends Block> BV_MACHINE_CASING  = registerCasing("bv",  MapColor.COLOR_BLACK);
    public static final Supplier<? extends Block> QV_MACHINE_CASING  = registerCasing("qv",  MapColor.GOLD);

    private static Supplier<? extends Block> registerCasing(String tierName, MapColor color) {
        int tier = com.endlessepoch.core.api.tier.VoltageTier.fromShortName(tierName).getIndex();
        String id = tierName + "_machine_casing";
        DEFERRED_TAGS.add(new String[]{com.endlessepoch.core.nova.block.part.PartBlock.toolTagForTier(tier), id});
        var sup = BLOCKS.register(id,
                () -> new CasingBlock(BlockBehaviour.Properties.of()
                        .mapColor(color)
                        .strength(3.0f + tier * 3.0f, 6.0f + tier * 3.0f)
                        .requiresCorrectToolForDrops(),
                        PartType.CASING, tier));
        PART_BLOCKS.add(sup);
        return sup;
    }

    /** Deferred item registrations — flushed after Items is ready. / 延迟物品注册。 */
    private static final List<PartItemDef> DEFERRED_ITEMS = new ArrayList<>();
    private record PartItemDef(String path, int tier, String en, String zh) {}

    /** Register block + defer item registration. / 注册方块 + 延迟物品。 */
    public static Supplier<? extends Block> registerPartBlock(String path, int tier, int slots, int fluidCap, long energyCap) {
        return registerPartBlock(path, tier, slots, fluidCap, 0, energyCap, null, null);
    }

    /** Register block + item name (for auto item registration). / 方块+名字（自动注册物品）。 */
    public static Supplier<? extends Block> registerPartBlock(String path, int tier, int slots, int fluidCap, int fluidSlots,
                                                                long energyCap, String en, String zh) {
        return registerPartBlock(path, tier, slots, fluidCap, fluidSlots, energyCap, 1, en, zh);
    }

    /**
     * Full variant with amperage — addon mods register tiered energy hatches in one call,
     * e.g. registerPartBlock("hv_energy_input", 3, 0, 0, 0, 4_000_000, 4, "HV Energy Hatch", "HV能源仓").
     * Unknown PartTypes are auto-registered; abilities resolve by path suffix.
     * 含安培数的完整变体——附属 Mod 一行注册任意等级能源仓。未知 PartType 自动注册，能力按路径后缀识别。
     */
    public static Supplier<? extends Block> registerPartBlock(String path, int tier, int slots, int fluidCap, int fluidSlots,
                                                                long energyCap, int amperage, String en, String zh) {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("eecore", path);
        PartType type = PartType.get(rl);
        if (type == null) type = PartType.register(rl, "block.eecore." + path);
        final PartType fType = type;
        var sup = BLOCKS.register(path,
                () -> new PartBlock(PartBlock.tieredProperties(tier), fType, tier, slots, fluidCap, energyCap, fluidSlots)
                        .amperage(amperage));
        PART_BLOCKS.add(sup);
        if (en != null) DEFERRED_ITEMS.add(new PartItemDef(path, tier, en, zh));
        return sup;
    }

    /** Called after Items.ITEMS is registered — flush deferred part items. / Items 就绪后批量注册物品。 */
    public static void flushPartItems() {
        for (PartItemDef d : DEFERRED_ITEMS) {
            PartReg.registerItem(Items.ITEMS, () -> findPartBlock(d.path), Items.PART_ITEMS,
                    "eecore", d.path, d.tier,
                    "eecore:block/parts/" + d.path + "/overlay_front", d.en, d.zh);
        }
        DEFERRED_ITEMS.clear();
    }

    private static Block findPartBlock(String path) {
        for (var sup : PART_BLOCKS) {
            var b = sup.get();
            if (b instanceof PartBlock pb && pb.getPartType().getId().getPath().equals(path)) return b;
        }
        throw new IllegalStateException("Part block not found: " + path);
    }

    /**
     * Bus slot count by voltage tier — (1 + min(9, tier))², i.e. LV 2×2, MV 3×3, HV 4×4 …
     * capped at 10×10. Addon mods pass this straight into registerPartBlock.
     * 按电压等级的总线槽数公式——(1 + min(9, tier))²：LV 2×2、MV 3×3、HV 4×4……封顶 10×10。
     * 附属 mod 可直接把返回值传给 registerPartBlock。
     */
    public static int busSlotsForTier(int tier) {
        int root = 1 + Math.min(9, Math.max(0, tier));
        return root * root;
    }

    // Built-in test buses hold 16 stacks (1024 items) so batch-pipeline tests can feed 1000+ inputs
    // 内置测试总线 16 槽（1024 个）——批处理管线测试需要一次容纳 1000+ 输入
    public static final Supplier<? extends Block> INPUT_BUS  = registerPartBlock("input_bus",  1, 16, 0, 0, 0, "Input Bus", "输入总线");
    public static final Supplier<? extends Block> OUTPUT_BUS = registerPartBlock("output_bus", 1, 16, 0, 0, 0, "Output Bus", "输出总线");
    /** Phantom infinite item source for pipeline tests (JEI-drag configurable). / 管线测试用幻影无限物品源（JEI 拖拽配置）。 */
    public static final Supplier<? extends Block> CREATIVE_INPUT_BUS =
            registerPartBlock("creative_input_bus", 1, 16, 0, 0, 0, "Creative Input Bus", "创造输入总线");
    /** Void sink — accepts and destroys all outputs, for endless stress runs. / 虚空输出——无限吞噬产物，持续压测用。 */
    public static final Supplier<? extends Block> CREATIVE_OUTPUT_BUS =
            registerPartBlock("creative_output_bus", 1, 4, 0, 0, 0, "Creative Output Bus", "创造输出总线");
    /** Infinite Ω source with GUI-adjustable tier — drives machine effective voltage. / 无限 Ω 源，GUI 调档，决定机器有效电压。 */
    public static final Supplier<? extends Block> CREATIVE_ENERGY_INPUT =
            registerPartBlock("creative_energy_input", 1, 0, 0, 0, 10000, 16, "Creative Energy Input Hatch", "创造能源输入仓");
    /** Void Ω sink — swallows unlimited energy, for generator tests. / 虚空能源输出——无限吞 Ω，测发电机用。 */
    public static final Supplier<? extends Block> CREATIVE_ENERGY_OUTPUT =
            registerPartBlock("creative_energy_output", 1, 0, 0, 0, 10000, 16, "Creative Energy Output Hatch", "创造能源输出仓");
    /** Infinite fluid source — bucket click sets the template. / 无限流体源——用桶灌入即设模板。 */
    public static final Supplier<? extends Block> CREATIVE_FLUID_INPUT =
            registerPartBlock("creative_fluid_input", 1, 0, 16000, 0, 0, "Creative Fluid Input Hatch", "创造流体输入仓");
    /** Void fluid sink. / 虚空流体输出。 */
    public static final Supplier<? extends Block> CREATIVE_FLUID_OUTPUT =
            registerPartBlock("creative_fluid_output", 1, 0, 16000, 0, 0, "Creative Fluid Output Hatch", "创造流体输出仓");
    /** Tier-11 parallel hatch → 16384 parallel via the standard formula, zero extra code. / QV 并行仓——标准公式直接得 16384 并行，零新代码。 */
    public static final Supplier<? extends Block> CREATIVE_PARALLEL_HATCH =
            registerPartBlock("creative_parallel_hatch", 11, 0, 0, 0, 0, "Creative Parallel Hatch", "创造并行仓");
    /** Infinite items + fluid templates in one part — 4×4 items left, 4×4 fluids right. / 无限物品+流体模板二合一——左 4×4 物品、右 4×4 流体。 */
    public static final Supplier<? extends Block> CREATIVE_INPUT_ASSEMBLY =
            registerPartBlock("creative_input_assembly", 1, 16, 16000, 16, 0, "Creative Input Assembly", "创造输入总成");
    /** Void items + fluids in one part. / 物品+流体全吞二合一。 */
    public static final Supplier<? extends Block> CREATIVE_OUTPUT_ASSEMBLY =
            registerPartBlock("creative_output_assembly", 1, 16, 16000, 16, 0, "Creative Output Assembly", "创造输出总成");
    public static final Supplier<? extends Block> FLUID_INPUT  = registerPartBlock("fluid_input",  1, 0, 8000, 0, 0, "Fluid Input Hatch", "流体输入仓");
    public static final Supplier<? extends Block> FLUID_OUTPUT = registerPartBlock("fluid_output", 1, 0, 8000, 0, 0, "Fluid Output Hatch", "流体输出仓");
    public static final Supplier<? extends Block> ENERGY_INPUT  = registerPartBlock("energy_input",  1, 0, 0, 0, 64000, "Energy Input Hatch", "能源输入仓");
    public static final Supplier<? extends Block> ENERGY_OUTPUT = registerPartBlock("energy_output", 1, 0, 0, 0, 64000, "Energy Output Hatch", "能源输出仓");
    public static final Supplier<? extends Block> INPUT_ASSEMBLY  = registerPartBlock("input_assembly",  1, 9, 16000, 9, 0, "Input Assembly", "输入总成");
    public static final Supplier<? extends Block> OUTPUT_ASSEMBLY = registerPartBlock("output_assembly", 1, 9, 16000, 9, 0, "Output Assembly", "输出总成");
    public static final Supplier<? extends Block> PARALLEL_HATCH = registerPartBlock("parallel_hatch", 1, 0, 0, 0, 0, "Parallel Control Hatch", "并行控制仓");

}