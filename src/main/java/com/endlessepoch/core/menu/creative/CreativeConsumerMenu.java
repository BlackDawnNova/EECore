package com.endlessepoch.core.menu.creative;

import com.endlessepoch.core.api.tier.VoltageTier;
import com.endlessepoch.core.blockentity.creative.CreativeConsumerBlockEntity;
import com.endlessepoch.core.registry.Menus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CreativeConsumerMenu extends AbstractContainerMenu {
    private final CreativeConsumerBlockEntity be;
    private final Level level;
    private final Player player;

    public CreativeConsumerMenu(int id, Inventory inv, CreativeConsumerBlockEntity be) {
        super(Menus.CREATIVE_CONSUMER.get(), id);
        this.be = be;
        this.level = inv.player.level();
        this.player = inv.player;
        addPlayerSlots(inv);
    }

    public CreativeConsumerMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.CREATIVE_CONSUMER.get(), id);
        this.level = inv.player.level();
        this.player = inv.player;
        BlockPos pos = buf.readBlockPos();
        var entity = level.getBlockEntity(pos);
        if (entity instanceof CreativeConsumerBlockEntity be) {
            this.be = be;
        } else {
            this.be = null;
        }
        addPlayerSlots(inv);
    }

    private void addPlayerSlots(Inventory inv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    // ===== 服务端操作方法 =====
    public void clearAll() { if (be != null) be.clearAll(); }
    public void setLogToChat(boolean enabled) { if (be != null) be.setLogToChat(enabled); }
    public void toggleAutoMode() { if (be != null) be.toggleAutoMode(); }
    public void setManualTier(VoltageTier tier) { if (be != null) be.setManualTier(tier); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be == null) return false;
        return switch (id) {
            case 0 -> { be.clearAll(); yield true; }
            case 1 -> { be.toggleAutoMode(); yield true; }
            case 2 -> { be.setLogToChat(!be.isLogToChat()); yield true; }
            case 50 -> {
                VoltageTier prev = be.getManualTier().prev();
                if (prev != be.getManualTier() && prev != VoltageTier.ELV) be.setManualTier(prev);
                yield true;
            }
            case 51 -> {
                VoltageTier next = be.getManualTier().next();
                if (next != be.getManualTier()) be.setManualTier(next);
                yield true;
            }
            default -> super.clickMenuButton(player, id);
        };
    }

    public CreativeConsumerBlockEntity getBlockEntity() { return be; }
    public Level getLevel() { return level; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        if (be == null) return false;
        return player.distanceToSqr(
                be.getBlockPos().getX() + 0.5,
                be.getBlockPos().getY() + 0.5,
                be.getBlockPos().getZ() + 0.5
        ) <= 64;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (be != null) be.removeSubscriber(player);
    }
}