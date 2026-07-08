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
/**
 * Replaces block models with GlowBakedModel wrappers during model baking.
 * 在模型烘焙阶段将方块模型替换为 GlowBakedModel 包装。
 */
public final class GlowModelRegistration {

    private GlowModelRegistration() {}

    @SubscribeEvent
    public static void onModelBake(ModelEvent.ModifyBakingResult event) {
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
