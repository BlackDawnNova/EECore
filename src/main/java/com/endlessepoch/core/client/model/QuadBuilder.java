package com.endlessepoch.core.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * Utility to build BakedQuad elements for composite machine models.
 * Uses 1.21.1 vertex format: 8 ints/vertex (Position×3, Color, UV0×2, UV2, Normal).
 * 方块面生成工具，1.21.1 顶点格式：每顶点 8 个 int。
 */
public final class QuadBuilder {

    private QuadBuilder() {}

    /** Generate a face quad covering the full sprite bounds precisely. */
    public static BakedQuad face(Direction dir, float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  TextureAtlasSprite sprite, boolean shade) {
        int[] vertices = singleFace(dir, x1, y1, z1, x2, y2, z2,
                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), shade);
        return new BakedQuad(vertices, -1, dir, sprite, shade);
    }

    /** Generate a face quad with custom UV. */
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

    /** Vertex data for one face — 8 ints/vertex × 4 vertices = 32 ints (1.21.1 format). */
    private static int[] singleFace(Direction dir, float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     float uMin, float vMin, float uMax, float vMax,
                                     boolean shade) {
        int[] data = new int[32]; // 4 vertices × 8 ints
        float nx = dir.getStepX(), ny = dir.getStepY(), nz = dir.getStepZ();
        float[][] corners = faceCorners(dir, x1, y1, z1, x2, y2, z2);
        float[][] uvs = {
            {uMin, vMax}, {uMax, vMax}, {uMax, vMin}, {uMin, vMin}
        };
        // Full-bright lightmap: skyLight=15, blockLight=15
        int lightmap = shade ? 0x00F000F0 : 0x00F000F0;
        int normal = packNormal(nx, ny, nz);

        for (int v = 0; v < 4; v++) {
            int off = v * 8;
            data[off]     = Float.floatToRawIntBits(corners[v][0]); // pos X
            data[off + 1] = Float.floatToRawIntBits(corners[v][1]); // pos Y
            data[off + 2] = Float.floatToRawIntBits(corners[v][2]); // pos Z
            data[off + 3] = -1;                                      // color (white)
            data[off + 4] = Float.floatToRawIntBits(uvs[v][0]);      // UV0 U
            data[off + 5] = Float.floatToRawIntBits(uvs[v][1]);      // UV0 V
            data[off + 6] = lightmap;                                 // UV2 (lightmap)
            data[off + 7] = normal;                                   // Normal
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
