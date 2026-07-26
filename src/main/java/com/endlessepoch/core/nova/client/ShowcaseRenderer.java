package com.endlessepoch.core.nova.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = com.endlessepoch.core.EECore.MOD_ID, value = Dist.CLIENT)
public final class ShowcaseRenderer {
    private ShowcaseRenderer() {}

    private static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath("eecore", "celestial_test");
    public static boolean enabled;

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (!enabled) return;
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        var shader = foundry.veil.api.client.render.VeilRenderSystem.setShader(SHADER);
        if (shader == null) return;
        var ua = (foundry.veil.api.client.render.shader.program.UniformAccess) shader;
        setF(ua, "SunX", -1); setF(ua, "SunY", -1);
        setF(ua, "MoonX", -1); setF(ua, "MoonY", -1);
        setF(ua, "BhX", -1); setF(ua, "BhY", -1);
        setF(ua, "BhDist", 999); setF(ua, "Time", 0); setF(ua, "AspectRatio", 1);
        setF(ua, "RingN", 0);
        RenderSystem.disableBlend();
        shader.bind();
        foundry.veil.api.client.render.VeilRenderSystem.drawScreenQuad();
        foundry.veil.api.client.render.shader.program.ShaderProgram.unbind();
    }

    private static void setF(foundry.veil.api.client.render.shader.program.UniformAccess s, String n, float v) {
        var u = s.getUniform(n); if (u != null) u.setFloat(v);
    }
}
