package com.endlessepoch.core;

import com.endlessepoch.core.api.multiblock.PartCategory;
import com.endlessepoch.core.api.multiblock.loader.FrameMachineLoader;
import com.endlessepoch.core.api.multiblock.loader.MultiblockLoader;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.function.Supplier;

/** EECore built-in multiblock machine definitions. Call {@link #init} from EECore constructor. / EECore 内置多方块机器定义，在构造器中调用 init 即可。 */
public final class EECoreMachines {

    private EECoreMachines() {}

    /**
     * Register machine items (constructor phase) + bind tags (commonSetup phase).
     * 注册机器物品（构造器阶段）+ 绑定标签（commonSetup 阶段）。
     */
    public static void init(IEventBus modEventBus) {
        registerAll();
        modEventBus.addListener(EECoreMachines::onCommonSetup);
    }

    private static void registerAll() {
        CREATIVE_TEST.registerItem();
        DISPATCH_CENTER.registerItem();
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
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
            .where("EE-3", PartCategory.ANY_FUNCTIONAL)
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

    public static final FrameMachineDef DISPATCH_CENTER = new FrameMachineDef()
            .ecs("eecore", "ddzx")
            .name("Dispatch Center", "调度中心")
            .tier(11)
            .frame("A", 11, 11, 11)
            .where("A", () -> com.endlessepoch.core.registry.Blocks.DISPATCH_CASING.get())
            .or(() -> com.endlessepoch.core.registry.Blocks.DISPATCH_CASING_II.get())
            .or(() -> com.endlessepoch.core.registry.Blocks.DISPATCH_ME_PORT.get())
            .where("B", PartCategory.DISPATCH_UNIT)
            .limit("A", () -> com.endlessepoch.core.registry.Blocks.DISPATCH_ME_PORT.get(), 1)
            .limit("B", PartCategory.QUANTITY_UNIT_CAT, 2)
            .out("eecore:dispatch_center");

    // Internal helpers / 内部辅助

    /** Holds fixed-format machine registration parameters. / 持有固定式机器注册参数。 */
    private static class MachineDef {
        private String ecsNs, ecsPath, nameEn, nameZh, outNs, outPath;
        private int tier;
        private String effect;
        private String[] supported;
        private float cx, cy, cz;
        private boolean cSet;
        private String lastTag;
        private final java.util.Map<String, java.util.List<Supplier<? extends net.minecraft.world.level.block.Block>>> tagSuppliers = new java.util.LinkedHashMap<>();
        private final java.util.Map<String, java.util.Set<PartCategory>> tagCategories = new java.util.LinkedHashMap<>();
        private final java.util.List<LimEntry> limits = new java.util.ArrayList<>();
        private record LimEntry(String tag, Supplier<? extends net.minecraft.world.level.block.Block> sup, int max) {}
        private final java.util.List<CatLimEntry> catLimits = new java.util.ArrayList<>();
        private record CatLimEntry(String tag, PartCategory cat, int max) {}

        MachineDef ecs(String ns, String path) { ecsNs = ns; ecsPath = path; return this; }
        MachineDef name(String en, String zh) { nameEn = en; nameZh = zh; return this; }
        MachineDef tier(int t) { tier = t; return this; }
        MachineDef effect(String e) { effect = e; return this; }
        MachineDef supports(String... ids) { supported = ids; return this; }
        MachineDef center(float x, float y, float z) { cx = x; cy = y; cz = z; cSet = true; return this; }
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
        MachineDef limit(String tag, PartCategory cat, int maxCount) {
            catLimits.add(new CatLimEntry(tag, cat, maxCount));
            return this;
        }

        private ResourceLocation getOutId() {
            return ResourceLocation.fromNamespaceAndPath(outNs != null ? outNs : EECore.MOD_ID,
                    outPath != null ? outPath : nameEn);
        }

        void registerItem() {
            var b = MultiblockLoader.load(ResourceLocation.fromNamespaceAndPath(ecsNs, ecsPath))
                    .name(nameEn, nameZh)
                    .tier(tier);
            if (effect != null) b.effect(effect);
            if (cSet) b.center(cx, cy, cz);
            if (supported != null) b.supports(supported);
            b.register(getOutId());
        }

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

    private static class FrameMachineDef {
        private String ecsNs, ecsPath, nameEn, nameZh, outNs, outPath;
        private int tier;
        private String effect;
        private String frameCasingTag;
        private int innerW, innerH, innerD;
        private String lastTag;
        private final java.util.Map<String, java.util.List<Supplier<? extends net.minecraft.world.level.block.Block>>> tagSuppliers = new java.util.LinkedHashMap<>();
        private final java.util.Map<String, java.util.Set<PartCategory>> tagCategories = new java.util.LinkedHashMap<>();
        private final java.util.List<FmLimEntry> limits = new java.util.ArrayList<>();
        private record FmLimEntry(String tag, Supplier<? extends net.minecraft.world.level.block.Block> sup, int max) {}
        private final java.util.List<FmCatLimEntry> catLimits = new java.util.ArrayList<>();
        private record FmCatLimEntry(String tag, PartCategory cat, int max) {}

        FrameMachineDef ecs(String ns, String path) { ecsNs = ns; ecsPath = path; return this; }
        FrameMachineDef name(String en, String zh) { nameEn = en; nameZh = zh; return this; }
        FrameMachineDef tier(int t) { tier = t; return this; }
        FrameMachineDef effect(String e) { effect = e; return this; }
        FrameMachineDef frame(String casingTag, int innerW, int innerH, int innerD) {
            this.frameCasingTag=casingTag; this.innerW=innerW; this.innerH=innerH; this.innerD=innerD; return this;
        }
        FrameMachineDef out(String id) {
            var rl = ResourceLocation.parse(id);
            outNs = rl.getNamespace(); outPath = rl.getPath(); return this;
        }
        @SafeVarargs
        final FrameMachineDef where(String tag, Supplier<? extends net.minecraft.world.level.block.Block>... blocks) {
            lastTag = tag;
            tagSuppliers.computeIfAbsent(tag, k -> new java.util.ArrayList<>()).addAll(java.util.Arrays.asList(blocks));
            return this;
        }
        @SafeVarargs
        final FrameMachineDef or(Supplier<? extends net.minecraft.world.level.block.Block>... blocks) {
            if (lastTag != null)
                tagSuppliers.computeIfAbsent(lastTag, k -> new java.util.ArrayList<>()).addAll(java.util.Arrays.asList(blocks));
            return this;
        }
        FrameMachineDef limit(String tag, Supplier<? extends net.minecraft.world.level.block.Block> block, int maxCount) {
            limits.add(new FmLimEntry(tag, block, maxCount));
            return this;
        }
        FrameMachineDef where(String tag, PartCategory... cats) {
            lastTag = tag;
            tagCategories.computeIfAbsent(tag, k -> new java.util.LinkedHashSet<>()).addAll(java.util.Arrays.asList(cats));
            return this;
        }
        FrameMachineDef or(PartCategory... cats) {
            if (lastTag != null)
                tagCategories.computeIfAbsent(lastTag, k -> new java.util.LinkedHashSet<>()).addAll(java.util.Arrays.asList(cats));
            return this;
        }
        FrameMachineDef limit(String tag, PartCategory cat, int maxCount) {
            catLimits.add(new FmCatLimEntry(tag, cat, maxCount));
            return this;
        }

        private ResourceLocation getOutId() {
            return ResourceLocation.fromNamespaceAndPath(outNs != null ? outNs : EECore.MOD_ID,
                    outPath != null ? outPath : nameEn);
        }

        void registerItem() {
            var b = FrameMachineLoader.load(ResourceLocation.fromNamespaceAndPath(ecsNs, ecsPath))
                    .name(nameEn, nameZh).tier(tier).frame(frameCasingTag, innerW, innerH, innerD);
            if (effect != null) b.effect(effect);
            b.register(getOutId());
        }

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
