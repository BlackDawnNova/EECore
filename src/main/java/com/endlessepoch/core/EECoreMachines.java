package com.endlessepoch.core;

import com.endlessepoch.core.api.multiblock.PartCategory;
import com.endlessepoch.core.api.multiblock.loader.MultiblockLoader;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/** EECore built-in multiblock machine definitions, call registerAll() to register. / EECore 内置多方块机器定义，调用 registerAll() 注册全部。 */
public final class EECoreMachines {

    private EECoreMachines() {}

    /** Call from constructor — register items before DeferredRegister freezes. / 构造器中调用。 */
    public static void registerAll() {
        CREATIVE_TEST.registerItem();
        DISPATCH_CENTER.registerItem();
    }

    /** Call from commonSetup — apply tag bindings after blocks are available. / commonSetup 中调用。 */
    public static void applyBindings() {
        CREATIVE_TEST.applyBindings();
        DISPATCH_CENTER.applyBindings();
    }

    // Machine Definitions / 机器定义

    /** EECore Creative Test Machine / EECore创造测试机 */
    public static final MachineDef CREATIVE_TEST = new MachineDef()
            .ecs("eecore", "d1")
            .name("EECore Creative Test Machine", "EECore创造测试机")
            .tier(1)
            .center(0, 49, 2)
            .effect("eecore:celestial")
            .supports("furnace", "blast_furnace", "machine", "boiler")
            // Category binding: any functional part fits EE-3 — creative and addon
            // variants included, no per-block enumeration.
            // 类别绑定：任意功能部件都可放 EE-3——含创造与附属变体，无需逐个点名。
            .where("EE-3", PartCategory.ANY_FUNCTIONAL)
            // Category-total limits / 类别总量上限（同类别所有方块合计）
            .limit("EE-3", PartCategory.ITEM_INPUT_BUS, 2)
            .limit("EE-3", PartCategory.ITEM_OUTPUT_BUS, 2)
            .limit("EE-3", PartCategory.INPUT_ASSEMBLY, 1)
            .limit("EE-3", PartCategory.OUTPUT_ASSEMBLY, 1)
            .limit("EE-3", PartCategory.FLUID_INPUT, 1)
            .limit("EE-3", PartCategory.FLUID_OUTPUT, 1)
            .limit("EE-3", PartCategory.ENERGY_INPUT, 2)
            .limit("EE-3", PartCategory.ENERGY_OUTPUT, 1)
            .limit("EE-3", PartCategory.PARALLEL_HATCH, 1)
            .out("eecore:creative_test");

    /** Dispatch Center / 调度中心 */
    public static final MachineDef DISPATCH_CENTER = new MachineDef()
            .ecs("eecore", "ceshi")
            .name("Dispatch Center", "调度中心")
            .tier(1)
            .frame("A", 2, 16, 2, 16, 2, 16)
            .where("A", () -> com.endlessepoch.core.registry.Blocks.DISPATCH_CASING.get())
            .where("B", () -> com.endlessepoch.core.registry.Blocks.SUPERCOMPUTING_UNIT.get())
            .or(() -> com.endlessepoch.core.registry.Blocks.PATTERN_UNIT.get())
            .or(() -> com.endlessepoch.core.registry.Blocks.QUANTITY_UNIT.get())
            .or(() -> com.endlessepoch.core.registry.Blocks.PARALLEL_UNIT.get())
            .limit("B", () -> com.endlessepoch.core.registry.Blocks.SUPERCOMPUTING_UNIT.get(), 4)
            .out("eecore:dispatch_center");

    // Internal helper / 内部辅助

    /** Holds machine registration parameters. / 持有机器注册参数。 */
    private static class MachineDef {
        private String ecsNs, ecsPath, nameEn, nameZh, outNs, outPath;
        private int tier;
        private String effect;
        private String[] supported;
        private float cx, cy, cz;
        private boolean cSet;
        private boolean frameBased;
        private String frameCasingTag;
        private int frameMinW=2, frameMaxW=32, frameMinH=2, frameMaxH=16, frameMinD=2, frameMaxD=32;
        private String lastTag;
        // Tag → block suppliers / 标签→方块供应器
        private final java.util.Map<String, java.util.List<Supplier<? extends net.minecraft.world.level.block.Block>>> tagSuppliers = new java.util.LinkedHashMap<>();
        // Tag → part categories / 标签→部件类别
        private final java.util.Map<String, java.util.Set<PartCategory>> tagCategories = new java.util.LinkedHashMap<>();
        // Tag → (block supplier → maxCount) / 标签→(供应器→上限)
        private final java.util.List<LimEntry> limits = new java.util.ArrayList<>();
        private record LimEntry(String tag, Supplier<? extends net.minecraft.world.level.block.Block> sup, int max) {}
        // Tag → (category → maxCount) / 标签→(类别→总量上限)
        private final java.util.List<CatLimEntry> catLimits = new java.util.ArrayList<>();
        private record CatLimEntry(String tag, PartCategory cat, int max) {}

