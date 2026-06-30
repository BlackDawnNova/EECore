package com.endlessepoch.core.nova.block;

import com.endlessepoch.core.api.multiblock.MultiBlockFormHandler;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.nova.network.transmitter.TransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Minimal transmitter test block.
 * Shift+right-click triggers multiblock formation.
 */
public class TransmitterTestBlock extends Block implements EntityBlock {

    public TransmitterTestBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransmitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof TransmitterBlockEntity tx) tx.serverTick();
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TransmitterBlockEntity tx)) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;

            var patterns = MultiBlockRegistry.getAll(player.getUUID());
            if (patterns.isEmpty()) {
                player.sendSystemMessage(Component.literal(
                        "No pattern registered! Scan a structure first with Multiblock Scanner."));
            } else {
                boolean ok = false;
                for (var e : patterns.entrySet()) {
                    if (MultiBlockFormHandler.tryForm(be, e.getValue(), Direction.NORTH, player)) {
                        player.sendSystemMessage(Component.literal("Formed: " + e.getKey()));
                        ok = true; break;
                    }
                }
                if (!ok) player.sendSystemMessage(Component.literal("Invalid structure"));
            }
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide()) {
            player.sendSystemMessage(Component.literal(
                    "NovaNet Transmitter | Tier: " + tx.getTier().getShortName()
                    + " | Formed: " + tx.isFormed()
                    + " | Buffer: " + tx.getBufferEnergy() + " / " + tx.getBufferCapacity()));
        }
        return InteractionResult.SUCCESS;
    }
}
