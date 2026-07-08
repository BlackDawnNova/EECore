package com.endlessepoch.core.client.model;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import net.minecraft.core.Direction;

/**
 * Wrapper that disables ambient occlusion for seamless casing walls.
 * Turns off AO to prevent edge-darkening on tileable textures.
 * 包装器，禁用环境光遮蔽以实现无缝外壳墙面，防止可平铺纹理的边角变暗。
 */
public class CasingBakedModel implements IDynamicBakedModel {

    private final BakedModel base;

    public CasingBakedModel(BakedModel base) {
        this.base = base;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                     RandomSource random, ModelData data, @Nullable RenderType type) {
        return base.getQuads(state, side, random, data, type);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override public boolean isGui3d() { return base.isGui3d(); }
    @Override public boolean usesBlockLight() { return base.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return base.getParticleIcon(); }
    @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return base.getParticleIcon(data); }
    @Override public ItemOverrides getOverrides() { return base.getOverrides(); }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return base.getRenderTypes(stack, fabulous);
    }
    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return base.getRenderTypes(state, random, data);
    }
}
