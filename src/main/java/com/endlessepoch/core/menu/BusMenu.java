package com.endlessepoch.core.menu;

import com.endlessepoch.core.nova.block.part.InputBusBlockEntity;
import com.endlessepoch.core.registry.Menus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Container for input/output bus inventory + player inventory.
 * 总线容器——总线格 + 玩家背包。
 */
public class BusMenu extends AbstractContainerMenu {

    private final InputBusBlockEntity bus;
    private final int slotCount;
    private final BlockPos pos;

    /** Server-side constructor. / 服务端构造器。 */
    public BusMenu(int id, Inventory inv, InputBusBlockEntity bus) {
        super(Menus.BUS.get(), id);
        this.bus = bus;
        this.slotCount = bus.getSlotCount();
        this.pos = bus.getBlockPos();
        addBusSlots(bus.getInventory());
        addPlayerSlots(inv);
    }

    /** Client-side constructor from network buffer. / 客户端从网络包构造。 */
    public BusMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.BUS.get(), id);
        this.pos = buf.readBlockPos();
        this.slotCount = buf.readVarInt();
        this.bus = null;
        addBusSlots(new net.neoforged.neoforge.items.wrapper.InvWrapper(
                new net.minecraft.world.SimpleContainer(slotCount)));
        addPlayerSlots(inv);
    }

    private void addBusSlots(net.neoforged.neoforge.items.IItemHandler handler) {
        int cols = Math.min(slotCount, 9);
        int x = 8 + (9 - cols) * 9;
        for (int i = 0; i < slotCount; i++) {
            int row = i / 9;
            int col = i % 9;
            this.addSlot(new SlotItemHandler(handler, i,
                    x + col * 18, 18 + row * 18));
        }
    }

    private void addPlayerSlots(Inventory inv) {
        int totalRows = (slotCount + 8) / 9;
        int gap = totalRows <= 3 ? 14 : 20;
        int invY = 18 + totalRows * 18 + gap;
        int hotY = invY + 3 * 18 + 4;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
        for (int col = 0; col < 9; col++)
            this.addSlot(new Slot(inv, col, 8 + col * 18, hotY));
    }

    public InputBusBlockEntity getBus() { return bus; }
    public int getSlotCount() { return slotCount; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack remainder = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return remainder;
        ItemStack stack = slot.getItem();
        remainder = stack.copy();

        if (index < slotCount) {
            if (!this.moveItemStackTo(stack, slotCount, this.slots.size(), true))
                return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(stack, 0, slotCount, false))
                return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return remainder;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
