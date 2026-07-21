package com.endlessepoch.ecsformat;

import java.util.List;

public class EcsRawData {
    public final int width, height, depth;
    public final int controllerX, controllerY, controllerZ;
    public final List<EcsPaletteEntry> palette;
    public final short[] voxelData;
    public final boolean frameBased;

    public EcsRawData(int width, int height, int depth,
                      int controllerX, int controllerY, int controllerZ,
                      List<EcsPaletteEntry> palette, short[] voxelData) {
        this(width, height, depth, controllerX, controllerY, controllerZ, palette, voxelData, false);
    }

    public EcsRawData(int width, int height, int depth,
                      int controllerX, int controllerY, int controllerZ,
                      List<EcsPaletteEntry> palette, short[] voxelData, boolean frameBased) {
        this.width = width; this.height = height; this.depth = depth;
        this.controllerX = controllerX; this.controllerY = controllerY; this.controllerZ = controllerZ;
        this.palette = List.copyOf(palette);
        this.voxelData = voxelData;
        this.frameBased = frameBased;
    }
}
