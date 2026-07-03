package com.endlessepoch.core.nova.block;

import com.endlessepoch.core.api.multiblock.MultiBlockFormHandler;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Scanner controller block — placed as part of a structure, detected as 'K' by the scanner.
 * Records the player's facing when placed so the scanner normalizes the pattern to north-facing.
 */
public class ScannerControllerBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ScannerControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
        return new ScannerControllerBlockEntity(pos, state);
    }

    /**
     * On sneak-click: attempt to form any registered multiblock pattern at this controller.
     * Server-side only; client returns SUCCESS to prevent off-hand activation.
     * <p>
     * 潜行右键点击时：尝试在此控制器位置成型任一已注册的多方块模式。仅服务端执行，客户端返回 SUCCESS 以防副手触发。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ScannerControllerBlockEntity sc)) return InteractionResult.PASS;

        var patterns = MultiBlockRegistry.getAll(player.getUUID());
        if (patterns.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "No pattern registered! Scan a structure first."));
            return InteractionResult.SUCCESS;
        }

        for (var e : patterns.entrySet()) {
            if (MultiBlockFormHandler.tryForm(be, e.getValue(), sc.getFacing(), player)) {
                player.sendSystemMessage(Component.literal("Formed: " + e.getKey()));
                return InteractionResult.SUCCESS;
            }
        }
        player.sendSystemMessage(Component.literal("Invalid structure"));
        return InteractionResult.SUCCESS;
    }
}
