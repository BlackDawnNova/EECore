package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.network.SyncValidationPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
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
 * Ghost preview with smart LOD. Near (<16 blocks): full block models + translucent overlay + no-depth wireframe.
 * Far (16-48 blocks): full cubes merged into chunk wireframes, partial blocks individual wireframes.
 * <p>
 * 幽灵预览+智能LOD。近处原方块模型 + 半透明覆盖 + 可透墙绿色线框，远处合并线框。
 */
public class WorldPreviewManager {

    private static final WorldPreviewManager INSTANCE = new WorldPreviewManager();
    public static WorldPreviewManager get() { return INSTANCE; }

    private static final double NEAR_RANGE_SQ = 16.0 * 16.0;
    private static final int LOD_CHUNK = 4;
    private static final double MIN_RANGE = 32.0, MAX_RANGE = 128.0;

    /** Thick green wireframe visible through walls / 可透墙的粗绿线框 */
    private static final RenderType GHOST_WIREFRAME = RenderType.create(
            "eecore_ghost_wireframe",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.LINES,
            16384,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderType.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderType.LineStateShard(java.util.OptionalDouble.of(4.0)))
                    .setDepthTestState(RenderType.NO_DEPTH_TEST)
                    .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderType.NO_CULL)
                    .createCompositeState(false)
    );

    private record GhostEntry(BlockPos worldPos, BlockPos localPos) {}

    private ResourceLocation patternId;
    private double renderRangeSq = MIN_RANGE * MIN_RANGE;
    private final List<GhostEntry> missEntries = new ArrayList<>();
    private final List<GhostEntry> wrongEntries = new ArrayList<>();
    private boolean active;
    private boolean postFormation;

    public void updateValidation(SyncValidationPacket pkt) {
        missEntries.clear(); wrongEntries.clear();
        patternId = pkt.patternId();
        postFormation = pkt.postFormation();
        var missW = pkt.missingWorldPositions();
        var missL = pkt.missingLocalPositions();
        var wrongW = pkt.wrongWorldPositions();
        var wrongL = pkt.wrongLocalPositions();
        for (int i = 0; i < missW.size(); i++)
            missEntries.add(new GhostEntry(missW.get(i), i < missL.size() ? missL.get(i) : null));
        for (int i = 0; i < wrongW.size(); i++)
            wrongEntries.add(new GhostEntry(wrongW.get(i), i < wrongL.size() ? wrongL.get(i) : null));
        // Larger structure → shorter range for FPS / 结构越大范围越小保帧数
        int total = pkt.width() * pkt.height() * pkt.depth();
        double range = total > 200_000 ? 16 : total > 50_000 ? 32 : total > 5_000 ? 48 : 64;
        range = Math.max(MIN_RANGE, Math.min(MAX_RANGE, range));
        renderRangeSq = range * range;
        active = !missEntries.isEmpty() || !wrongEntries.isEmpty();
    }

    public void clearPreview() { patternId = null; missEntries.clear(); wrongEntries.clear(); active = false; }


    @SubscribeEvent
    public void onRenderWorldLast(RenderLevelStageEvent event) {
        if (!active) return;
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

        if (!missEntries.isEmpty() && pattern != null) {
            int nearDrawn = 0;
            var nearGhosts = new ArrayList<GhostEntry>();
            // Far: split full-cube vs partial / 远处分流
            var fullChunks = new HashMap<Long, AABB>();
            var partials = new HashMap<GhostEntry, BlockState>();

            for (var entry : missEntries) {
                double distSq = entry.worldPos.distToCenterSqr(cam);
                if (distSq > renderRangeSq) continue;
                BlockState state = entry.localPos != null
                        ? pattern.getExpectedState(entry.localPos.getX(), entry.localPos.getY(), entry.localPos.getZ())
                        : null;
                if (state == null || state.isAir()) continue;

                if (distSq <= NEAR_RANGE_SQ) {
                    nearGhosts.add(entry);
                    pose.pushPose();
                    pose.translate(entry.worldPos.getX(), entry.worldPos.getY(), entry.worldPos.getZ());
                    blockRenderer.renderSingleBlock(state, pose, buf,
                            0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                    nearDrawn++;
                } else if (state.isCollisionShapeFullBlock(null, null)) {
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
                    partials.put(entry, state);
                }
            }

            if (nearDrawn > 0) {
                buf.endBatch(RenderType.solid());
                buf.endBatch(RenderType.cutoutMipped());
                buf.endBatch(RenderType.cutout());
                buf.endBatch(RenderType.translucent());
            }

            if (postFormation && !nearGhosts.isEmpty()) {
                var ghostVc = buf.getBuffer(GHOST_WIREFRAME);
                for (var entry : nearGhosts) {
                    BlockPos p = entry.worldPos;
                    AABB box = new AABB(p.getX()-0.005, p.getY()-0.005, p.getZ()-0.005,
                            p.getX()+1.005, p.getY()+1.005, p.getZ()+1.005);
                    LevelRenderer.renderLineBox(pose, ghostVc, box, 0.15f, 0.95f, 0.45f, 1.0f);
                }
                buf.endBatch(GHOST_WIREFRAME);

                var tess = Tesselator.getInstance();
                var builder = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
                for (var entry : nearGhosts) {
                    BlockPos p = entry.worldPos;
                    addGhostOverlay(pose.last(), builder,
                            p.getX()+0.02f, p.getY()+0.02f, p.getZ()+0.02f,
                            p.getX()+0.98f, p.getY()+0.98f, p.getZ()+0.98f,
                            0.1f, 0.9f, 0.5f, 0.25f);
                }
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableDepthTest();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                BufferUploader.drawWithShader(builder.buildOrThrow());
                RenderSystem.enableDepthTest();
                RenderSystem.disableBlend();
            }

            // Far: all wireframes in one color / 远处统一蓝色线框
            if (!fullChunks.isEmpty() || !partials.isEmpty()) {
                var vc = buf.getBuffer(RenderType.lines());
                for (var box : fullChunks.values())
                    LevelRenderer.renderLineBox(pose, vc, box.inflate(0.01), 0.2f, 0.6f, 0.8f, 0.3f);
                for (var e : partials.entrySet()) {
                    var ge = e.getKey();
                    var state = e.getValue();
                    var shape = state.getShape(null, null);
                    for (AABB box : shape.toAabbs()) {
                        LevelRenderer.renderLineBox(pose, vc,
                                box.move(ge.worldPos.getX(), ge.worldPos.getY(), ge.worldPos.getZ()).inflate(0.002),
                                0.2f, 0.6f, 0.8f, 0.3f);
                    }
                }
                buf.endBatch(RenderType.lines());
            }
        }

        // Wrong blocks — red wireframe / 错误方块红色线框
        if (!wrongEntries.isEmpty()) {
            var vc = buf.getBuffer(RenderType.lines());
            for (var entry : wrongEntries) {
                if (entry.worldPos.distToCenterSqr(cam) > renderRangeSq) continue;
                BlockPos p = entry.worldPos;
                AABB box = new AABB(p.getX()+0.002, p.getY()+0.002, p.getZ()+0.002,
                        p.getX()+0.998, p.getY()+0.998, p.getZ()+0.998);
                LevelRenderer.renderLineBox(pose, vc, box, 1f, 0.2f, 0.2f, 0.9f);
            }
            buf.endBatch(RenderType.lines());
        }

        pose.popPose();
    }

    /** Render a translucent colored cube for ghost overlay. / 渲染半透明彩色立方体覆盖层。 */
    private static void addGhostOverlay(PoseStack.Pose pose, VertexConsumer vc,
                                        float x1, float y1, float z1, float x2, float y2, float z2,
                                        float r, float g, float b, float a) {
        addTri(pose, vc, x1, y1, z1, x2, y1, z1, x1, y1, z2, r, g, b, a);
        addTri(pose, vc, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        addTri(pose, vc, x1, y2, z1, x1, y2, z2, x2, y2, z1, r, g, b, a);
        addTri(pose, vc, x2, y2, z1, x1, y2, z2, x2, y2, z2, r, g, b, a);
        addTri(pose, vc, x1, y1, z1, x1, y2, z1, x2, y1, z1, r, g, b, a);
        addTri(pose, vc, x2, y1, z1, x1, y2, z1, x2, y2, z1, r, g, b, a);
        addTri(pose, vc, x1, y1, z2, x2, y1, z2, x1, y2, z2, r, g, b, a);
        addTri(pose, vc, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        addTri(pose, vc, x1, y1, z1, x1, y1, z2, x1, y2, z1, r, g, b, a);
        addTri(pose, vc, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        addTri(pose, vc, x2, y1, z1, x2, y2, z1, x2, y1, z2, r, g, b, a);
        addTri(pose, vc, x2, y1, z2, x2, y2, z1, x2, y2, z2, r, g, b, a);
    }

    private static void addTri(PoseStack.Pose pose, VertexConsumer vc,
                               float x1, float y1, float z1, float x2, float y2, float z2,
                               float x3, float y3, float z3, float r, float g, float b, float a) {
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
    }
}
