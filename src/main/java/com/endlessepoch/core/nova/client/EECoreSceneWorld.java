package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Virtual world holding a multiblock pattern for 3D preview rendering.
 * Implements BlockAndTintGetter for vanilla block renderer queries.
 * Stores pre-computed surface block info (exposed face bitmask) for fast back-face culling.
 */
@OnlyIn(Dist.CLIENT)
public class EECoreSceneWorld implements BlockAndTintGetter {

    // Exposed face bitmask bits / 暴露面掩码位
    public static final int FACE_DN = 0x01, FACE_UP = 0x02, FACE_NZ = 0x04, FACE_PZ = 0x08, FACE_NX = 0x10, FACE_PX = 0x20;

    private final MultiBlockPattern pattern;
    private final Set<BlockPos> positions;
    private final List<BlockPos> surfacePositions;
    private final Set<BlockPos> controllerPositions;
    private final Map<BlockPos, Integer> exposedFaceMask;
    private final int minY, maxY;
    private final int centerX, centerY, centerZ;

    public EECoreSceneWorld(MultiBlockPattern pattern) {
        this.pattern = pattern;
        this.positions = new LinkedHashSet<>(pattern.getNonAirPositions());
        this.controllerPositions = new HashSet<>(pattern.getNonAirControllers());
        this.centerX = pattern.controllerX;
        this.centerY = pattern.controllerY;
        this.centerZ = pattern.controllerZ;
        this.exposedFaceMask = new HashMap<>();
        var surfaceSet = new ArrayList<BlockPos>();
        for (var pos : positions)
            computeSurface(pos, surfaceSet);
        surfaceSet.sort((a, b) -> Integer.compare(
            distSq(a, centerX, centerY, centerZ),
            distSq(b, centerX, centerY, centerZ)));
        this.surfacePositions = List.copyOf(surfaceSet);
        this.minY = 0;
        this.maxY = pattern.height;
    }

    private static int distSq(BlockPos p, int cx, int cy, int cz) {
        int dx = p.getX() - cx, dy = p.getY() - cy, dz = p.getZ() - cz;
        return dx*dx + dy*dy + dz*dz;
    }

    private void computeSurface(BlockPos pos, List<BlockPos> surfaceList) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        int mask = 0;
        if (getBlockState(new BlockPos(x, y-1, z)).isAir()) mask |= FACE_DN;
        if (getBlockState(new BlockPos(x, y+1, z)).isAir()) mask |= FACE_UP;
        if (getBlockState(new BlockPos(x, y, z-1)).isAir()) mask |= FACE_NZ;
        if (getBlockState(new BlockPos(x, y, z+1)).isAir()) mask |= FACE_PZ;
        if (getBlockState(new BlockPos(x-1, y, z)).isAir()) mask |= FACE_NX;
        if (getBlockState(new BlockPos(x+1, y, z)).isAir()) mask |= FACE_PX;
        if (mask != 0) {
            surfaceList.add(pos);
            exposedFaceMask.put(pos, mask);
        }
    }

    public Set<BlockPos> getPositions() { return positions; }
    public List<BlockPos> getSurfacePositions() { return surfacePositions; }
    public Set<BlockPos> getControllerPositions() { return controllerPositions; }
    public int getExposedFaceMask(BlockPos pos) { return exposedFaceMask.getOrDefault(pos, 0); }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState st = pattern.getExpectedState(pos.getX(), pos.getY(), pos.getZ());
        return st != null ? st : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) { return null; }

    @Override
    public int getHeight() { return maxY; }

    @Override
    public int getMinBuildHeight() { return minY; }

    @Nullable
    @Override
    public LevelLightEngine getLightEngine() { return null; }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) { return 15; }

    @Override
    public float getShade(Direction dir, boolean b) { return 1.0f; }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) { return -1; }
}
