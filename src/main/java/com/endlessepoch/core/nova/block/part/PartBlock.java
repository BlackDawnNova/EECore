package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.MultiBlockFormHandler;
import com.endlessepoch.core.api.multiblock.MultiBlockValidator;
import com.endlessepoch.core.api.multiblock.PartType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Base block for all multiblock parts (bus, hatch, assembly).
 * Stores FACING, links to controller via BE on formation.
 * 多方块部件基类，存朝向，成形时通过 BE 链接控制器。
 */
public class PartBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, 11); // 0=ELV..11=QV
    private final PartType partType;

    public PartBlock(Properties properties, PartType type) {
        super(properties);
        this.partType = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TIER, 1));
    }

    public PartType getPartType() { return partType; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TIER);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PartBlockEntity(pos, state, partType);
    }

    /**
     * When the part block is broken, notify the controller to re-validate.
     * 部件方块被破坏时通知控制器重新验证。
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof com.endlessepoch.core.api.multiblock.IPart part && part.isFormed()) {
                BlockPos ctrl = part.getControllerPos();
                BlockEntity ctrlBe = level.getBlockEntity(ctrl);
                if (ctrlBe instanceof com.endlessepoch.core.nova.block.MachineControllerBlockEntity mc
                        && mc.getMachineId() != null && mc.getMachineId().equals(part.getMachineId())) {
                    var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(part.getMachineId());
                    if (pattern.isPresent() && !MultiBlockValidator.validate(
                            level, pattern.get(), ctrl, mc.getFacing())) {
                        mc.onMultiblockBroken();
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
