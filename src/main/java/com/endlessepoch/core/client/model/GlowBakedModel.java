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

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a baked model to switch render layer to cutoutMipped and add UV-offset glow.
 * <p>
 * 包装模型：改用 cutoutMipped 渲染层，并生成 UV 偏移辉光 quad。
 */
public class GlowBakedModel implements IDynamicBakedModel {

    private final BakedModel base;
    private final TextureAtlasSprite emissiveSprite;

    /** @param sprite emissive texture sprite, null disables glow / null 则无辉光 */
    public GlowBakedModel(BakedModel base, @Nullable TextureAtlasSprite sprite) {
        this.base = base;
        this.emissiveSprite = sprite;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction dir,
                                     RandomSource random, ModelData data, @Nullable RenderType type) {
        List<BakedQuad> quads = base.getQuads(state, dir, random, data, type);

        // Add UV-offset glow copies for emissive quads on north face in cutoutMipped
        if (type == RenderType.cutoutMipped() && dir == Direction.NORTH && emissiveSprite != null) {
            List<BakedQuad> result = new ArrayList<>(quads);
            for (BakedQuad q : quads) {
                if (isEmissive(q)) {
                    result.add(offsetUV(q, 1, 0));
                    result.add(offsetUV(q, -1, 0));
                    result.add(offsetUV(q, 0, 1));
                    result.add(offsetUV(q, 0, -1));
                }
            }
            return result;
        }
        return quads;
    }

    /** Check if the quad uses the registered emissive texture. / 判断是否发光贴图 quad。 */
    private boolean isEmissive(BakedQuad q) {
        TextureAtlasSprite s = q.getSprite();
        if (s == null || emissiveSprite == null) return false;
        return s.contents().name().equals(emissiveSprite.contents().name());
    }

    /** Clone a quad with UV offset by du/dv pixels, clamped to sprite bounds. / 克隆 quad 并偏移 UV。 */
    private BakedQuad offsetUV(BakedQuad q, int du, int dv) {
        int[] data = q.getVertices().clone();
        float uStep = (emissiveSprite.getU(1) - emissiveSprite.getU(0)) * du;
        float vStep = (emissiveSprite.getV(1) - emissiveSprite.getV(0)) * dv;
        float uMin = emissiveSprite.getU(0.5f);
        float uMax = emissiveSprite.getU(emissiveSprite.contents().width() - 0.5f);
        float vMin = emissiveSprite.getV(0.5f);
        float vMax = emissiveSprite.getV(emissiveSprite.contents().height() - 0.5f);
        int stride = 8;
        for (int i = 0; i < 4; i++) {
            int uv = i * stride + 4;
            data[uv]     = Float.floatToRawIntBits(clamp(
                    Float.intBitsToFloat(data[uv]) + uStep, uMin, uMax));
            data[uv + 1] = Float.floatToRawIntBits(clamp(
                    Float.intBitsToFloat(data[uv + 1]) + vStep, vMin, vMax));
        }
        return new BakedQuad(data, q.getTintIndex(), q.getDirection(), emissiveSprite, q.isShade());
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.cutoutMipped());
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
