package com.endlessepoch.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * Provides BELOW_STATE and SPOT_INDEX ModelData for OreBakedModel.
 * Caches belowState, invalidated on neighbour changes.
 * 提供底岩状态和矿斑索引，缓存底岩结果，邻居变化时刷新。
 */
public class OreBlockEntity extends BlockEntity {

    public static final ModelProperty<BlockState> BELOW_STATE = new ModelProperty<>();
    public static final ModelProperty<Integer> SPOT_INDEX = new ModelProperty<>();

    public static final String[] SPOT_SUFFIXES = {
        "dull_ore", "dull_ore_small", "fine_ore", "flint_ore", "diamond_ore"
    };

    private int spotIndex = -1;
    private BlockState cachedBelow;
    private boolean belowValid;

    public OreBlockEntity(BlockPos pos, BlockState blockState) {
        super(com.endlessepoch.core.registry.BlockEntities.ORE.get(), pos, blockState);
    }

    public void invalidateBelowCache() { belowValid = false; cachedBelow = null; }

    private int getSpotIndex() {
        if (spotIndex < 0) {
            spotIndex = getBlockState().getBlock() instanceof OreBlock ob
                    ? ob.getSpotIndex() : 0;
        }
        return spotIndex;
    }

    @Override
    public ModelData getModelData() {
        if (level == null) return ModelData.EMPTY;
        if (!belowValid) {
            BlockPos checkPos = getBlockPos().below();
            for (int i = 0; i < 128; i++) {
                BlockState s = level.getBlockState(checkPos);
                if (!s.isAir() && s.getFluidState().isEmpty() && !(s.getBlock() instanceof OreBlock)) {
                    cachedBelow = s;
                    break;
                }
                checkPos = checkPos.below();
            }
            belowValid = true;
        }
        var builder = ModelData.builder().with(SPOT_INDEX, getSpotIndex());
        if (cachedBelow != null) builder = builder.with(BELOW_STATE, cachedBelow);
        return builder.build();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("spotIndex", spotIndex);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        spotIndex = tag.getInt("spotIndex");
    }
}
