package com.endlessepoch.core.event;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.client.model.CasingBakedModel;
import com.endlessepoch.core.client.model.MachineModelLoader;
import com.endlessepoch.core.registry.Menus;
import com.endlessepoch.core.screen.BusScreen;
import com.endlessepoch.core.screen.HatchScreen;
import com.endlessepoch.core.screen.MachineTestScreen;
import com.endlessepoch.core.menu.MachineMenu;
import com.endlessepoch.core.screen.creative.CreativeConsumerScreen;
import com.endlessepoch.core.screen.creative.CreativeGeneratorScreen;
import net.minecraft.client.resources.model.BakedModel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.Map;

@EventBusSubscriber(modid = EECore.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(Menus.CREATIVE_GENERATOR.get(), CreativeGeneratorScreen::new);
        event.register(Menus.CREATIVE_CONSUMER.get(), CreativeConsumerScreen::new);
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

        // Casing blocks: disable AO for seamless tiling / 外壳方块关 AO 无缝拼接
        for (var entry : models.entrySet()) {
            String path = entry.getKey().id().getPath();
            if (path.endsWith("_machine_casing") && !"inventory".equals(entry.getKey().getVariant())) {
                BakedModel model = entry.getValue();
                if (!(model instanceof CasingBakedModel)) {
                    entry.setValue(new CasingBakedModel(model));
                }
            }
        }
    }
}
