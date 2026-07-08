package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.PartType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Voltage-tier casing block — a structural multiblock part with no facing.
 * 电压等级外壳方块——无朝向的结构部件。
 */
public class CasingBlock extends PartBlock {

    public CasingBlock(Properties properties, PartType type) {
        super(properties, type);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        // No FACING for casing blocks / 外壳无朝向
    }
}
