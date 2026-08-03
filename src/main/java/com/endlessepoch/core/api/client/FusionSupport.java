package com.endlessepoch.core.api.client;

/**
 * Single decision point for optional Fusion rendering enhancements.
 * Fusion (optional mod) provides texture-level emissive / connecting textures;
 * without it EECore falls back to its own baked-model mechanisms.
 * 可选 Fusion 渲染增强的唯一判断点——有则纹理级发光/连接纹理，无则回退自研机制。
 */
public final class FusionSupport {

    private FusionSupport() {}

    /** Whether Fusion is loaded. / Fusion 是否加载。 */
    public static boolean active() {
        return net.neoforged.fml.ModList.get().isLoaded("fusion");
    }
}
