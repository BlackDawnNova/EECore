package com.endlessepoch.core.nova.network.laser;

import com.endlessepoch.core.api.field.NodeType;
import com.endlessepoch.core.nova.network.transmitter.TransmitterRangeScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.UUID;

/**
 * Laser link card binding logic (NeoForge 1.21.1 Data Components API).
 * <p>
 * 激光链接卡绑定逻辑（NeoForge 1.21.1 Data Components API）。
 * <p>
 * Uses CustomData data component for persisting binding state on the item.
 * 使用 CustomData 数据组件在物品上持久化绑定状态。
 * Binding flow (like AE2 P2P Memory Card):
 * 绑定流程（类似 AE2 P2P 内存卡）：
 * <ol>
 *   <li>Shift+right-click a node → binds it / 潜行+右键节点 → 绑定</li>
 *   <li>Shift+right-click second node of opposite type → creates link / 潜行+右键异类节点 → 创建链接</li>
 *   <li>Shift+right-click air → clears binding / 潜行+右键空气 → 清除绑定</li>
 * </ol>
 */
public final class LaserLinkCard {

    private LaserLinkCard() {}

    public static boolean onShiftUse(ItemStack stack, BlockPos targetPos,
                                      NodeType targetType, UUID targetId,
                                      String tierName, double distance, Player player) {
        if (player.level().isClientSide()) return true;

        BlockPos boundPos = getBoundPos(stack);
        NodeType boundType = getBoundType(stack);

        if (boundPos == null || boundType == null) {
            bind(stack, targetPos, targetType, targetId, tierName);
            player.sendSystemMessage(Component.translatable("eecore.laser.bind.first",
                    targetType.name(), tierName));
            return true;
        }

        if (boundType != targetType) {
            double efficiency = TransmitterRangeScanner.getEfficiency(distance);
            int pct = (int)(efficiency * 100);

            player.sendSystemMessage(Component.translatable("eecore.laser.bound",
                    String.format("%.1f", distance), pct));
            clearBinding(stack);
            return true;
        }

        bind(stack, targetPos, targetType, targetId, tierName);
        return true;
    }

    public static void onShiftAir(ItemStack stack, Player player) {
        if (getBoundPos(stack) != null) {
            clearBinding(stack);
            if (!player.level().isClientSide()) {
                player.sendSystemMessage(Component.translatable("eecore.laser.cleared"));
            }
        }
    }

    private static CompoundTag getTag(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null ? cd.copyTag() : new CompoundTag();
    }

    private static void setTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void bind(ItemStack stack, BlockPos pos, NodeType type, UUID id, String tier) {
        CompoundTag tag = getTag(stack);
        tag.putString("ee_laser_type", type.name());
        tag.putLong("ee_laser_pos", pos.asLong());
        tag.putUUID("ee_laser_id", id);
        tag.putString("ee_laser_tier", tier);
        setTag(stack, tag);
    }

    private static void clearBinding(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        tag.remove("ee_laser_type");
        tag.remove("ee_laser_pos");
        tag.remove("ee_laser_id");
        tag.remove("ee_laser_tier");
        setTag(stack, tag);
    }

    public static NodeType getBoundType(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        String s = tag.getString("ee_laser_type");
        if (s.isEmpty()) return null;
        try { return NodeType.valueOf(s); }
        catch (IllegalArgumentException e) { return null; }
    }

    public static BlockPos getBoundPos(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        long l = tag.getLong("ee_laser_pos");
        if (l == 0) return null;
        return BlockPos.of(l);
    }

    public static UUID getBoundId(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        if (!tag.hasUUID("ee_laser_id")) return null;
        return tag.getUUID("ee_laser_id");
    }
}
