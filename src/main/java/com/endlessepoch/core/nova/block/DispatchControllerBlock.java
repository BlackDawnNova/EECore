package com.endlessepoch.core.nova.block;

import com.endlessepoch.core.registry.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dispatch center controller block — creates DispatchCenterBlockEntity instead of MachineControllerBlockEntity.
 * Shares all MachineControllerBlock behavior, only differs in BE type (adds AE2 terminal host).
 * 调度中心控制器方块——创建 DispatchCenterBlockEntity，行为不变，仅 BE 类型不同（附加 AE2 终端）。
 */
public class DispatchControllerBlock extends MachineControllerBlock {

    public DispatchControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DispatchCenterBlockEntity(pos, state);
    }
}
