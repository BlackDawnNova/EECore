package com.endlessepoch.core.menu;

import com.endlessepoch.core.nova.block.MachineControllerBlockEntity;
import com.endlessepoch.core.registry.Menus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class DispatchMenu extends AbstractContainerMenu {

    private final MachineControllerBlockEntity be;
    private final String nameEn, nameZh;
    private final net.minecraft.core.BlockPos pos;
    private final int gridRows;
    public int getGridRows() { return gridRows; }
    public net.minecraft.core.BlockPos getPos() { return pos; }
    static final int GRID_HEAD = 17, GRID_ROW = 18, PANEL_H = 130, INV_AREA = 97;

    public DispatchMenu(int id, Inventory inv, MachineControllerBlockEntity be) {
        this(id, inv, be, 3);
    }

    public DispatchMenu(int id, Inventory inv, MachineControllerBlockEntity be, int gridRows) {
        super(Menus.DISPATCH.get(), id);
        this.be = be;
        this.pos = be.getBlockPos();
        this.gridRows = gridRows;
        var def = com.endlessepoch.core.api.multiblock.MachineRegistry.get(be.getMachineId());
        this.nameEn = def.map(com.endlessepoch.core.api.multiblock.MachineDefinition::getNameEn).orElse("Dispatch Center");
        this.nameZh = def.map(com.endlessepoch.core.api.multiblock.MachineDefinition::getNameZh).orElse("调度中心");
        addPlayerSlots(inv, imgH(gridRows));
    }

    public DispatchMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.DISPATCH.get(), id);
        this.be = null;
        this.pos = buf.readBlockPos();
        this.gridRows = buf.readVarInt();
        this.nameEn = buf.readUtf();
        this.nameZh = buf.readUtf();
        addPlayerSlots(inv, imgH(gridRows));
    }

    public static int imgH(int gridRows) { return GRID_HEAD + gridRows * GRID_ROW + PANEL_H + INV_AREA; }

    private void addPlayerSlots(Inventory inv, int h) {
        int invY = h - INV_AREA + 14;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, 8 + col * 18, invY + 58));
    }

    public MachineControllerBlockEntity getBE() { return be; }
    public String getNameEn() { return nameEn; }
    public String getNameZh() { return nameZh; }

    public void rebuildSlots(Inventory inv, int h) {
        slots.clear();
        addPlayerSlots(inv, h);
    }

    @Override public ItemStack quickMoveStack(Player p, int idx) {
        Slot slot = slots.get(idx);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack src = slot.getItem(), s = src.copy();
        if (idx < 27) { // main inv → hotbar
            if (!moveItemStackTo(src, 27, 36, false)) return ItemStack.EMPTY;
        } else { // hotbar → main inv
            if (!moveItemStackTo(src, 0, 27, false)) return ItemStack.EMPTY;
        }
        if (src.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return s;
    }
    @Override public boolean stillValid(Player p) { return true; }
}
