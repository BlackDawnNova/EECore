package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.network.OpenMbVisPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    public static void openMbVis(OpenMbVisPacket payload) {
        Minecraft.getInstance().setScreen(new MultiblockVisualizerScreen(payload.patternId()));
    }
}
