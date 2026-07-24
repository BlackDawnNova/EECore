package com.endlessepoch.core.nova.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Shared VBO builder — bakes block quads into GPU VertexBuffer.
 * Supports per-vertex color override for ghost preview tinting.
 * 公共 VBO 构建器——将方块四边形烘焙到 GPU VertexBuffer，支持逐顶点着色覆盖。
 */
public final class VboBuilder {

    private VboBuilder() {}

    /** Bake blocks at given positions into a VertexBuffer. scale=1.0 for full size, 0.75 for 75%. */
    public static VertexBuffer build(List<BlockPos> positions, List<BlockState> states,
                                      float r, float g, float b, float a, float scale) {
        var mc = Minecraft.getInstance();
        var random = net.minecraft.util.RandomSource.create();
        int stride = DefaultVertexFormat.BLOCK.getVertexSize() / 4;

        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.disableCull();
        RenderSystem.depthMask(true);

        var builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            BlockState state = states.get(i);
            if (state == null || state.isAir()) continue;
            var model = mc.getBlockRenderer().getBlockModel(state);
            float px = pos.getX(), py = pos.getY(), pz = pos.getZ();

            var qs = new java.util.ArrayList<BakedQuad>();
            qs.addAll(model.getQuads(state, null, random));
            for (Direction d : Direction.values())
                qs.addAll(model.getQuads(state, d, random));

            for (var q : qs) {
                int[] qv = q.getVertices();
                for (int vi = 0; vi < 4; vi++) {
                    int off = vi * stride;
                    float vx = Float.intBitsToFloat(qv[off]) * 0.998f + 0.001f;
                    float vy = Float.intBitsToFloat(qv[off + 1]) * 0.998f + 0.001f;
                    float vz = Float.intBitsToFloat(qv[off + 2]) * 0.998f + 0.001f;
                    // Scale toward block center / 向方块中心缩放
                    float sx = px + 0.5f + (vx - 0.5f) * scale;
                    float sy = py + 0.5f + (vy - 0.5f) * scale;
                    float sz = pz + 0.5f + (vz - 0.5f) * scale;
                    builder.addVertex(sx, sy, sz)
                          .setUv(Float.intBitsToFloat(qv[off + 4]),
                                 Float.intBitsToFloat(qv[off + 5]))
                          .setColor(r, g, b, a);
                }
            }
        }

        var mesh = builder.buildOrThrow();
        var vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(mesh);
        VertexBuffer.unbind();
        return vbo;
    }
}
