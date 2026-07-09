package com.endlessepoch.core.menu;

import com.endlessepoch.core.nova.block.MachineControllerBlockEntity;
import com.endlessepoch.core.registry.Menus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Placeholder machine menu — shows machine GUI with player inventory.
 * 占位机器菜单——显示机器界面+玩家背包。
 */
public class MachineMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private final String nameEn, nameZh;

    public MachineMenu(int id, Inventory inv, MachineControllerBlockEntity mc) {
        super(Menus.MACHINE.get(), id);
        this.pos = mc.getBlockPos();
        this.nameEn = ""; this.nameZh = ""; // server doesn't need names
        addPlayerSlots(inv);
    }

    public MachineMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.MACHINE.get(), id);
        this.pos = buf.readBlockPos();
        this.nameEn = buf.readUtf();
        this.nameZh = buf.readUtf();
        addPlayerSlots(inv);
    }

    public String getNameEn() { return nameEn; }
    public String getNameZh() { return nameZh; }

    private void addPlayerSlots(Inventory inv) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 124 + row * 18));
        for (int col = 0; col < 9; col++)
            this.addSlot(new Slot(inv, col, 8 + col * 18, 182));
    }

    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p) { return true; }
}
