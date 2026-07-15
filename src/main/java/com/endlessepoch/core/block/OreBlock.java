package com.endlessepoch.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * Ore block — stone base from block below, randomly-assigned spot variant.
 * 矿石方块——石底来自下方方块，注册时随机分配矿斑变体。
 */
public class OreBlock extends Block implements EntityBlock {

    private final String materialId;
    private final int spotIndex;

    public OreBlock(Properties props, String materialId) {
        super(props);
        this.materialId = materialId;
        this.spotIndex = new java.util.Random().nextInt(
                com.endlessepoch.core.block.OreBlockEntity.SPOT_SUFFIXES.length);
    }

    public String getMaterialId() { return materialId; }
    public int getSpotIndex() { return spotIndex; }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OreBlockEntity(pos, state);
    }

    @Override public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ResourceLocation rawId = ResourceLocation.fromNamespaceAndPath("eecore", "raw_" + materialId);
        var rawItem = BuiltInRegistries.ITEM.get(rawId);
        if (rawItem == null) return List.of(new ItemStack(this.asItem()));
        return List.of(new ItemStack(rawItem, 1));
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos fromPos, boolean moved) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof OreBlockEntity obe)
            obe.invalidateBelowCache();
    }
}
