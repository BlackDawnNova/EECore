package com.endlessepoch.core.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * Utility to build BakedQuad elements for composite machine models.
 * 方块面生成工具，用于合成机器模型。
 */
public final class QuadBuilder {

    private QuadBuilder() {}

    /**
     * Generate a face quad with full UV [ux,vy] → [ux+uw, vy+vh].
     * 生成单面四边形，UV 全幅。
     */
    public static BakedQuad face(Direction dir, float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  TextureAtlasSprite sprite, boolean shade) {
        return face(dir, x1, y1, z1, x2, y2, z2, sprite, 0f, 0f, 16f, 16f, shade);
    }

    /**
     * Generate a face quad with custom UV.
     * 生成单面四边形，自定义 UV。
     */
    public static BakedQuad face(Direction dir, float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  TextureAtlasSprite sprite,
                                  float ux, float vy, float uw, float vh,
                                  boolean shade) {
        float uMin = sprite.getU(ux);
        float uMax = sprite.getU(ux + uw);
        float vMin = sprite.getV(vy);
        float vMax = sprite.getV(vy + vh);
        int[] vertices = singleFace(dir, x1, y1, z1, x2, y2, z2, uMin, vMin, uMax, vMax, shade);
        return new BakedQuad(vertices, -1, dir, sprite, shade);
    }

    /**
     * Returns vertex data for one face. Adapted from vanilla face baking.
     * 返回单面的顶点数据，取材自原版面烘焙逻辑。
     */
    private static int[] singleFace(Direction dir, float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     float uMin, float vMin, float uMax, float vMax,
                                     boolean shade) {
        int[] data = new int[28]; // 4 vertices × 7 ints each
        float nx = dir.getStepX(), ny = dir.getStepY(), nz = dir.getStepZ();
        float[][] corners = faceCorners(dir, x1, y1, z1, x2, y2, z2);
        float[][] uvs = {
            {uMin, vMax}, {uMax, vMax}, {uMax, vMin}, {uMin, vMin}
        };
        for (int v = 0; v < 4; v++) {
            int off = v * 7;
            float cx = corners[v][0], cy = corners[v][1], cz = corners[v][2];
            data[off]     = Float.floatToRawIntBits(cx);
            data[off + 1] = Float.floatToRawIntBits(cy);
            data[off + 2] = Float.floatToRawIntBits(cz);
            // Color: white
            data[off + 3] = -1;
            // UV
            data[off + 4] = Float.floatToRawIntBits(uvs[v][0]);
            data[off + 5] = Float.floatToRawIntBits(uvs[v][1]);
            // Light: normal + padding
            int normalPacked = packNormal(nx, ny, nz);
            data[off + 6] = shade ? (normalPacked | 0x00F000F0) : (normalPacked | 0x00F000FF);
        }
        return data;
    }

    private static float[][] faceCorners(Direction dir, float x1, float y1, float z1,
                                          float x2, float y2, float z2) {
        return switch (dir) {
            case DOWN  -> new float[][]{{x1, y1, z1},{x2, y1, z1},{x2, y1, z2},{x1, y1, z2}};
            case UP    -> new float[][]{{x1, y2, z1},{x1, y2, z2},{x2, y2, z2},{x2, y2, z1}};
            case NORTH -> new float[][]{{x1, y2, z1},{x2, y2, z1},{x2, y1, z1},{x1, y1, z1}};
            case SOUTH -> new float[][]{{x2, y2, z2},{x1, y2, z2},{x1, y1, z2},{x2, y1, z2}};
            case WEST  -> new float[][]{{x1, y2, z2},{x1, y2, z1},{x1, y1, z1},{x1, y1, z2}};
            case EAST  -> new float[][]{{x2, y2, z1},{x2, y2, z2},{x2, y1, z2},{x2, y1, z1}};
        };
    }

    private static int packNormal(float x, float y, float z) {
        int bx = ((int)(x * 127) & 0xFF);
        int by = ((int)(y * 127) & 0xFF);
        int bz = ((int)(z * 127) & 0xFF);
        return (bx << 16) | (by << 8) | bz;
    }
}
