package com.endlessepoch.core.client.model;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Wraps a baked model to force emissive render type (cutoutMipped) for glow effects.
 * 包装已烘焙模型，强制使用发光渲染通道（cutoutMipped）以实现发光效果。
 */
public class GlowBakedModel implements IDynamicBakedModel {

    private final BakedModel base;

    public GlowBakedModel(BakedModel base) {
        this.base = base;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction dir,
                                     RandomSource random, ModelData data, @Nullable RenderType type) {
        return base.getQuads(state, dir, random, data, type);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.cutoutMipped(), RenderType.translucent());
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return List.of(RenderType.solid());
    }

    @Override public boolean useAmbientOcclusion() { return base.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return base.isGui3d(); }
    @Override public boolean usesBlockLight() { return base.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return base.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return base.getParticleIcon(); }
    @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return base.getParticleIcon(data); }
    @Override public ItemOverrides getOverrides() { return base.getOverrides(); }
}
