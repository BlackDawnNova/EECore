package com.endlessepoch.core;

import com.endlessepoch.core.api.multiblock.loader.MultiblockLoader;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/** EECore built-in multiblock machine definitions, call registerAll() to register. / EECore 内置多方块机器定义，调用 registerAll() 注册全部。 */
public final class EECoreMachines {

    private EECoreMachines() {}

    /** Call from constructor — register items before DeferredRegister freezes. / 构造器中调用。 */
    public static void registerAll() {
        CREATIVE_TEST.registerItem();
    }

    /** Call from commonSetup — apply tag bindings after blocks are available. / commonSetup 中调用。 */
    public static void applyBindings() {
        CREATIVE_TEST.applyBindings();
    }

    // Machine Definitions / 机器定义

    /** EECore Creative Test Machine / EECore创造测试机 */
    public static final MachineDef CREATIVE_TEST = new MachineDef()
            .ecs("eecore", "d1")
            .name("EECore Creative Test Machine", "EECore创造测试机")
            .tier(1)
            .center(0, 49, 2)
            .effect("eecore:celestial")
            .where("EE-3", com.endlessepoch.core.registry.Blocks.INPUT_BUS)
                .or(com.endlessepoch.core.registry.Blocks.OUTPUT_BUS)
            .limit("EE-3", com.endlessepoch.core.registry.Blocks.INPUT_BUS, 2)
            .limit("EE-3", com.endlessepoch.core.registry.Blocks.OUTPUT_BUS, 1)
            .out("eecore:creative_test");

    // Internal helper / 内部辅助

    /** Holds machine registration parameters. / 持有机器注册参数。 */
    private static class MachineDef {
        private String ecsNs, ecsPath, nameEn, nameZh, outNs, outPath;
        private int tier;
        private String effect;
        private float cx, cy, cz;
        private boolean cSet;
        private String lastTag;
        // Tag → block suppliers / 标签→方块供应器
        private final java.util.Map<String, java.util.List<Supplier<? extends net.minecraft.world.level.block.Block>>> tagSuppliers = new java.util.LinkedHashMap<>();
        // Tag → (block supplier → maxCount) / 标签→(供应器→上限)
        private final java.util.List<LimEntry> limits = new java.util.ArrayList<>();
        private record LimEntry(String tag, Supplier<? extends net.minecraft.world.level.block.Block> sup, int max) {}

        MachineDef ecs(String ns, String path) { ecsNs = ns; ecsPath = path; return this; }
        MachineDef name(String en, String zh) { nameEn = en; nameZh = zh; return this; }
        MachineDef tier(int t) { tier = t; return this; }
        MachineDef effect(String e) { effect = e; return this; }
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
            for (var le : limits)
                pattern.setBlockLimit(le.tag, le.sup.get(), le.max);
        }
    }
}
