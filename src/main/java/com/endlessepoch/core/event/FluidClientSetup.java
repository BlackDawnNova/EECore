package com.endlessepoch.core.event;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.registry.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = EECore.MOD_ID, value = Dist.CLIENT)
public class FluidClientSetup {
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        Fluids.registerClient(event);
    }
}
