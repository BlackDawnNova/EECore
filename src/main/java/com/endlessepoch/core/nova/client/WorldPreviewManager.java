package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.network.SyncValidationPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

import java.util.*;

public class WorldPreviewManager {

    private static final WorldPreviewManager INSTANCE = new WorldPreviewManager();
    public static WorldPreviewManager get() { return INSTANCE; }

    private ResourceLocation patternId;
    private final List<BlockPos> missWorld = new ArrayList<>();
    private final List<BlockPos> missLocal = new ArrayList<>();
    private final List<BlockPos> wrongWorld = new ArrayList<>();
    private boolean active, meshDirty;
    private VertexBuffer ghostVBO;
    private RenderType ghostVboLayer;

    public void updateValidation(SyncValidationPacket pkt) {
        closeVBO();
        missWorld.clear(); missLocal.clear(); wrongWorld.clear();
        patternId = pkt.patternId();
        missWorld.addAll(pkt.missingWorldPositions());
        wrongWorld.addAll(pkt.wrongWorldPositions());
        missLocal.addAll(pkt.missingLocalPositions());
        active = !missWorld.isEmpty() || !wrongWorld.isEmpty();
        if (!missWorld.isEmpty()) meshDirty = true;
    }

    private void closeVBO() { if (ghostVBO != null) { ghostVBO.close(); ghostVBO = null; } }

    private void buildVBO() {
        closeVBO(); if (missWorld.isEmpty()) return;
        var player = Minecraft.getInstance().player;
        var pattern = patternId != null && player != null
                ? MultiBlockRegistry.get(player.getUUID(), patternId).orElse(null) : null;
        if (pattern == null) return;
        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        ghostVboLayer = RenderType.solid();
        BufferBuilder buf = Tesselator.getInstance().begin(ghostVboLayer.mode(), ghostVboLayer.format());
        PoseStack ps = new PoseStack();

        for (int i = 0; i < missWorld.size(); i++) {
            BlockPos wp = missWorld.get(i);
            BlockPos local = i < missLocal.size() ? missLocal.get(i) : null;
            BlockState state = local != null ? pattern.getExpectedState(local.getX(), local.getY(), local.getZ()) : null;
            if (state == null || state.isAir()) continue;
            ps.pushPose();
            ps.translate(wp.getX(), wp.getY(), wp.getZ());
            blockRenderer.renderSingleBlock(state, ps, new SingleRTBufferSource(ghostVboLayer, buf), 0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        MeshData md = buf.build();
        int vc = md.drawState().vertexCount();
        System.out.println("[EECore] VBO built: vertices=" + vc);
        if (vc > 0) {
            ghostVBO = new VertexBuffer(VertexBuffer.Usage.STATIC);
            ghostVBO.bind(); ghostVBO.upload(md); VertexBuffer.unbind();
        }
        md.close(); meshDirty = false;
    }

    private record SingleRTBufferSource(RenderType rt, BufferBuilder buf) implements net.minecraft.client.renderer.MultiBufferSource {
        public VertexConsumer getBuffer(RenderType type) { return buf; }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderLevelStageEvent event) {
        if (!active) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (meshDirty) buildVBO();
        if (ghostVBO == null) return;

        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cp = cam.getPosition();
        Matrix4f mv = new Matrix4f().translate((float)-cp.x, (float)-cp.y, (float)-cp.z);
        Matrix4f proj = new Matrix4f(event.getProjectionMatrix());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ghostVboLayer.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) {
            // Litematica approach: set ALL uniforms manually, then draw() / 手动设全uniforms
            for (int i = 0; i < 12; i++)
                shader.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
            if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(mv);
            if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(proj);
            if (shader.COLOR_MODULATOR != null) shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
            if (shader.FOG_START != null) shader.FOG_START.set(RenderSystem.getShaderFogStart());
            if (shader.FOG_END != null) shader.FOG_END.set(RenderSystem.getShaderFogEnd());
            if (shader.FOG_COLOR != null) shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
            if (shader.TEXTURE_MATRIX != null) shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
            if (shader.GAME_TIME != null) shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
            RenderSystem.setupShaderLights(shader);
            shader.apply();
            ghostVBO.bind();
            ghostVBO.draw(); // direct GL draw, shader already set up / 直接GL绘制
            VertexBuffer.unbind();
            shader.clear();
        }
        ghostVboLayer.clearRenderState();
        RenderSystem.disableBlend();

        if (!wrongWorld.isEmpty()) {
            PoseStack ps = event.getPoseStack();
            ps.pushPose(); ps.translate(-cp.x, -cp.y, -cp.z);
            BufferBuilder buf = Tesselator.getInstance().begin(
                    VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            for (BlockPos p : wrongWorld) {
                float x = p.getX(), y = p.getY(), z = p.getZ();
                LevelRenderer.renderLineBox(ps, buf, x, y, z, x+1, y+1, z+1, 1f, 0.1f, 0.1f, 0.9f);
            }
            RenderSystem.enableDepthTest();
            RenderType.lines().setupRenderState();
            MeshData md = buf.build();
            if (md != null) { BufferUploader.drawWithShader(md); md.close(); }
            RenderType.lines().clearRenderState();
            RenderSystem.disableDepthTest();
            ps.popPose();
        }
    }

    private static void putQuad(BufferBuilder buf, BakedQuad q, float dx, float dy, float dz) {
        int[] v = q.getVertices();
        for (int i = 0; i < 4; i++) {
            int off = i * 8;
            buf.addVertex(Float.intBitsToFloat(v[off])+dx, Float.intBitsToFloat(v[off+1])+dy, Float.intBitsToFloat(v[off+2])+dz)
               .setColor((v[off+3]>>16)&0xFF, (v[off+3]>>8)&0xFF, v[off+3]&0xFF, 140)
               .setUv(Float.intBitsToFloat(v[off+4]), Float.intBitsToFloat(v[off+5]))
               .setUv2(255, 255)
               .setNormal((v[off+7]>>24)&0xFF, (v[off+7]>>16)&0xFF, (v[off+7]>>8)&0xFF);
        }
    }
}
