package com.endlessepoch.core.nova.item;

import com.endlessepoch.core.api.field.INovaNode;
import com.endlessepoch.core.nova.network.laser.LaserLinkCard;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Laser link card — shift+right-click nodes to bind, shift+right-click air to clear.
 */
public class LaserLinkCardItem extends Item {

    public LaserLinkCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();

        if (player == null) return InteractionResult.PASS;
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        if (!(be instanceof INovaNode node)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        LaserLinkCard.onShiftUse(stack, node.getBlockPos(), node.getNodeType(),
                node.getNodeId(), node.getTier().getShortName(),
                Math.sqrt(player.blockPosition().distSqr(node.getBlockPos())), player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            LaserLinkCard.onShiftAir(stack, player);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }
}
