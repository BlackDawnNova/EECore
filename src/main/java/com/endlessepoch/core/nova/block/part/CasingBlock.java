package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.PartType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Voltage-tier casing block — a structural part with no facing.
 * 电压等级外壳方块——无朝向结构部件。
 */
public class CasingBlock extends Block implements EntityBlock {

    private final PartType partType;

    public CasingBlock(Properties properties, PartType type) {
        super(properties);
        this.partType = type;
    }

    public PartType getPartType() { return partType; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PartBlockEntity(pos, state, partType);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof com.endlessepoch.core.api.multiblock.IPart part && part.isFormed()) {
                BlockPos ctrl = part.getControllerPos();
                BlockEntity ctrlBe = level.getBlockEntity(ctrl);
                if (ctrlBe instanceof com.endlessepoch.core.nova.block.MachineControllerBlockEntity mc
                        && mc.getMachineId() != null && mc.getMachineId().equals(part.getMachineId())) {
                    var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(part.getMachineId());
                    if (pattern.isPresent() && !com.endlessepoch.core.api.multiblock.MultiBlockValidator.validate(
                            level, pattern.get(), ctrl, mc.getFacing())) {
                        mc.onMultiblockBroken();
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
