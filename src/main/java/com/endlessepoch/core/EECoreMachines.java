package com.endlessepoch.core;

import com.endlessepoch.core.api.multiblock.loader.MultiblockLoader;
import net.minecraft.resources.ResourceLocation;

/** EECore built-in multiblock machine definitions, call registerAll() to register. / EECore 内置多方块机器定义，调用 registerAll() 注册全部。 */
public final class EECoreMachines {

    private EECoreMachines() {}

    /** Call from commonSetup to register all built-in machines. / 在 commonSetup 调用注册全部内置机器。 */
    public static void registerAll() {
        CREATIVE_TEST.register();
    }

    // Machine Definitions / 机器定义

    /** EECore Creative Test Machine / EECore创造测试机 */
    public static final MachineDef CREATIVE_TEST = new MachineDef()
            .ecs("eecore", "d1")
            .name("EECore Creative Test Machine", "EECore创造测试机")
            .tier(1)
            .center(0, 49, 2)
            .effect("eecore:celestial")
            .out("eecore:creative_test");

    // Internal helper / 内部辅助

    /** Holds machine registration parameters. / 持有机器注册参数。 */
    private static class MachineDef {
        private String ecsNs, ecsPath, nameEn, nameZh, outNs, outPath;
        private int tier;
        private String effect;
        private float cx, cy, cz;
        private boolean cSet;

        MachineDef ecs(String ns, String path) { ecsNs = ns; ecsPath = path; return this; }
        MachineDef name(String en, String zh) { nameEn = en; nameZh = zh; return this; }
        MachineDef tier(int t) { tier = t; return this; }
        MachineDef effect(String e) { effect = e; return this; }
        MachineDef center(float x, float y, float z) { cx = x; cy = y; cz = z; cSet = true; return this; }
        MachineDef out(String id) {
            var rl = ResourceLocation.parse(id);
            outNs = rl.getNamespace(); outPath = rl.getPath(); return this;
        }

        void register() {
            var b = MultiblockLoader.load(ResourceLocation.fromNamespaceAndPath(ecsNs, ecsPath))
                    .name(nameEn, nameZh)
                    .tier(tier);
            if (effect != null) b.effect(effect);
            if (cSet) b.center(cx, cy, cz);
            b.register(ResourceLocation.fromNamespaceAndPath(outNs != null ? outNs : EECore.MOD_ID,
                    outPath != null ? outPath : nameEn));
        }
    }
}
