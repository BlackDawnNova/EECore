package com.endlessepoch.core.nova.client;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Encodes BlockPos (x, y, z) into an ARGB pick color and decodes back.
 * <p>
 * Range per axis: [-128, 127] — sufficient for most multiblock structures.
 * Background (no block) = ARGB(0,0,0,0) → decode returns null.
 */
@OnlyIn(Dist.CLIENT)
public final class PickEncoder {

    private static final int OFFSET = 128;

    private PickEncoder() {}

    /**
     * Encode a block position into a unique ARGB color.
     * Alpha = 255 means block present; 0 means air.
     */
    public static int encode(int x, int y, int z) {
        int r = clamp(x + OFFSET, 0, 255);
        int g = clamp(y + OFFSET, 0, 255);
        int b = clamp(z + OFFSET, 0, 255);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    /**
     * Decode an ARGB pixel back to BlockPos.
     * Returns null if alpha == 0 (background / air).
     */
    public static BlockPos decode(int argb) {
        int a = (argb >> 24) & 0xFF;
        if (a == 0) return null;
        int r = argb & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = (argb >> 16) & 0xFF;
        return new BlockPos(r - OFFSET, g - OFFSET, b - OFFSET);
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}
