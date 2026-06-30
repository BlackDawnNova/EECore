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

import java.util.HashMap;
import java.util.Map;

/**
 * Virtual world holding a multiblock pattern for 3D preview rendering.
 * Implements BlockAndTintGetter for vanilla block renderer queries.
 */
@OnlyIn(Dist.CLIENT)
public class EECoreSceneWorld implements BlockAndTintGetter {

    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final int minY, maxY;

    public EECoreSceneWorld(MultiBlockPattern pattern) {
        for (int y = 0; y < pattern.height; y++)
            for (int z = 0; z < pattern.depth; z++)
                for (int x = 0; x < pattern.width; x++) {
                    char c = pattern.getChar(x, y, z);
                    if (c == ' ' || c == '_') continue;
                    BlockState state = pattern.getExpectedState(x, y, z);
                    if (state != null) blocks.put(new BlockPos(x, y, z), state);
                }
        this.minY = 0;
        this.maxY = pattern.height;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
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
    public float getShade(Direction dir, boolean b) { return 0.6f; }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) { return -1; }
}