        MachineDef ecs(String ns, String path) { ecsNs = ns; ecsPath = path; return this; }
        MachineDef name(String en, String zh) { nameEn = en; nameZh = zh; return this; }
        MachineDef tier(int t) { tier = t; return this; }
        MachineDef effect(String e) { effect = e; return this; }
        MachineDef supports(String... ids) { supported = ids; return this; }
        MachineDef center(float x, float y, float z) { cx = x; cy = y; cz = z; cSet = true; return this; }
        MachineDef frame(String casingTag, int minW, int maxW, int minH, int maxH, int minD, int maxD) {
            this.frameBased=true; this.frameCasingTag=casingTag;
            this.frameMinW=minW; this.frameMaxW=maxW; this.frameMinH=minH; this.frameMaxH=maxH;
            this.frameMinD=minD; this.frameMaxD=maxD; return this;
        }
        MachineDef out(String id) {
            var rl = ResourceLocation.parse(id);
            outNs = rl.getNamespace(); outPath = rl.getPath(); return this;
        }
        @SafeVarargs
        final MachineDef where(String tag, Supplier<? extends net.minecraft.world.level.block.Block>... blocks) {
            lastTag = tag;
            tagSuppliers.computeIfAbsent(tag, k -> new java.util.ArrayList<>())
                    .addAll(java.util.Arrays.asList(blocks));
            return this;
        }
        @SafeVarargs
        final MachineDef or(Supplier<? extends net.minecraft.world.level.block.Block>... blocks) {
            if (lastTag != null)
                tagSuppliers.computeIfAbsent(lastTag, k -> new java.util.ArrayList<>())
                        .addAll(java.util.Arrays.asList(blocks));
            return this;
        }
        MachineDef limit(String tag, Supplier<? extends net.minecraft.world.level.block.Block> block, int maxCount) {
            limits.add(new LimEntry(tag, block, maxCount));
            return this;
        }
        /** Category binding — every registered block in the category. / 类别绑定——该类别所有已注册方块。 */
        MachineDef where(String tag, PartCategory... cats) {
            lastTag = tag;
            tagCategories.computeIfAbsent(tag, k -> new java.util.LinkedHashSet<>())
                    .addAll(java.util.Arrays.asList(cats));
            return this;
        }
        MachineDef or(PartCategory... cats) {
            if (lastTag != null)
                tagCategories.computeIfAbsent(lastTag, k -> new java.util.LinkedHashSet<>())
                        .addAll(java.util.Arrays.asList(cats));
            return this;
        }
        /** Category-total limit. / 类别总量上限。 */
        MachineDef limit(String tag, PartCategory cat, int maxCount) {
            catLimits.add(new CatLimEntry(tag, cat, maxCount));
            return this;
        }

        private ResourceLocation getOutId() {
            return ResourceLocation.fromNamespaceAndPath(outNs != null ? outNs : EECore.MOD_ID,
                    outPath != null ? outPath : nameEn);
        }

        /** Phase 1 — register item (constructor, before DeferredRegister freeze). / 阶段1注册物品。 */
        void registerItem() {
            var b = MultiblockLoader.load(ResourceLocation.fromNamespaceAndPath(ecsNs, ecsPath))
                    .name(nameEn, nameZh)
                    .tier(tier);
            if (effect != null) b.effect(effect);
            if (cSet) b.center(cx, cy, cz);
            if (supported != null) b.supports(supported);
            if (frameBased) b.frame(frameCasingTag, frameMinW, frameMaxW, frameMinH, frameMaxH, frameMinD, frameMaxD);
            b.register(getOutId());
        }

        /** Phase 2 — apply tag bindings (commonSetup, after blocks available). / 阶段2绑定标签。 */
        void applyBindings() {
            var def = com.endlessepoch.core.api.multiblock.MachineRegistry.get(getOutId());
            if (def.isEmpty()) return;
            var pat = def.get().getPattern();
            if (pat.isEmpty()) return;
            var pattern = pat.get();
            for (var e : tagSuppliers.entrySet()) {
                for (var sup : e.getValue()) {
                    var block = sup.get();
                    for (char c : pattern.getDefinitions().keySet())
                        if (pattern.getTags(c).contains(e.getKey()))
                            pattern.addAlternatives(c, block.defaultBlockState());
                }
            }
            // Expand category bindings / 展开类别绑定
            for (var e : tagCategories.entrySet()) {
                for (var cat : e.getValue())
                    for (var block : cat.resolve())
                        for (char c : pattern.getDefinitions().keySet())
                            if (pattern.getTags(c).contains(e.getKey()))
                                pattern.addAlternatives(c, block.defaultBlockState());
            }
            for (var le : limits)
                pattern.setBlockLimit(le.tag, le.sup.get(), le.max);
            for (var ce : catLimits)
                pattern.setCategoryLimit(ce.tag, ce.cat, ce.max);
        }
    }
}
