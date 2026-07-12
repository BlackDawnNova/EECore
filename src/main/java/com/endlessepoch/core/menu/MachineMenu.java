package com.endlessepoch.core.menu;

import com.endlessepoch.core.nova.block.MachineControllerBlockEntity;
import com.endlessepoch.core.registry.Menus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MachineMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final String nameEn, nameZh;
    private final MachineControllerBlockEntity mc;
    private final ContainerData data; // 0=paused 1=hasWork 2=progress 3=maxProgress 4=itemId 5=profileCount 6=profileIdx 7=outputBlocked / 数据槽位布局

    public MachineMenu(int id, Inventory inv, MachineControllerBlockEntity mc) {
        super(Menus.MACHINE.get(), id);
        this.mc = mc; this.pos = mc.getBlockPos();
        this.nameEn = ""; this.nameZh = "";
        this.data = new SimpleContainerData(8);
        addDataSlots(data);
        addSlots(inv);
        syncFromBE();
    }

    public MachineMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.MACHINE.get(), id);
        this.mc = null; this.pos = buf.readBlockPos();
        this.nameEn = buf.readUtf(); this.nameZh = buf.readUtf();
        this.data = new SimpleContainerData(8);
        addDataSlots(data);
        addSlots(inv);
    }

    public String getNameEn() { return nameEn; }
    public String getNameZh() { return nameZh; }
    public boolean isPaused() { return data.get(0) != 0; }
    public boolean hasWork() { return data.get(1) != 0; }
    public int getProgress() { return data.get(2); }
    public int getMaxProgress() { return Math.max(data.get(3), 1); }
    public int getProcessingItemId() { return data.get(4); }
    public int getProfileCount() { return data.get(5); }
    public int getProfileIndex() { return data.get(6); }
    public boolean isOutputBlocked() { return data.get(7) != 0; }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (mc != null) syncFromBE();
    }

    @Override public boolean clickMenuButton(Player p, int id) {
        if (mc == null) return false;
        if (id == 0) { mc.togglePause(); syncFromBE(); return true; }
        if (id == 1) { mc.retryFormation(); return true; }
        if (id >= 3) {
            var profiles = com.endlessepoch.core.api.machine.MachineProfileRegistry.getAll();
            int idx = id - 3;
            if (idx >= 0 && idx < profiles.size()) {
                mc.selectProfile(profiles.get(idx).id());
                syncFromBE();
            }
            return true;
        }
        return false;
    }

    private void syncFromBE() {
        if (mc == null) return;
        data.set(0, mc.isPaused() ? 1 : 0);
        data.set(1, mc.hasWork() ? 1 : 0);
        data.set(2, mc.getProgress());
        data.set(3, mc.getMaxProgress());
        data.set(4, mc.getProcessingItemId());
        var profiles = com.endlessepoch.core.api.machine.MachineProfileRegistry.getAll();
        data.set(5, profiles.size());
        int pIdx = 0;
        var curId = mc.getCurrentProfileId();
        for (int i = 0; i < profiles.size(); i++)
            if (profiles.get(i).id().equals(curId)) { pIdx = i; break; }
        data.set(6, pIdx);
        data.set(7, mc.isOutputBlocked() ? 1 : 0);
    }

    private void addSlots(Inventory inv) {
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 124 + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, 8 + c * 18, 182));
    }

    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p) { return true; }
}
