package com.endlessepoch.core.client.model;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.client.EmissiveHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.Map;

/**
 * Wraps registered emissive block models with GlowBakedModel at bake time.
 * <p>
 * 烘焙时自动包装已注册的发光方块模型。
 */
@EventBusSubscriber(modid = EECore.MOD_ID, value = Dist.CLIENT)
public final class GlowModelRegistration {

    private GlowModelRegistration() {}

    @SubscribeEvent
    public static void onModelBake(ModelEvent.ModifyBakingResult event) {
        if (EmissiveHelper.getRegistry().isEmpty()) return;

        Map<ModelResourceLocation, BakedModel> models = event.getModels();

        for (var entry : EmissiveHelper.getRegistry().entrySet()) {
            String blockModelPath = entry.getKey();
            String emissiveTexPath = entry.getValue();

            // Strip namespace if present (e.g. "eecore:scanner_controller" -> "scanner_controller")
            String modelPath = blockModelPath.contains(":")
                    ? blockModelPath.substring(blockModelPath.indexOf(':') + 1)
                    : blockModelPath;

            TextureAtlasSprite sprite = null;
            try {
                var atlas = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
                sprite = atlas.getSprite(ResourceLocation.parse(emissiveTexPath));
            } catch (Exception e) {
                EECore.LOGGER.warn("Could not get emissive sprite: {}", emissiveTexPath);
            }

            // Only pass sprite when glow is enabled (avoids unnecessary processing)
            boolean enableGlow = EmissiveHelper.hasGlow(blockModelPath);
            final TextureAtlasSprite fsprite = enableGlow ? sprite : null;

            models.replaceAll((loc, model) -> {
                if (loc.id().getPath().equals(modelPath)
                        && !"inventory".equals(loc.getVariant())) {
                    return new GlowBakedModel(model, fsprite);
                }
                return model;
            });
        }
    }
}
