package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.EECore;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VBO-style ECS structure renderer — bakes block model quads into a GPU-side VertexBuffer once,
 * then draws cheaply each frame via glDrawElements. No per-frame vertex rebuild.
 * VBO 风格 ECS 结构渲染器——将方块模型四边形烘焙到 GPU VertexBuffer，之后每帧轻量绘制。
 */
@EventBusSubscriber(modid = EECore.MOD_ID, value = Dist.CLIENT)
public final class EcsVboRenderer {

    private EcsVboRenderer() {}

    /** GPU-side baked data for one pattern. / 单个结构的 GPU 烘焙数据。 */
    private static class GpuBake {
        final VertexBuffer vbo;
        final double cx, cy, cz; // controller position in ECS / 控制器在ECS中的坐标

        GpuBake(VertexBuffer vbo, double cx, double cy, double cz) {
            this.vbo = vbo; this.cx = cx; this.cy = cy; this.cz = cz;
        }
    }

    private static final Map<ResourceLocation, GpuBake> bakeCache = new ConcurrentHashMap<>();

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;

        var bindings = EcsPreviewTracker.getBindings();
        if (bindings.isEmpty()) return;

        var cam = e.getCamera();
        var random = net.minecraft.util.RandomSource.create();
        var playerUUID = mc.player != null ? mc.player.getUUID() : null;
        var stalePositions = new java.util.ArrayList<BlockPos>();

        for (var entry : bindings.entrySet()) {
            BlockPos worldPos = entry.getKey();
            if (!(mc.level.getBlockState(worldPos).getBlock() instanceof com.endlessepoch.core.nova.block.ScannerControllerBlock)) {
                stalePositions.add(worldPos);
                continue;
            }
            ResourceLocation patternId = entry.getValue();

            GpuBake gpu = bakeCache.computeIfAbsent(patternId,
                    id -> bakeGpu(mc, id, playerUUID, random));
            if (gpu == null) continue;

            var facing = mc.level.getBlockState(worldPos).getValue(
                    com.endlessepoch.core.nova.block.ScannerControllerBlock.FACING);
            float angle = switch (facing) {
                case EAST  -> (float)(-Math.PI / 2);
                case SOUTH -> (float)Math.PI;
                case WEST  -> (float)(Math.PI / 2);
                default    -> 0f;
            };

            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            modelView.translate((float)(worldPos.getX() + 0.5 - cam.getPosition().x),
                                (float)(worldPos.getY() + 0.5 - cam.getPosition().y),
                                (float)(worldPos.getZ() + 0.5 - cam.getPosition().z));
            modelView.rotateY(angle);
            modelView.translate((float)(-gpu.cx - 0.5), (float)(-gpu.cy - 0.5), (float)(-gpu.cz - 0.5));
            Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());

            gpu.vbo.bind();
            gpu.vbo.drawWithShader(modelView, projection, GameRenderer.getPositionTexColorShader());
            VertexBuffer.unbind();
        }
        for (var p : stalePositions) EcsPreviewTracker.removeBinding(p);
    }

    /**
     * Build a GPU VertexBuffer from block quads, uploaded once, drawn each frame via drawWithShader.
     * 从方块四边形构建 GPU VertexBuffer，上传一次，每帧 drawWithShader 绘制。
     */
    private static GpuBake bakeGpu(Minecraft mc, ResourceLocation id,
                                    UUID playerUUID, net.minecraft.util.RandomSource random) {
        com.endlessepoch.core.api.multiblock.MultiBlockPattern pat;
        try {
            var patOpt = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(playerUUID, id);
            if (patOpt.isEmpty()) {
                EECore.LOGGER.warn("[ECS-VBO] pattern not found: {}", id);
                return null;
            }
            pat = patOpt.get();
        } catch (Exception ex) {
            EECore.LOGGER.error("[ECS-VBO] Failed to load pattern for {}", id, ex);
            return null;
        }

        int stride = DefaultVertexFormat.BLOCK.getVertexSize() / 4;
        double cx = pat.controllerX, cy = pat.controllerY, cz = pat.controllerZ;

        // Set render state BEFORE building so DrawState captures the correct shader
        // 在构建前设置渲染状态，让 DrawState 捕获正确的 shader
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.disableCull();
        RenderSystem.depthMask(true);

        var builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        for (BlockPos pos : pat.getNonAirPositions()) {
            BlockState state = pat.getExpectedState(pos.getX(), pos.getY(), pos.getZ());
            if (state == null || state.isAir()) continue;
            var model = mc.getBlockRenderer().getBlockModel(state);
            float px = pos.getX(), py = pos.getY(), pz = pos.getZ();

            var qs = new java.util.ArrayList<net.minecraft.client.renderer.block.model.BakedQuad>();
            qs.addAll(model.getQuads(state, null, random));
            for (var d : net.minecraft.core.Direction.values())
                qs.addAll(model.getQuads(state, d, random));

            for (var q : qs) {
                int[] qv = q.getVertices();
                for (int vi = 0; vi < 4; vi++) {
                    int off = vi * stride;
                    builder.addVertex(
                            px + Float.intBitsToFloat(qv[off]) * 0.998f + 0.001f,
                            py + Float.intBitsToFloat(qv[off + 1]) * 0.998f + 0.001f,
                            pz + Float.intBitsToFloat(qv[off + 2]) * 0.998f + 0.001f)
                          .setUv(Float.intBitsToFloat(qv[off + 4]),
                                 Float.intBitsToFloat(qv[off + 5]))
                          .setColor((qv[off + 3] & 0xFF) / 255f,
                                    ((qv[off + 3] >> 8) & 0xFF) / 255f,
                                    ((qv[off + 3] >> 16) & 0xFF) / 255f,
                                    1f);
                }
            }
        }

        var mesh = builder.buildOrThrow();
        var vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(mesh);
        VertexBuffer.unbind();

        EECore.LOGGER.info("[ECS-VBO] GPU bake {} — {} blocks",
                pat.width + "×" + pat.height + "×" + pat.depth,
                pat.getNonAirPositions().size());
        return new GpuBake(vbo, cx, cy, cz);
    }

}
