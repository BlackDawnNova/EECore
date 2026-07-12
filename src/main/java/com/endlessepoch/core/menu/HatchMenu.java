package com.endlessepoch.core.menu;

import com.endlessepoch.core.nova.block.part.PartBlockEntity;
import com.endlessepoch.core.registry.Menus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Energy/fluid hatch status screen container. / 能源/流体仓状态屏幕容器。 */
public class HatchMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private final PartBlockEntity hatch;

    public HatchMenu(int id, Inventory inv, PartBlockEntity hatch) {
        super(Menus.HATCH.get(), id);
        this.hatch = hatch;
        this.pos = hatch.getBlockPos();
        addSlots(inv);
    }

    public HatchMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.HATCH.get(), id);
        this.pos = buf.readBlockPos();
        this.hatch = null;
        addSlots(inv);
    }

    private void addSlots(Inventory inv) {
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, 8 + c * 18, 142));
    }

    public PartBlockEntity getHatch() { return hatch; }

    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p) {
        return p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
