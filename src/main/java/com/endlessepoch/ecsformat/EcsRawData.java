package com.endlessepoch.ecsformat;

import java.util.List;

/** Raw decoded .ecs data. / 解码后的原始 .ecs 数据 */
public class EcsRawData {
    public final int width, height, depth;
    public final int controllerX, controllerY, controllerZ;
    public final List<EcsPaletteEntry> palette;
    public final short[] voxelData;

    public EcsRawData(int width, int height, int depth,
                      int controllerX, int controllerY, int controllerZ,
                      List<EcsPaletteEntry> palette, short[] voxelData) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.controllerX = controllerX;
        this.controllerY = controllerY;
        this.controllerZ = controllerZ;
        this.palette = List.copyOf(palette);
        this.voxelData = voxelData;
    }
}
