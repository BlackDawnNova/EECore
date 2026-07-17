package com.endlessepoch.core.event;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.client.model.CasingBakedModel;
import com.endlessepoch.core.client.model.MachineModelLoader;
import com.endlessepoch.core.client.model.OreBakedModel;
import com.endlessepoch.core.registry.Menus;
import com.endlessepoch.core.screen.BusScreen;
import com.endlessepoch.core.screen.HatchScreen;
import com.endlessepoch.core.screen.MachineTestScreen;
import com.endlessepoch.core.screen.creative.CreativeConsumerScreen;
import com.endlessepoch.core.screen.creative.CreativeGeneratorScreen;
import net.minecraft.client.resources.model.BakedModel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = EECore.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(Menus.CREATIVE_GENERATOR.get(), CreativeGeneratorScreen::new);
        event.register(Menus.CREATIVE_CONSUMER.get(), CreativeConsumerScreen::new);
        event.register(Menus.CREATIVE_HATCH.get(), com.endlessepoch.core.screen.creative.CreativeHatchScreen::new);
        event.register(Menus.CREATIVE_PARALLEL.get(), com.endlessepoch.core.screen.creative.CreativeParallelScreen::new);
        event.register(Menus.CREATIVE_VOID.get(), com.endlessepoch.core.screen.creative.CreativeVoidScreen::new);
        event.register(Menus.BUS.get(), BusScreen::new);
        event.register(Menus.MACHINE.get(), MachineTestScreen::new);
        event.register(Menus.HATCH.get(), HatchScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(MachineModelLoader.ID, new MachineModelLoader());
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        for (var entry : models.entrySet()) {
            String path = entry.getKey().id().getPath();
            if (path.endsWith("_machine_casing") && !"inventory".equals(entry.getKey().getVariant())) {
                BakedModel model = entry.getValue();
                if (!(model instanceof CasingBakedModel)) {
                    entry.setValue(new CasingBakedModel(model));
                }
            }
            // Wrap ore models with dynamic composite renderer / 矿石用动态合成模型
            if (path.endsWith("_ore") && entry.getKey().id().getNamespace().equals(EECore.MOD_ID)
                    && !path.contains("crushed") && !path.contains("purified") && !path.contains("refined")
                    && !"inventory".equals(entry.getKey().getVariant())) {
                BakedModel model = entry.getValue();
                if (!(model instanceof OreBakedModel)) {
                    entry.setValue(new OreBakedModel(model));
                }
            }
        }
    }
}
