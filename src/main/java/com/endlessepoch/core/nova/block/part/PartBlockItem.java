package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.tier.VoltageTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Part BlockItem with Shift+right-click tier cycling. / Shift+右键循环切换电压等级。
 */
public class PartBlockItem extends BlockItem {

    public PartBlockItem(PartBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            int tier = getTier(stack);
            tier = (tier + 1) % 12;
            setTier(stack, tier);
            if (!level.isClientSide()) {
                VoltageTier vt = VoltageTier.fromOrdinal(tier);
                player.displayClientMessage(Component.literal(vt.name()).withStyle(ChatFormatting.GREEN), true);
            }
            return InteractionResultHolder.success(stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    protected BlockState getPlacementState(BlockPlaceContext ctx) {
        BlockState state = super.getPlacementState(ctx);
        return state.setValue(PartBlock.TIER, getTier(ctx.getItemInHand()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        VoltageTier vt = VoltageTier.fromOrdinal(getTier(stack));
        tooltip.add(Component.literal(vt.name() + " (" + vt.getChineseName() + ")").withStyle(ChatFormatting.GRAY));
    }

    private static int getTier(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null && cd.copyTag().contains("PartTier"))
            return cd.copyTag().getInt("PartTier");
        return 1;
    }

    private static void setTier(ItemStack stack, int tier) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("PartTier", tier);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
