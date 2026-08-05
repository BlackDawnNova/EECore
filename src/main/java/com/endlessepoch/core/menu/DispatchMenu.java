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

    private final java.util.Map<appeng.api.stacks.AEKey, Long> clientView = new java.util.LinkedHashMap<>();
    private final java.util.Set<appeng.api.stacks.AEKey> dirty = new java.util.LinkedHashSet<>();
    private boolean firstSync = true;
    private boolean prefSent;

    /** Event-driven delta sync: full snapshot once, then only changed keys. / 事件驱动增量同步：首包全量，之后只发变化。 */
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (be != null) {
            int f = be.isFormed() ? 1 : 0;
            if (f != formedData.get(0))
            formedData.set(0, f);
        }
        if (!(be instanceof com.endlessepoch.core.nova.block.DispatchCenterBlockEntity dc)) return;
        if (!(inv.player instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        if (!prefSent) {
            prefSent = true;
            var p = be.getPlayerPref(inv.player.getUUID());
            if (p != null)
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                        new com.endlessepoch.core.network.PrefPacket(p[0], p[1], p[2]));
        }
        var grid = dc.getGrid();
        // getAvailableStacks is a free read — no channels/energy required, so only
        // the grid link matters; inactive (unpowered) networks still display fine.
        // getAvailableStacks 是免费读取，不依赖通道/能量——只要求网格连通即可，
        // 无电（未激活）网络同样可以显示列表，交互扣能留到 Stage 2.5。
        if (grid == null) return;
        var storage = grid.getStorageService().getInventory();
        var cur = new appeng.api.stacks.KeyCounter();
        storage.getAvailableStacks(cur);
        if (firstSync) {
            var all = new java.util.ArrayList<com.endlessepoch.core.network.GridIncrementalUpdatePacket.Entry>();
            for (var e : cur) all.add(new com.endlessepoch.core.network.GridIncrementalUpdatePacket.Entry(e.getKey(), e.getLongValue()));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                    new com.endlessepoch.core.network.GridIncrementalUpdatePacket(all, true));
            firstSync = false;
        } else {
            dirty.clear();
            for (var e : cur)
                if (e.getLongValue() != clientView.getOrDefault(e.getKey(), -1L)) dirty.add(e.getKey());
            for (var k : clientView.keySet())
                if (cur.get(k) == 0) dirty.add(k);
            if (!dirty.isEmpty()) {
                var delta = new java.util.ArrayList<com.endlessepoch.core.network.GridIncrementalUpdatePacket.Entry>();
                for (var k : dirty) delta.add(new com.endlessepoch.core.network.GridIncrementalUpdatePacket.Entry(k, cur.get(k)));
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                        new com.endlessepoch.core.network.GridIncrementalUpdatePacket(delta, false));
            }
        }
        clientView.clear();
        for (var e : cur) clientView.put(e.getKey(), e.getLongValue());
    }

    @Override public ItemStack quickMoveStack(Player p, int idx) {
        Slot slot = slots.get(idx);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        // Shift+click inventory slot → deposit into the ME network / Shift+点击背包物品 → 存入 ME 网络
        if (be instanceof com.endlessepoch.core.nova.block.DispatchCenterBlockEntity dc && dc.isFormed()) {
            var grid = dc.getGrid();
            if (grid != null) {
                var storage = grid.getStorageService().getInventory();
                var stack = slot.getItem();
                if (com.endlessepoch.core.nova.item.CollapseCoreItem.isCore(stack)) {
                    var rem = com.endlessepoch.core.nova.item.CollapseCoreItem.unpack(storage, stack,
                            appeng.api.networking.security.IActionSource.ofPlayer(p), p.level().registryAccess());
                    if (rem.isEmpty()) slot.set(ItemStack.EMPTY);
                    else slot.set(rem);
                    slot.setChanged();
                    return ItemStack.EMPTY;
                }
                long inserted = storage.insert(appeng.api.stacks.AEItemKey.of(stack), stack.getCount(),
                        appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.ofPlayer(p));
                if (inserted > 0) {
                    stack.shrink((int) inserted);
                    if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
                    slot.setChanged();
                    return ItemStack.EMPTY;
                }
            }
        }
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
