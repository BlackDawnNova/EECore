package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.network.SyncValidationPacket;
import com.mojang.blaze3d.systems.RenderSystem;
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

public class WorldPreviewManager {

    private static final WorldPreviewManager INSTANCE = new WorldPreviewManager();
    public static WorldPreviewManager get() { return INSTANCE; }

    private static final double RENDER_RANGE_SQ = 64.0 * 64.0;
    private static final double LOD_NEAR_SQ = 16.0 * 16.0;
    private static final int LOD_CHUNK = 4; // Merge far blocks into 4x4x4 chunks / 远处以4格立方体合并

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

    /** 2 triangles = 1 AABB face / 2三角=1个AABB面 */
    private static void lodQuad(VertexConsumer vc, PoseStack.Pose pose,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float r, float g, float b, float a) {
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(pose, x4, y4, z4).setColor(r, g, b, a);
    }

    /** Simplified AABB cube — 12 triangles, no texture / 简化AABB—12三角面无纹理 */
    private static void lodAABB(VertexConsumer vc, PoseStack pose,
            float bx, float by, float bz, float r, float g, float b, float a) {
        var m = pose.last();
        float s = 0.02f, e = 0.98f;
        lodQuad(vc, m, bx+s,by+s,bz+s, bx+e,by+s,bz+e, bx+e,by+e,bz+e, bx+s,by+e,bz+e, r,g,b,a); // -Z
        lodQuad(vc, m, bx+s,by+s,bz+e, bx+e,by+e,bz+e, bx+e,by+s,bz+e, bx+s,by+e,bz+e, r,g,b,a); // +Z
        lodQuad(vc, m, bx+s,by+s,bz+s, bx+s,by+e,bz+e, bx+s,by+e,bz+s, bx+s,by+s,bz+e, r,g,b,a); // -X
        lodQuad(vc, m, bx+e,by+s,bz+s, bx+e,by+s,bz+e, bx+e,by+e,bz+e, bx+e,by+e,bz+s, r,g,b,a); // +X
        lodQuad(vc, m, bx+s,by+s,bz+s, bx+e,by+s,bz+s, bx+e,by+s,bz+e, bx+s,by+s,bz+e, r,g,b,a); // -Y
        lodQuad(vc, m, bx+s,by+e,bz+s, bx+s,by+e,bz+e, bx+e,by+e,bz+e, bx+e,by+e,bz+s, r,g,b,a); // +Y
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderLevelStageEvent event) {
        if (!active) return;
        // if (System.currentTimeMillis() - previewStartMs > 5_000) { clearPreview(); return; }
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

        int nearDrawn = 0, farDrawn = 0;
        if (!missEntries.isEmpty() && pattern != null) {
            // Near: full model via renderSingleBlock / 近处完整方块模型
            for (var entry : missEntries) {
                double distSq = entry.worldPos.distToCenterSqr(cam);
                if (distSq > RENDER_RANGE_SQ) continue;
                BlockState state = entry.localPos != null
                        ? pattern.getExpectedState(entry.localPos.getX(), entry.localPos.getY(), entry.localPos.getZ())
                        : null;
                if (state == null || state.isAir()) continue;
                if (distSq <= LOD_NEAR_SQ) {
                    pose.pushPose();
                    pose.translate(entry.worldPos.getX(), entry.worldPos.getY(), entry.worldPos.getZ());
                    blockRenderer.renderSingleBlock(state, pose, buf,
                            0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                    nearDrawn++;
                } else {
                    farDrawn++;
                }
            }
            // Far: merged chunk-AABBs / 远处按块合并
            if (farDrawn > 0) {
                var chunks = new java.util.HashMap<Long, AABB>();
                for (var entry : missEntries) {
                    double distSq = entry.worldPos.distToCenterSqr(cam);
                    if (distSq <= LOD_NEAR_SQ || distSq > RENDER_RANGE_SQ) continue;
                    BlockState state = entry.localPos != null
                            ? pattern.getExpectedState(entry.localPos.getX(), entry.localPos.getY(), entry.localPos.getZ())
                            : null;
                    if (state == null || state.isAir()) continue;
                    int cx = entry.worldPos.getX() / LOD_CHUNK;
                    int cy = entry.worldPos.getY() / LOD_CHUNK;
                    int cz = entry.worldPos.getZ() / LOD_CHUNK;
                    long key = ((long)cx << 42) | ((long)(cy & 0x1FFFFF) << 21) | (cz & 0x1FFFFF);
                    chunks.merge(key,
                            new AABB(entry.worldPos.getX(), entry.worldPos.getY(), entry.worldPos.getZ(),
                                     entry.worldPos.getX() + 1, entry.worldPos.getY() + 1, entry.worldPos.getZ() + 1),
                            (a, b) -> new AABB(
                                    Math.min(a.minX, b.minX), Math.min(a.minY, b.minY), Math.min(a.minZ, b.minZ),
                                    Math.max(a.maxX, b.maxX), Math.max(a.maxY, b.maxY), Math.max(a.maxZ, b.maxZ)));
                }
                var lodVc = buf.getBuffer(RenderType.lines());
                for (var box : chunks.values()) {
                    LevelRenderer.renderLineBox(pose, lodVc, box.inflate(0.02), 0.2f, 0.6f, 0.8f, 0.5f);
                }
                buf.endBatch(RenderType.lines());
            }
        }

        if (nearDrawn > 0) {
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
