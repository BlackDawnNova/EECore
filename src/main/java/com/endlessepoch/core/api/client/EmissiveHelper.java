package com.endlessepoch.core.api.client;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * API for registering block models with emissive cutoutMipped rendering.
 * <p>
 * Makes alpha textures render correctly — pixels with alpha &lt; 0.5 are discarded.
 * The model JSON uses {@code neoforge_data} for full brightness.
 * <p>
 * 注册方块模型的 emissive 渲染，自动使用 cutoutMipped 渲染层处理透明纹理。
 * 模型 JSON 通过 neoforge_data 实现全亮度发光。
 */
@ApiStatus.AvailableSince("0.1.1")
public final class EmissiveHelper {

    private static final Map<String, String> REGISTRY = new HashMap<>();

    private EmissiveHelper() {}

    /**
     * Register a block model for emissive cutoutMipped rendering.
     *
     * @param blockModelId      the block model resource path (e.g. "addon:block/my_machine")
     * @param emissiveTextureId the emissive texture resource path (e.g. "addon:block/my_machine_e")
     */
    public static void registerEmissiveModel(ResourceLocation blockModelId, ResourceLocation emissiveTextureId) {
        REGISTRY.put(blockModelId.toString(), emissiveTextureId.toString());
    }

    /**
     * @see #registerEmissiveModel(ResourceLocation, ResourceLocation)
     */
    public static void registerEmissiveModel(String blockModelPath, String emissiveTexturePath) {
        REGISTRY.put(blockModelPath, emissiveTexturePath);
    }

    @ApiStatus.Internal
    public static Map<String, String> getRegistry() {
        return REGISTRY;
    }
}
