package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * VBO-based ghost preview — missing blocks as semi-transparent models,
 * wrong blocks as red wireframe.
 * VBO 幽灵预览：缺失方块半透明模型，错误方块红线框。
 */
@EventBusSubscriber(modid = EECore.MOD_ID, value = Dist.CLIENT)
public final class GhostVboRenderer {

    private GhostVboRenderer() {}

    private static VertexBuffer missVbo;
    private static List<BlockPos> wrongPositions = Collections.emptyList();
    private static boolean active;

    /** Handle a validation packet directly — extracts data and delegates to setGhostData. */
    public static void handlePacket(com.endlessepoch.core.network.SyncValidationPacket pkt) {
        if (pkt.patternId().getPath().equals("clear") && pkt.missingWorldPositions().isEmpty()
                && pkt.wrongWorldPositions().isEmpty()) {
            clear();
            return;
        }
        setGhostData(pkt.patternId(),
                pkt.missingWorldPositions(), pkt.missingLocalPositions(),
                pkt.wrongWorldPositions(), pkt.wrongLocalPositions(),
                pkt.postFormation());
    }

    /** Bake VBO for missing blocks, store wrong positions for red wireframe. */
    public static void setGhostData(ResourceLocation patternId, List<BlockPos> missWorld,
                                     List<BlockPos> missLocal, List<BlockPos> wrongWorld,
                                     List<BlockPos> wrongLocal, boolean postFormation) {
        release();
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var patOpt = MultiBlockRegistry.get(mc.player.getUUID(), patternId);
        if (patOpt.isEmpty()) return;
        MultiBlockPattern pat = patOpt.get();

        // Missing: pre-formation 75% normal / post-formation green semi-transparent
        if (!missWorld.isEmpty()) {
            var states = new ArrayList<BlockState>();
            for (BlockPos local : missLocal)
                states.add(pat.getExpectedState(local.getX(), local.getY(), local.getZ()));
            if (postFormation) {
                missVbo = VboBuilder.build(missWorld, states, 0.15f, 0.95f, 0.45f, 0.40f, 1.0f);
            } else {
                missVbo = VboBuilder.build(missWorld, states, 1f, 1f, 1f, 1f, 0.75f);
            }
        }

        // Wrong: just store positions for red wireframe / 错误: 红线框存位置
        wrongPositions = wrongWorld.isEmpty() ? Collections.emptyList() : List.copyOf(wrongWorld);

        active = (missVbo != null || !wrongPositions.isEmpty());
    }

    public static void clear() { release(); active = false; }

    private static void release() {
        if (missVbo != null) { missVbo.close(); missVbo = null; }
        wrongPositions = Collections.emptyList();
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!active) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;

        var cam = e.getCamera();
        var camPos = cam.getPosition();
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
        view.translate((float)(-camPos.x), (float)(-camPos.y), (float)(-camPos.z));

        // Draw missing VBO / 渲染缺失VBO
        if (missVbo != null) {
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            missVbo.bind();
            missVbo.drawWithShader(view, proj, GameRenderer.getPositionTexColorShader());
            VertexBuffer.unbind();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
        }

        // Draw wrong wireframe / 渲染错误红线框
        if (!wrongPositions.isEmpty()) {
            var ps = e.getPoseStack();
            ps.pushPose();
            ps.translate(-camPos.x, -camPos.y, -camPos.z);
            var buf = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
            for (BlockPos p : wrongPositions) {
                net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                        p.getX() + 0.002, p.getY() + 0.002, p.getZ() + 0.002,
                        p.getX() + 0.998, p.getY() + 0.998, p.getZ() + 0.998);
                net.minecraft.client.renderer.LevelRenderer.renderLineBox(ps, buf, box, 1f, 0.2f, 0.2f, 0.8f);
            }
            mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
            ps.popPose();
        }
    }
}
