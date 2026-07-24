package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.EECore;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.util.Random;

@EventBusSubscriber(modid = EECore.MOD_ID, value = Dist.CLIENT)
public final class BlackholeRenderer {
    private BlackholeRenderer() {}

    public static float currentScale = 9.0f;
    public static boolean showcaseMode;

    private static ShaderInstance shader;
    private static DynamicTexture noiseTex;
    private static int scFbo, scColor, scW, scH;
    private static int halfFbo, halfColor, halfW, halfH;
    private static final int N = 256;

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent e) throws IOException {
        e.registerShader(new ShaderInstance(e.getResourceProvider(),
            ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "blackhole"),
            DefaultVertexFormat.POSITION), i -> shader = i);
    }

    @EventBusSubscriber(modid = EECore.MOD_ID, value = Dist.CLIENT)
    public static final class Render {
        @SubscribeEvent
        public static void onRender(RenderLevelStageEvent e) {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
            if (shader == null) return;
            if (com.endlessepoch.core.Config.p4DisableEffects) return;
            var mc = Minecraft.getInstance();
            if (mc.level == null) return;
            var main = mc.getMainRenderTarget();
            if (main == null) return;

            int fw = main.width, fh = main.height;
            int hw = Math.max(1, fw / 2), hh = Math.max(1, fh / 2);
            ensureFbo(fw, fh, hw, hh);

            var cam = e.getCamera();
            var cp = cam.getPosition();
            float px = e.getPartialTick().getGameTimeDeltaPartialTick(false);

            var look = cam.getEntity().getViewVector(px);
            var up = cam.getEntity().getUpVector(px);
            Matrix4f mv = new Matrix4f().lookAt(0, 0, 0,
                (float) look.x, (float) look.y, (float) look.z,
                (float) up.x, (float) up.y, (float) up.z);
            Matrix4f proj = new Matrix4f(e.getProjectionMatrix());
            Matrix4f invPV = new Matrix4f(proj).mul(mv).invert();
            float time = (float) (mc.level.getGameTime() / 20.0 + px / 20.0);

            float bhx = 0.5f, bhy = 60f, bhz = 0.5f;
            float bhScale = currentScale;

            int fid = main.frameBufferId;
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fid);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scFbo);
            GL30.glBlitFramebuffer(0, 0, fw, fh, 0, 0, fw, fh, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fid);

            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableScissor();

            shader.setSampler("MainDepthSampler", scColor);
            shader.setSampler("MainColorSampler", scColor);
            shader.setSampler("TextureSampler", noiseTex.getId());
            shader.setSampler("ColorSampler", noiseTex.getId());

            shader.safeGetUniform("projectionMatrix").set(proj);
            shader.safeGetUniform("modelViewMatrix").set(mv);
            shader.safeGetUniform("invPV").set(invPV);
            shader.safeGetUniform("cameraPos").set((float) cp.x, (float) cp.y, (float) cp.z);
            shader.safeGetUniform("time").set(time);
            shader.safeGetUniform("screenSize").set((float) hw, (float) hh);
            shader.safeGetUniform("noiseTextureSize").set((float) N);

            shader.safeGetUniform("entityPos").set(bhx, bhy, bhz);
            shader.safeGetUniform("scale").set(bhScale);
            shader.safeGetUniform("intensity").set(0.6f);
            shader.safeGetUniform("accretionDiskRadiusScale").set(1.0f);
            shader.safeGetUniform("accretionDiskThicknessScale").set(1.0f);
            shader.safeGetUniform("accretionDiskDensity").set(0.003f);
            shader.safeGetUniform("tiltAngle").set(0.0f);
            shader.safeGetUniform("renderQuality").set(1.35f);
            shader.safeGetUniform("ditherStrength").set(0.7f);
            shader.safeGetUniform("lensBoundarySoftness").set(0.6f);
            shader.safeGetUniform("diskNoiseStrength").set(1.0f);
            shader.safeGetUniform("diskTextureStrength").set(0.35f);
            shader.safeGetUniform("coreRadiusScale").set(0.2f);
            shader.safeGetUniform("accretionDiskColor").set(1.0f, 0.7f, 0.1f);
            shader.safeGetUniform("accretionDiskInnerColor").set(1.0f, 0.9f, 0.5f);
            shader.safeGetUniform("accretionDiskOuterColor").set(1.0f, 0.4f, 0.05f);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, halfFbo);
            GL30.glViewport(0, 0, hw, hh);
            shader.apply();
            var tess = Tesselator.getInstance();
            var buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            buf.addVertex(-1, -1, 0); buf.addVertex(1, -1, 0);
            buf.addVertex(1, 1, 0); buf.addVertex(-1, 1, 0);
            BufferUploader.draw(buf.buildOrThrow());
            shader.clear();

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, halfFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fid);
            GL30.glBlitFramebuffer(0, 0, hw, hh, 0, 0, fw, fh, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_LINEAR);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fid);
            GL30.glViewport(0, 0, fw, fh);

            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static void ensureFbo(int w, int h, int hw, int hh) {
        if (scFbo == 0 || scW != w || scH != h) {
            if (scFbo != 0) { GL30.glDeleteTextures(scColor); GL30.glDeleteFramebuffers(scFbo); }
            scW = w; scH = h;
            scFbo = GL30.glGenFramebuffers();
            scColor = GL30.glGenTextures();
            GL30.glBindTexture(GL30.GL_TEXTURE_2D, scColor);
            GL30.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA8, w, h, 0, GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
            GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, scFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_TEXTURE_2D, scColor, 0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }
        if (halfFbo == 0 || halfW != hw || halfH != hh) {
            if (halfFbo != 0) { GL30.glDeleteTextures(halfColor); GL30.glDeleteFramebuffers(halfFbo); }
            halfW = hw; halfH = hh;
            halfFbo = GL30.glGenFramebuffers();
            halfColor = GL30.glGenTextures();
            GL30.glBindTexture(GL30.GL_TEXTURE_2D, halfColor);
            GL30.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA8, hw, hh, 0, GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
            GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, halfFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_TEXTURE_2D, halfColor, 0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }
        if (noiseTex == null) {
            noiseTex = new DynamicTexture(N, N, true);
            NativeImage ni = new NativeImage(N, N, false);
            Random r = new Random(0x48424D4CL);
            for (int x = 0; x < N; x++)
                for (int y = 0; y < N; y++) {
                    int g = r.nextInt(256);
                    ni.setPixelRGBA(x, y, (g << 16) | (g << 8) | g | 0xFF000000);
                }
            noiseTex.setPixels(ni);
            noiseTex.setFilter(false, true);
            noiseTex.upload();
        }
    }
}
