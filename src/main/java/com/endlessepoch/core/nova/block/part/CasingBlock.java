package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.PartType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;

/**
 * Voltage-tier casing block — a structural part with no facing.
 * 电压等级外壳方块——无朝向结构部件。
 */
public class CasingBlock extends Block implements EntityBlock {

    private final PartType partType;
    private final int tier;

    public CasingBlock(Properties properties, PartType type, int tier) {
        super(properties);
        this.partType = type;
        this.tier = tier;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PartBlockEntity(pos, state, partType, tier);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
    }
}
