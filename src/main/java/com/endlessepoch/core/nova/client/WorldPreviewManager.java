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
 * Smart LOD: full cubes → merged chunk wireframes, partial blocks → individual wireframes.
 * <p>
 * 智能LOD——完整方块合并成区域线框，非完整方块（半砖/栅栏/末地烛等）单独线框保留辨识度。
 */
public class WorldPreviewManager {

    private static final WorldPreviewManager INSTANCE = new WorldPreviewManager();
    public static WorldPreviewManager get() { return INSTANCE; }

    private static final double RENDER_RANGE_SQ = 48.0 * 48.0;
    private static final int LOD_CHUNK = 4; // Merge full blocks into 4³ chunks / 完整方块4格合并

    private record GhostEntry(BlockPos worldPos, BlockPos localPos) {}

    private ResourceLocation patternId;
    private final List<GhostEntry> missEntries = new ArrayList<>();
    private final List<GhostEntry> wrongEntries = new ArrayList<>();
    private boolean active;

    public void updateValidation(SyncValidationPacket pkt) {
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
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        var player = Minecraft.getInstance().player;
        var pattern = patternId != null && player != null
                ? MultiBlockRegistry.get(player.getUUID(), patternId).orElse(null) : null;
        var buf = Minecraft.getInstance().renderBuffers().bufferSource();
        var pose = event.getPoseStack();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        if (!missEntries.isEmpty() && pattern != null) {
            // Split: full-cube (mergeable) vs partial (individual wireframe) / 完整方块vs部分方块
            var fullChunks = new HashMap<Long, AABB>();
            var partials = new ArrayList<GhostEntry>();

            for (var entry : missEntries) {
                if (entry.worldPos.distToCenterSqr(cam) > RENDER_RANGE_SQ) continue;
                BlockState state = entry.localPos != null
                        ? pattern.getExpectedState(entry.localPos.getX(), entry.localPos.getY(), entry.localPos.getZ())
                        : null;
                if (state == null || state.isAir()) continue;
                if (state.isCollisionShapeFullBlock(null, null)) {
                    int cx = entry.worldPos.getX() / LOD_CHUNK;
                    int cy = entry.worldPos.getY() / LOD_CHUNK;
                    int cz = entry.worldPos.getZ() / LOD_CHUNK;
                    long key = ((long)cx << 42) | ((long)(cy & 0x1FFFFF) << 21) | (cz & 0x1FFFFF);
                    fullChunks.merge(key,
                            new AABB(entry.worldPos.getX(), entry.worldPos.getY(), entry.worldPos.getZ(),
                                     entry.worldPos.getX()+1, entry.worldPos.getY()+1, entry.worldPos.getZ()+1),
                            (a, b) -> new AABB(
                                    Math.min(a.minX, b.minX), Math.min(a.minY, b.minY), Math.min(a.minZ, b.minZ),
                                    Math.max(a.maxX, b.maxX), Math.max(a.maxY, b.maxY), Math.max(a.maxZ, b.maxZ)));
                } else {
                    partials.add(entry);
                }
            }

            // Full cubes → merged chunk wireframes (blue) / 完整方块合并线框
            if (!fullChunks.isEmpty()) {
                var vc = buf.getBuffer(RenderType.lines());
                for (var box : fullChunks.values())
                    LevelRenderer.renderLineBox(pose, vc, box.inflate(0.01), 0.2f, 0.6f, 0.8f, 0.4f);
                buf.endBatch(RenderType.lines());
            }

            // Partial blocks → individual wireframes (yellow) / 部分方块单独线框
            if (!partials.isEmpty()) {
                var vc = buf.getBuffer(RenderType.lines());
                for (var entry : partials) {
                    AABB box = new AABB(entry.worldPos.getX()+0.02, entry.worldPos.getY()+0.02, entry.worldPos.getZ()+0.02,
                            entry.worldPos.getX()+0.98, entry.worldPos.getY()+0.98, entry.worldPos.getZ()+0.98);
                    LevelRenderer.renderLineBox(pose, vc, box, 0.8f, 0.8f, 0.2f, 0.5f);
                }
                buf.endBatch(RenderType.lines());
            }
        }

        // Wrong blocks — red wireframe / 错误方块红色线框
        if (!wrongEntries.isEmpty()) {
            var vc = buf.getBuffer(RenderType.lines());
            for (var entry : wrongEntries) {
                if (entry.worldPos.distToCenterSqr(cam) > RENDER_RANGE_SQ) continue;
                BlockPos p = entry.worldPos;
                AABB box = new AABB(p.getX()+0.002, p.getY()+0.002, p.getZ()+0.002,
                        p.getX()+0.998, p.getY()+0.998, p.getZ()+0.998);
                LevelRenderer.renderLineBox(pose, vc, box, 1f, 0.2f, 0.2f, 0.9f);
            }
            buf.endBatch(RenderType.lines());
        }

        pose.popPose();
    }
}
