package com.endlessepoch.core.api.energy.eb;

import net.minecraft.core.BlockPos;

/** BlockPos → long hash and segment assignment. / 方块坐标→long哈希+分片分配。 */
public final class HashUtil {
    private HashUtil() {}

    public static long hash(BlockPos pos) {
        return ((long) pos.getX() << 42) ^ ((long) pos.getY() << 21) ^ pos.getZ();
    }

    public static int segment(long hash, int segmentCount) {
        return Math.abs((int) (hash % segmentCount));
    }
}
