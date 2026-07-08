package com.endlessepoch.core.client.model;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.nova.block.MachineControllerBlock;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * Custom model loader: all machine controller variants share one model JSON.
 * Bakes per-machine composite models (tier casing body + machine overlay) at load time.
 * 自定义模型加载器：所有机器变体共用模型，加载时预合成外壳+面板。
 */
public class MachineModelLoader implements IGeometryLoader<MachineModelLoader.Geometry> {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "machine");

    /** Master baked model created during bake, distributed to variants in ModifyBakingResult. / 烘焙时创建的主模型，在 ModifyBakingResult 中分发给各变体。 */
    public static MasterBaked MASTER;

    @Override
    public Geometry read(JsonObject json, JsonDeserializationContext ctx) {
        return new Geometry();
    }

    public static class Geometry implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext ctx, ModelBaker baker,
                               Function<net.minecraft.client.resources.model.Material, TextureAtlasSprite> spriteGetter,
                               ModelState modelState, ItemOverrides overrides) {
            MASTER = new MasterBaked(spriteGetter, overrides);
            MASTER.bakeSubModels(baker, spriteGetter);
            return MASTER;
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter,
                                   IGeometryBakingContext ctx) {}
    }

    /**
     * Holds all machine-specific sub-models. Each model=N variant gets one entry.
     * 存储所有机器子模型，每个 model=N 变体对应一条记录。
     */
    public static class MasterBaked implements IDynamicBakedModel {

        private final Function<net.minecraft.client.resources.model.Material, TextureAtlasSprite> spriteGetter;
        private final ItemOverrides overrides;

        private final Map<Integer, Map<Direction, List<BakedQuad>>> bodyQuads = new HashMap<>();
        private final Map<Integer, BakedQuad> overlayQuads = new HashMap<>();
        private final Map<Integer, BakedQuad> emissiveQuads = new HashMap<>();
        private final Map<Integer, TextureAtlasSprite> particles = new HashMap<>();
        private BakedModel fallback;

        MasterBaked(Function<net.minecraft.client.resources.model.Material, TextureAtlasSprite> spriteGetter,
                    ItemOverrides overrides) {
            this.spriteGetter = spriteGetter;
            this.overrides = overrides;
        }

        // Bake sub-models / 烘焙子模型

        void bakeSubModels(ModelBaker baker,
                           Function<net.minecraft.client.resources.model.Material, TextureAtlasSprite> sprites) {
            ModelState rotState = BlockModelRotation.X0_Y0;

            for (var entry : MachineControllerBlock.getModelIndices().entrySet()) {
                String itemId = entry.getKey();
                int idx = entry.getValue();
                try { bakeOne(itemId, idx, baker, sprites, rotState); }
                catch (Exception e) { EECore.LOGGER.warn("MachineModelLoader: {}", e.toString()); }
            }

            // Fallback: try machine_controller model / 回退：尝试使用 machine_controller 模型
            try {
                ResourceLocation fl = ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "block/machine_controller");
                fallback = baker.bake(fl, rotState, sprites);
            } catch (Exception ignored) {}
        }

        private void bakeOne(String itemId, int idx, ModelBaker baker,
                             Function<net.minecraft.client.resources.model.Material, TextureAtlasSprite> sprites,
                             ModelState modelState) {
            var defOpt = com.endlessepoch.core.api.multiblock.MachineRegistry.getByItemId(itemId);
            if (defOpt.isEmpty()) return;
            var def = defOpt.get();
            String casingName = com.endlessepoch.core.api.tier.VoltageTier
                    .fromOrdinal(def.getTier()).name().toLowerCase();

            // Bake tier casing model / 烘焙等级外壳模型
            ResourceLocation casingLoc = ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID,
                    "block/casings/voltage/" + casingName);
            BakedModel casingModel = baker.bake(casingLoc, modelState, sprites);
            if (casingModel == null) return;

            // Cache body quads per direction / 按方向缓存外壳方块面
            Map<Direction, List<BakedQuad>> dirQuads = new HashMap<>();
            for (Direction dir : Direction.values())
                dirQuads.put(dir, casingModel.getQuads(null, dir, RandomSource.create(),
                        ModelData.EMPTY, null));
            dirQuads.put(null, casingModel.getQuads(null, null, RandomSource.create(),
                    ModelData.EMPTY, null));
            bodyQuads.put(idx, dirQuads);

            // Overlay sprites / 面板纹理
            TextureAtlasSprite overlaySprite = sprites.apply(material(
                    "block/machines/" + itemId + "/overlay_front"));
            TextureAtlasSprite emissiveSprite;
            try {
                emissiveSprite = sprites.apply(material(
                        "block/machines/" + itemId + "/overlay_front_e"));
            } catch (Exception e) { emissiveSprite = overlaySprite; }

            overlayQuads.put(idx, QuadBuilder.face(Direction.NORTH,
                    0f, 0f, -0.01f, 16f, 16f, -0.01f, overlaySprite, false));
            emissiveQuads.put(idx, QuadBuilder.face(Direction.NORTH,
                    0f, 0f, -0.02f, 16f, 16f, -0.02f, emissiveSprite, false));
            particles.put(idx, overlaySprite);
        }

        // Per-variant access / 按变体分发

        public BakedModel forModelIndex(int idx) {
            if (bodyQuads.containsKey(idx)) return new VariantBaked(this, idx);
            return fallback;
        }

        // IDynamicBakedModel / 动态烘焙模型接口实现

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                         RandomSource random, ModelData data, @Nullable RenderType type) {
            if (state == null) return fallbackQuads(side);
            int idx = state.hasProperty(MachineControllerBlock.MODEL)
                    ? state.getValue(MachineControllerBlock.MODEL) : 0;
            return getQuads(idx, side, type);
        }

        List<BakedQuad> getQuads(int idx, @Nullable Direction side, @Nullable RenderType type) {
            if (type == RenderType.cutoutMipped()) {
                BakedQuad eq = emissiveQuads.get(idx);
                if (eq != null && (side == null || side == Direction.NORTH)) return List.of(eq);
                return List.of();
            }
            if (type != null && type != RenderType.solid()) return List.of();

            Map<Direction, List<BakedQuad>> dirMap = bodyQuads.get(idx);
            if (dirMap == null) return fallbackQuads(side);
            List<BakedQuad> body = dirMap.get(side);
            if (body == null) body = List.of();
            List<BakedQuad> result = new ArrayList<>(body);
            if (side == null || side == Direction.NORTH) {
                BakedQuad oq = overlayQuads.get(idx);
                if (oq != null) result.add(oq);
            }
            return result;
        }

        private List<BakedQuad> fallbackQuads(@Nullable Direction side) {
            return fallback != null ? fallback.getQuads(null, side, RandomSource.create(),
                    ModelData.EMPTY, null) : List.of();
        }

        @Override public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return List.of(RenderType.solid(), RenderType.cutoutMipped());
        }
        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return ChunkRenderTypeSet.of(RenderType.solid(), RenderType.cutoutMipped());
        }
        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }

        @Override public TextureAtlasSprite getParticleIcon() { return getParticleIcon(ModelData.EMPTY); }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            for (var s : particles.values()) if (s != null) return s;
            return fallback != null ? fallback.getParticleIcon() : null;
        }

        @Override public ItemOverrides getOverrides() { return overrides; }

        private net.minecraft.client.resources.model.Material material(String path) {
            return new net.minecraft.client.resources.model.Material(
                    net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                    ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, path));
        }
    }

    /**
     * Thin wrapper delegating to MasterBaked for a specific model index.
     * 薄包装层，将请求委托给 MasterBaked 的特定模型索引。
     */
    static class VariantBaked implements IDynamicBakedModel {
        private final MasterBaked master;
        private final int idx;

        VariantBaked(MasterBaked master, int idx) { this.master = master; this.idx = idx; }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                         RandomSource random, ModelData data, @Nullable RenderType type) {
            return master.getQuads(idx, side, type);
        }
        @Override public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return master.getRenderTypes(stack, fabulous);
        }
        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return master.getRenderTypes(state, random, data);
        }
        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return master.getParticleIcon(); }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return master.getParticleIcon(data); }
        @Override public ItemOverrides getOverrides() { return master.overrides; }
    }
}
