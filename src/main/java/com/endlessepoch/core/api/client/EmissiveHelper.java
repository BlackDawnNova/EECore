package com.endlessepoch.core.api.client;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * API for registering block models with emissive glow rendering.
 * <p>
 * 注册方块模型的发光渲染效果 API。
 */
@ApiStatus.AvailableSince("0.1.1")
public final class EmissiveHelper {

    private static final Map<String, String> REGISTRY = new HashMap<>();
    private static final Map<String, Boolean> GLOW_FLAGS = new HashMap<>();

    private EmissiveHelper() {}

    /** Register with UV-offset glow enabled by default. / 默认启用 UV 偏移辉光。 */
    public static void registerEmissiveModel(ResourceLocation blockModelId, ResourceLocation emissiveTextureId) {
        registerEmissiveModel(blockModelId.toString(), emissiveTextureId.toString(), true);
    }

    /** @see #registerEmissiveModel(ResourceLocation, ResourceLocation) */
    public static void registerEmissiveModel(String blockModelPath, String emissiveTexturePath) {
        registerEmissiveModel(blockModelPath, emissiveTexturePath, true);
    }

    /**
     * Register for emissive rendering.
     *
     * @param enableGlow false = only cutoutMipped + neoforge_data brightness,
     *                   no UV-offset glow duplicates / 不生成 UV 偏移辉光
     */
    public static void registerEmissiveModel(String blockModelPath, String emissiveTexturePath, boolean enableGlow) {
        REGISTRY.put(blockModelPath, emissiveTexturePath);
        GLOW_FLAGS.put(blockModelPath, enableGlow);
    }

    @ApiStatus.Internal
    public static Map<String, String> getRegistry() { return REGISTRY; }

    @ApiStatus.Internal
    public static boolean hasGlow(String modelPath) {
        return GLOW_FLAGS.getOrDefault(modelPath, true);
    }
}
