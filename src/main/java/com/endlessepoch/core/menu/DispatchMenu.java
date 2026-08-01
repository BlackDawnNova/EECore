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
    private final Inventory inv;
    private final String nameEn, nameZh;
    private final net.minecraft.core.BlockPos pos;
    private final int gridRows;
    private final net.minecraft.world.inventory.SimpleContainerData formedData = new net.minecraft.world.inventory.SimpleContainerData(1);
    public int getGridRows() { return gridRows; }
    public net.minecraft.core.BlockPos getPos() { return pos; }
    static final int GRID_HEAD = 17, GRID_ROW = 18, PANEL_H = 130, INV_AREA = 97;

    public DispatchMenu(int id, Inventory inv, MachineControllerBlockEntity be) {
        this(id, inv, be, 3);
    }

    public DispatchMenu(int id, Inventory inv, MachineControllerBlockEntity be, int gridRows) {
        super(Menus.DISPATCH.get(), id);
        this.be = be;
        this.inv = inv;
        this.pos = be.getBlockPos();
        this.gridRows = gridRows;
        var def = com.endlessepoch.core.api.multiblock.MachineRegistry.get(be.getMachineId());
        this.nameEn = def.map(com.endlessepoch.core.api.multiblock.MachineDefinition::getNameEn).orElse("Dispatch Center");
        this.nameZh = def.map(com.endlessepoch.core.api.multiblock.MachineDefinition::getNameZh).orElse("调度中心");
        addPlayerSlots(inv, imgH(gridRows));
        addDataSlots(formedData);
        formedData.set(0, be.isFormed() ? 1 : 0);
    }

    public DispatchMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.DISPATCH.get(), id);
        this.be = null;
        this.inv = inv;
        this.pos = buf.readBlockPos();
        this.gridRows = buf.readVarInt();
        this.nameEn = buf.readUtf();
        this.nameZh = buf.readUtf();
        addPlayerSlots(inv, imgH(gridRows));
        addDataSlots(formedData);
    }

    /** Formed state — synced to client via ContainerData / 成形状态——经 ContainerData 同步到客户端 */
    public boolean isFormed() { return formedData.get(0) != 0; }

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

    private long lastGridHash = -1;

    /** Detect grid storage change via hash — snapshot sent only on change / 网格存储变化检测：对比缓存 hash，变化才发快照 */
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (be != null) {
            int f = be.isFormed() ? 1 : 0;
            if (f != formedData.get(0))
            formedData.set(0, f);
        }
        if (!(be instanceof com.endlessepoch.core.nova.block.DispatchCenterBlockEntity dc)) return;
        if (!(inv.player instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        var node = dc.getActionableNode();
        if (node == null || !node.isActive() || node.getGrid() == null) return;
        var counter = node.getGrid().getStorageService().getCachedInventory();
        long h = 0;
        for (var e : counter) h = h * 31 + (e.getKey().hashCode() ^ Long.hashCode(e.getLongValue()));
        if (h == lastGridHash) return;
        lastGridHash = h;
        var items = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
        for (var e : counter) {
            if (e.getKey() instanceof appeng.api.stacks.AEItemKey itemKey) {
                var stack = itemKey.wrapForDisplayOrFilter();
                stack.setCount((int) Math.min(e.getLongValue(), 64));
                items.add(stack);
            }
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                new com.endlessepoch.core.network.GridStorageUpdatePacket(items));
    }

    @Override public ItemStack quickMoveStack(Player p, int idx) {
        Slot slot = slots.get(idx);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack src = slot.getItem(), s = src.copy();
        if (idx < 27) {
            if (!moveItemStackTo(src, 27, 36, false)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(src, 0, 27, false)) return ItemStack.EMPTY;
        }
        if (src.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return s;
    }
    @Override public boolean stillValid(Player p) { return true; }
}
