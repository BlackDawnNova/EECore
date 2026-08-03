package com.endlessepoch.core.client.model;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.client.EmissiveHelper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.Map;

@EventBusSubscriber(modid = EECore.MOD_ID, value = Dist.CLIENT)
public final class GlowModelRegistration {

    private GlowModelRegistration() {}

    @SubscribeEvent
    public static void onModelBake(ModelEvent.ModifyBakingResult event) {
        // Fusion provides texture-level emissive (full bright) — GlowBakedModel wrapping
        // conflicts with its render pipeline (all _e blocks rendered as air).
        // Skip wrapping when Fusion is loaded; fall back to GlowBakedModel otherwise.
        // Fusion 提供纹理级满亮——GlowBakedModel 包裹与其渲染管线冲突（_e 方块渲染为空气）。
        // 有 Fusion 时跳过包裹，无 Fusion 时回退 GlowBakedModel。
        if (com.endlessepoch.core.api.client.FusionSupport.active()) return;
        if (EmissiveHelper.getRegistry().isEmpty()) return;

        Map<ModelResourceLocation, BakedModel> models = event.getModels();

        for (var entry : EmissiveHelper.getRegistry().entrySet()) {
            String blockModelPath = entry.getKey();
            String modelPath = blockModelPath.contains(":")
                    ? blockModelPath.substring(blockModelPath.indexOf(':') + 1)
                    : blockModelPath;

            models.replaceAll((loc, model) -> {
                if (loc.id().getPath().equals(modelPath)
                        && !"inventory".equals(loc.getVariant())) {
                    return new GlowBakedModel(model);
                }
                return model;
            });
        }
    }
}
