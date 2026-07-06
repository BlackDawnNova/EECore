package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.network.SyncValidationPacket;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.*;

/**
 * In-world ghost preview after multiblock validation failure.
 * Renders missing blocks as translucent models, wrong blocks as red wireframes.
 * <p>
 * 多方块验证失败后世界中幽灵预览——缺失方块半透明模型，错误方块红色线框。
 */
public class WorldPreviewManager {

    private static final WorldPreviewManager INSTANCE = new WorldPreviewManager();
    public static WorldPreviewManager get() { return INSTANCE; }

    private static final double RENDER_RANGE_SQ = 32.0 * 32.0; // Max render distance² / 最大渲染距离²

    private record GhostEntry(BlockPos worldPos, BlockPos localPos) {}

    private ResourceLocation patternId;
    private final List<GhostEntry> missEntries = new ArrayList<>();
    private final List<GhostEntry> wrongEntries = new ArrayList<>();
    private boolean active;
    private long previewStartMs;

    public void updateValidation(SyncValidationPacket pkt) {
        previewStartMs = System.currentTimeMillis();
        missEntries.clear(); wrongEntries.clear();
        patternId = pkt.patternId();
        var missW = pkt.missingWorldPositions();
        var missL = pkt.missingLocalPositions();
        var wrongW = pkt.wrongWorldPositions();
        var wrongL = pkt.wrongLocalPositions();
        for (int i = 0; i < missW.size(); i++)
            missEntries.add(new GhostEntry(missW.get(i), i < missL.size() ? missL.get(i) : null));
        for (int i = 0; i < wrongW.size(); i++)
            wrongEntries.add(new GhostEntry(wrongW.get(i), i < wrongL.size() ? wrongL.get(i) : null));
        active = !missEntries.isEmpty() || !wrongEntries.isEmpty();
        EECore.LOGGER.info("[EECore Preview] active={} missing={} wrong={}",
                active, missEntries.size(), wrongEntries.size());
    }

    public void clearPreview() { patternId = null; missEntries.clear(); wrongEntries.clear(); active = false; }

    @SubscribeEvent
    public void onRenderWorldLast(RenderLevelStageEvent event) {
        if (!active) return;
        // Auto-clear after 5s / 5秒后自动清除
        if (System.currentTimeMillis() - previewStartMs > 5_000) { clearPreview(); return; }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        var player = Minecraft.getInstance().player;
        var pattern = patternId != null && player != null
                ? MultiBlockRegistry.get(player.getUUID(), patternId).orElse(null) : null;
        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        var buf = Minecraft.getInstance().renderBuffers().bufferSource();
        var pose = event.getPoseStack();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        int ghostDrawn = 0;
        if (!missEntries.isEmpty() && pattern != null) {
            for (var entry : missEntries) {
                // Frustum culling by distance / 距离裁剪
                if (entry.worldPos.distToCenterSqr(cam) > RENDER_RANGE_SQ) continue;
                BlockState state = entry.localPos != null
                        ? pattern.getExpectedState(entry.localPos.getX(), entry.localPos.getY(), entry.localPos.getZ())
                        : null;
                if (state == null || state.isAir()) continue;
                pose.pushPose();
                pose.translate(entry.worldPos.getX(), entry.worldPos.getY(), entry.worldPos.getZ());
                blockRenderer.renderSingleBlock(state, pose, buf,
                        0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                pose.popPose();
                ghostDrawn++;
            }
        }

        if (ghostDrawn > 0) {
            buf.endBatch(RenderType.solid());
            buf.endBatch(RenderType.cutoutMipped());
            buf.endBatch(RenderType.cutout());
            buf.endBatch(RenderType.translucent());
        }

        if (!wrongEntries.isEmpty()) {
            var vc = buf.getBuffer(RenderType.lines());
            for (var entry : wrongEntries) {
                if (entry.worldPos.distToCenterSqr(cam) > RENDER_RANGE_SQ) continue;
                BlockPos p = entry.worldPos;
                AABB box = new AABB(p.getX() + 0.002, p.getY() + 0.002, p.getZ() + 0.002,
                        p.getX() + 0.998, p.getY() + 0.998, p.getZ() + 0.998);
                LevelRenderer.renderLineBox(pose, vc, box, 1f, 0.2f, 0.2f, 0.9f);
            }
            buf.endBatch(RenderType.lines());
        }

        pose.popPose();
    }
}
