package com.endlessepoch.core.client.model;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.block.OreBlock;
import com.endlessepoch.core.block.OreBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic ore model — replaces the original quads' sprite with stone-base texture.
 * Clones each vertex array, recomputes UVs from the target sprite's atlas bounds.
 * 动态矿石模型——替换原版 quad 的纹理为石底，保留顶点位置。
 */
public class OreBakedModel implements IDynamicBakedModel {

    private static final float INFLATE = 0.001f;

    private final BakedModel original;

    public OreBakedModel(BakedModel original) {
        this.original = original;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                     RandomSource random, ModelData data, @Nullable RenderType type) {
        if (state == null)
            return original.getQuads(null, side, random, data, type);

        List<BakedQuad> originalQuads = original.getQuads(state, side, random, data, null);
        if (originalQuads.isEmpty()) return List.of();

        if (type == null || type == RenderType.solid()) {
            TextureAtlasSprite stoneTex = getStoneTexture(data);
            if (stoneTex == null)
                return originalQuads;
            return remapQuads(originalQuads, stoneTex);
        }

        if (type == RenderType.cutout()) {
            TextureAtlasSprite oreTex = getOreTexture(state, data);
            if (oreTex == null) return List.of();
            return remapQuads(originalQuads, oreTex, true);
        }

        return List.of();
    }

    /** 克隆quad并替换sprite+UV，可选沿法线外扩顶点 / Clone quads with new sprite UV, optionally inflating along face normal */
    private static List<BakedQuad> remapQuads(List<BakedQuad> quads, TextureAtlasSprite target) {
        return remapQuads(quads, target, false);
    }

    private static List<BakedQuad> remapQuads(List<BakedQuad> quads, TextureAtlasSprite target, boolean inflate) {
        float u0 = target.getU0(), v0 = target.getV0();
        float uSpan = target.getU1() - u0;
        float vSpan = target.getV1() - v0;
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            int[] src = q.getVertices();
            int[] dst = src.clone();
            Direction dir = q.getDirection();
            float dx = inflate ? dir.getStepX() * INFLATE : 0f;
            float dy = inflate ? dir.getStepY() * INFLATE : 0f;
            float dz = inflate ? dir.getStepZ() * INFLATE : 0f;
            for (int v = 0; v < 4; v++) {
                int off = v * 8;
                if (inflate) {
                    dst[off]     = Float.floatToRawIntBits(Float.intBitsToFloat(dst[off]) + dx);
                    dst[off + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(dst[off + 1]) + dy);
                    dst[off + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(dst[off + 2]) + dz);
                }
                float origU = Float.intBitsToFloat(dst[off + 4]);
                float origV = Float.intBitsToFloat(dst[off + 5]);
                TextureAtlasSprite origSprite = q.getSprite();
                float origU0 = origSprite.getU0(), origV0 = origSprite.getV0();
                float origUSpan = origSprite.getU1() - origU0;
                float origVSpan = origSprite.getV1() - origV0;
                float fracU = origUSpan > 0 ? (origU - origU0) / origUSpan : 0f;
                float fracV = origVSpan > 0 ? (origV - origV0) / origVSpan : 0f;
                dst[off + 4] = Float.floatToRawIntBits(u0 + fracU * uSpan);
                dst[off + 5] = Float.floatToRawIntBits(v0 + fracV * vSpan);
            }
            out.add(new BakedQuad(dst, q.getTintIndex(), dir, target, q.isShade()));
        }
        return out;
    }

    /** 按材质+SPOT_INDEX选矿斑贴图 / Per-material ore spots texture, variant selected by SPOT_INDEX */
    private TextureAtlasSprite getOreTexture(BlockState state, ModelData data) {
        String material = state.getBlock() instanceof OreBlock ob ? ob.getMaterialId() : "iron";
        int spotIdx = data.has(OreBlockEntity.SPOT_INDEX)
                ? Math.abs(data.get(OreBlockEntity.SPOT_INDEX) % OreBlockEntity.SPOT_SUFFIXES.length) : 0;
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID,
                "block/ores/" + material + "_ore_" + spotIdx);
        try {
            var mc = Minecraft.getInstance();
            if (mc != null)
                return mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(rl);
        } catch (Exception ignored) {}
        return null;
    }

    /** 从下方方块取石底贴图，兜底stone / Stone texture from block below, fallback to stone */
    private TextureAtlasSprite getStoneTexture(ModelData data) {
        BlockState below = data.get(OreBlockEntity.BELOW_STATE);
        if (below == null || below.isAir() || below.getBlock() instanceof OreBlock)
            below = Blocks.STONE.defaultBlockState();
        try {
            var mc = Minecraft.getInstance();
            if (mc != null && mc.getModelManager() != null) {
                BakedModel m = mc.getModelManager().getBlockModelShaper().getBlockModel(below);
                if (m != null) {
                    TextureAtlasSprite s = m.getParticleIcon(ModelData.EMPTY);
                    if (s != null) return s;
                }
            }
        } catch (Exception ignored) {}
        try {
            var mc = Minecraft.getInstance();
            if (mc != null)
                return mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                        .apply(ResourceLocation.withDefaultNamespace("block/stone"));
        } catch (Exception ignored) {}
        return null;
    }

    // Delegation / 委托方法

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.solid(), RenderType.cutout());
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return List.of(RenderType.solid());
    }

    @Override public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return original.isGui3d(); }
    @Override public boolean usesBlockLight() { return original.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
    @Override public TextureAtlasSprite getParticleIcon(ModelData data) {
        TextureAtlasSprite s = getStoneTexture(data);
        return s != null ? s : original.getParticleIcon(data);
    }
    @Override public ItemOverrides getOverrides() { return original.getOverrides(); }
}
