package com.endlessepoch.core.menu.creative;

import com.endlessepoch.core.nova.block.part.CreativeHatchBlockEntity;
import com.endlessepoch.core.registry.Menus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Menu for the creative energy input hatch — syncs the selected tier and maps
 * the ◀/▶ buttons (ids 50/51) to tier changes on the server.
 * 创造能源输入仓菜单——同步所选电压档，◀/▶ 按钮（id 50/51）映射服务端调档。
 */
public class CreativeHatchMenu extends AbstractContainerMenu {

    private final CreativeHatchBlockEntity be;
    private final Level level;
    private int clientTier = 1;
    private int clientAmp = 16;

    public CreativeHatchMenu(int id, Inventory inv, CreativeHatchBlockEntity be) {
        super(Menus.CREATIVE_HATCH.get(), id);
        this.be = be;
        this.level = inv.player.level();
        addDataSlots();
        addPlayerSlots(inv);
    }

    public CreativeHatchMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.CREATIVE_HATCH.get(), id);
        this.level = inv.player.level();
        BlockPos pos = buf.readBlockPos();
        this.be = level.getBlockEntity(pos) instanceof CreativeHatchBlockEntity ch ? ch : null;
        addDataSlots();
        addPlayerSlots(inv);
    }

    private void addPlayerSlots(Inventory inv) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new net.minecraft.world.inventory.Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            this.addSlot(new net.minecraft.world.inventory.Slot(inv, col, 8 + col * 18, 142));
    }

    private void addDataSlots() {
        addDataSlot(new DataSlot() {
            @Override public int get() { return be != null ? be.getCreativeTier() : clientTier; }
            @Override public void set(int v) { clientTier = v; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return be != null ? be.getAmperage() : clientAmp; }
            @Override public void set(int v) { clientAmp = v; }
        });
    }

    /**
     * Currently selected tier ordinal. Client side must read the synced slot value —
     * the client BE's field never syncs (BEs don't push NBT on change).
     * 当前所选电压档。客户端必须读同步槽值——客户端 BE 字段不会随改动同步。
     */
    public int tierOrdinal() {
        return level.isClientSide() ? clientTier : (be != null ? be.getCreativeTier() : clientTier);
    }

    /** Synced amperage. / 已同步的安培数。 */
    public int ampValue() {
        return level.isClientSide() ? clientAmp : (be != null ? be.getAmperage() : clientAmp);
    }

    public CreativeHatchBlockEntity getBlockEntity() { return be; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be == null) return false;
        return switch (id) {
            case 50 -> { be.setCreativeTier(be.getCreativeTier() - 1); yield true; }
            case 51 -> { be.setCreativeTier(be.getCreativeTier() + 1); yield true; }
            case 60 -> { be.setCreativeTier(1); yield true; }   // LV preset / LV 预设
            case 61 -> { be.setCreativeTier(3); yield true; }   // HV preset / HV 预设
            case 62 -> { be.setCreativeTier(11); yield true; }  // QV preset / QV 预设
            case 100 -> { be.cycleAmp(true); yield true; }      // amp up / 安培升档
            case 101 -> { be.cycleAmp(false); yield true; }     // amp down / 安培降档
            default -> super.clickMenuButton(player, id);
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        if (be == null) return false;
        return player.distanceToSqr(
                be.getBlockPos().getX() + 0.5,
                be.getBlockPos().getY() + 0.5,
                be.getBlockPos().getZ() + 0.5) <= 64;
    }
}
