package com.endlessepoch.core.nova.block;

import com.endlessepoch.core.registry.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Dispatch center controller block — creates DispatchCenterBlockEntity instead of MachineControllerBlockEntity.
 * Shares all MachineControllerBlock behavior, only differs in BE type (adds AE2 terminal host).
 * FORMED switches the screen model: unformed dark screen_off, formed glowing animated screen_on.
 * 调度中心控制器方块——创建 DispatchCenterBlockEntity，行为不变，仅 BE 类型不同（附加 AE2 终端）。
 * FORMED 切换屏幕模型：未成形黑屏 screen_off，成形发光动画 screen_on。
 */
public class DispatchControllerBlock extends MachineControllerBlock {

    /** Formed flag — drives the screen model variant. 成形标志——驱动屏幕模型变体。 */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public DispatchControllerBlock(Properties properties) {
        super(properties);
        // BooleanProperty defaults to [true,false] — force unformed as default state.
        // BooleanProperty 默认首值 true——覆盖为未成形默认（否则放置即亮屏）。
        registerDefaultState(defaultBlockState().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DispatchCenterBlockEntity(pos, state);
    }
}
