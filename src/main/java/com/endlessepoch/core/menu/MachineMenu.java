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
    private java.util.List<net.minecraft.resources.ResourceLocation> clientSupported;
    private final ContainerData data;

    public MachineMenu(int id, Inventory inv, MachineControllerBlockEntity mc) {
        super(Menus.MACHINE.get(), id);
        this.mc = mc; this.pos = mc.getBlockPos();
        this.nameEn = ""; this.nameZh = "";
        this.data = new SimpleContainerData(12);
        addDataSlots(data);
        addSlots(inv);
        syncFromBE();
    }

    public MachineMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.MACHINE.get(), id);
        this.mc = null; this.pos = buf.readBlockPos();
        this.nameEn = buf.readUtf(); this.nameZh = buf.readUtf();
        int count = buf.readVarInt();
        var types = new java.util.ArrayList<net.minecraft.resources.ResourceLocation>();
        for (int i = 0; i < count; i++)
            types.add(net.minecraft.resources.ResourceLocation.parse(buf.readUtf()));
        this.clientSupported = java.util.List.copyOf(types);
        this.data = new SimpleContainerData(12);
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
    public boolean hasMultipleProfiles() { return data.get(5) > 1; }
    public int getProfileIndex() { return data.get(6); }
    public boolean isOutputBlocked() { return data.get(7) != 0; }
    /** Heat as permille of maxHeat (0-1000). 0 = cold, 1000 = max heat. / 热量千分比(0-1000) */
    public int getHeatMille() { return data.get(8); }
    /** Speed multiplier ×100. 100 = 1.0x, 150 = 1.5x. / 速度倍率×100 */
    public int getSpeedMultiplier() { return Math.max(100, data.get(9)); }
    /** Voltage-gate rejected tier ordinal, -1 = none. / 电压门槛拒绝的需求电压序数，-1=无 */
    public int getVoltageBlockedTier() { return data.get(10) - 1; }
    /** Matched recipe waiting for energy. / 配方已匹配但在等能量 */
    public boolean isEnergyBlocked() { return data.get(11) != 0; }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (mc != null) syncFromBE();
    }

    public java.util.List<com.endlessepoch.core.api.machine.MachineType> getSupportedProfiles() {
        var ids = mc != null ? mc.getSupportedTypes() : clientSupported;
        if (ids == null) return java.util.List.of();
        return ids.stream()
                .map(id -> com.endlessepoch.core.api.machine.MachineTypeRegistry.get(id))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();
    }

    @Override public boolean clickMenuButton(Player p, int id) {
        if (mc == null) return false;
        if (id == 0) { mc.togglePause(); syncFromBE(); return true; }
        if (id == 1) { mc.retryFormation(); return true; }
        if (id >= 3) {
            var types = getSupportedProfiles();
            int idx = id - 3;
            if (idx >= 0 && idx < types.size()) {
                mc.selectProfile(types.get(idx).id());
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
        var supported = mc.getSupportedTypes();
        data.set(5, supported.size());
        data.set(6, supported.indexOf(mc.getCurrentProfileId()));
        data.set(7, mc.isOutputBlocked() ? 1 : 0);
        // Heat: only update when changed (lazy, not per-tick) / 热量仅在变化时更新
        var hc = mc.getHeatComponent();
        var hConfig = com.endlessepoch.core.api.energy.eb.HeatMapCache.get(mc.getCurrentProfileId());
        if (hConfig != null && com.endlessepoch.core.Config.heatEnabled) {
            double heat = hc.getHeatRaw(mc.getCurrentProfileId());
            int mille = Math.min(1000, (int)(heat / Math.max(1.0, hConfig.maxHeat()) * 1000));
            if (mille != data.get(8)) data.set(8, mille); // only sync on change / 仅变化时同步
        } else {
            if (data.get(8) != 0) data.set(8, 0);
        }
        // getCurrentHeatBoost is already ×100 (overclock × heat combined) / 已是×100（超频×热机综合倍率）
        int boost = mc.getCurrentHeatBoost();
        if (boost != data.get(9)) data.set(9, boost);
        data.set(10, mc.getVoltageBlockedTier() + 1);
        data.set(11, mc.isEnergyBlocked() ? 1 : 0);
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
