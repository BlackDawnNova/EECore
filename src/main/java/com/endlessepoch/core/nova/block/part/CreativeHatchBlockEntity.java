package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.energy.EnergyPacket;
import com.endlessepoch.core.api.energy.OmegaStorage;
import com.endlessepoch.core.api.energy.OmegaValue;
import com.endlessepoch.core.api.multiblock.PartAbility;
import com.endlessepoch.core.api.multiblock.PartType;
import com.endlessepoch.core.api.tier.VoltageTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Creative hatch — dual mode by suffix (for pipeline testing):
 * energy input = infinite Ω source at a GUI-adjustable tier (extract never depletes,
 * receive rejected, drives the machine's effective voltage); energy output = void sink
 * that swallows unlimited Ω and never fills; fluid input = template tank that presents
 * an endless full tank of the configured fluid (fill sets the template, drain never
 * depletes); fluid output = void tank that swallows everything.
 * 创造仓——按后缀双模（管线测试用）：
 * 能源输入=无限 Ω 源，GUI 调档（取之不竭、拒绝充入、决定机器有效电压）；
 * 能源输出=虚空，无限吞 Ω 永不满；流体输入=模板罐，恒满配置流体（灌入即设模板、
 * 抽取不减少）；流体输出=虚空罐，无限吞流体。
 */
public class CreativeHatchBlockEntity extends PartBlockEntity implements VoidStats {

    /** GUI-adjustable voltage tier for the energy-input variant. / 能源输入型的 GUI 可调电压档。 */
    private int creativeTier = 1; // LV default / 默认 LV
    /** GUI-adjustable amperage (1/2/4/8/16). / GUI 可调安培数。 */
    private int creativeAmp = 16;
    private OmegaStorage creativeStorage;
    /** Swallowed fluid mB (fluid-output variant), registry id → mB. / 吞噬流体计量（流体输出型）。 */
    private final java.util.LinkedHashMap<Integer, Long> voidedFluids = new java.util.LinkedHashMap<>();

    public CreativeHatchBlockEntity(BlockPos pos, BlockState state, PartType type, int tier) {
        super(pos, state, type, tier);
        if (isEnergyInput() || getAbilities().contains(PartAbility.ENERGY_OUTPUT)) rebuildStorage();
    }

    public boolean isEnergyInput() { return getAbilities().contains(PartAbility.ENERGY_INPUT); }

    /** Infinite-source fluid template variant? / 是否为无限流体模板型（流体输入）。 */
    public boolean isFluidTemplate() { return getAbilities().contains(PartAbility.FLUID_INPUT); }

    /**
     * Set or clear a tank's fluid template — never consumes the source container.
     * 设置或清除罐位流体模板——不消耗来源容器。
     */
    public void setFluidTemplate(int tankIdx, net.minecraft.world.level.material.Fluid fluid) {
        if (!isFluidTemplate()) return;
        var tanks = getFluidTanks();
        if (tankIdx < 0 || tankIdx >= tanks.size()) return;
        tanks.get(tankIdx).setFluid(fluid == null ? FluidStack.EMPTY : new FluidStack(fluid, 1));
        setChanged();
        wakeController();
    }

    public int getCreativeTier() { return creativeTier; }

    /** Set the source tier (clamped ELV..QV) and rebuild the storage. / 设置源电压档（钳位）并重建储能。 */
    public void setCreativeTier(int tier) {
        int t = Math.max(0, Math.min(tier, VoltageTier.values().length - 1));
        if (t == creativeTier) return;
        creativeTier = t;
        rebuildStorage();
        setChanged();
        wakeController();
    }

    /** Cycle amperage through the legal steps. / 在合法安培档位间循环。 */
    public void cycleAmp(boolean up) {
        int idx = 0;
        for (int i = 0; i < PartBlock.VALID_AMPERAGES.length; i++)
            if (PartBlock.VALID_AMPERAGES[i] == creativeAmp) { idx = i; break; }
        idx = Math.floorMod(idx + (up ? 1 : -1), PartBlock.VALID_AMPERAGES.length);
        creativeAmp = PartBlock.VALID_AMPERAGES[idx];
        rebuildStorage();
        setChanged();
        wakeController();
    }

    /** Amperage follows the GUI selection for the energy source. / 能源源的安培数跟随 GUI 所选值。 */
    @Override
    public int getAmperage() {
        return isEnergyInput() ? creativeAmp : super.getAmperage();
    }

    /** Tier/amp change may unblock a gated machine — wake it. / 调档可能解除门槛，唤醒机器。 */
    private void wakeController() {
        if (level != null && !level.isClientSide() && getControllerPos() != null
                && level.getBlockEntity(getControllerPos())
                        instanceof com.endlessepoch.core.nova.block.MachineControllerBlockEntity mc)
            mc.publishProcessEvent();
    }

    private void rebuildStorage() {
        var tierV = VoltageTier.fromOrdinal(creativeTier);
        long cap = Long.MAX_VALUE / 2;
        if (isEnergyInput()) {
            // Infinite source: always full, extraction never depletes, charging rejected
            // 无限源：恒满、取之不竭、拒绝充入
            creativeStorage = new OmegaStorage(cap, Long.MAX_VALUE, Long.MAX_VALUE, tierV, creativeAmp) {
                @Override public OmegaValue extractEnergy(OmegaValue amount, boolean simulate) { return amount; }
                @Override public OmegaValue getEnergyStored() { return getCapacity(); }
                @Override public EnergyPacket receivePacket(EnergyPacket packet, boolean simulate) { return null; }
            };
        } else {
            // Void sink: swallows everything, never fills / 虚空：全吞永不满
            creativeStorage = new OmegaStorage(cap, Long.MAX_VALUE, Long.MAX_VALUE, tierV, creativeAmp) {
                @Override public EnergyPacket receivePacket(EnergyPacket packet, boolean simulate) { return packet; }
                @Override public OmegaValue getEnergyStored() { return OmegaValue.of(0); }
                @Override public OmegaValue extractEnergy(OmegaValue amount, boolean simulate) { return OmegaValue.of(0); }
            };
        }
    }

    @Override
    public OmegaStorage getEnergyStorage() {
        return creativeStorage != null ? creativeStorage : super.getEnergyStorage();
    }

    /** Effective tier follows the GUI-selected tier for the energy source. / 能源源的有效电压跟随 GUI 所选档。 */
    @Override
    public int getTier() {
        return isEnergyInput() ? creativeTier : super.getTier();
    }

    @Override
    protected FluidTank createFluidTank(int capacity, boolean output) {
        return output
                ? CreativeFluidTanks.voidSink(capacity, this::recordVoidedFluid)
                : CreativeFluidTanks.template(capacity, () -> { setChanged(); wakeController(); });
    }

    // VoidStats — fluid-output variant / 虚空统计（流体输出型）

    /** Void fluid sink variant? / 是否为虚空流体型（流体输出）。 */
    public boolean isFluidVoid() { return getAbilities().contains(PartAbility.FLUID_OUTPUT); }

    private void recordVoidedFluid(FluidStack stack) {
        int id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(stack.getFluid());
        synchronized (voidedFluids) {
            if (!voidedFluids.containsKey(id) && voidedFluids.size() >= MAX_TRACKED) return;
            voidedFluids.merge(id, (long) stack.getAmount(), Long::sum);
        }
        setChanged();
    }

    @Override
    public java.util.List<Entry> voidEntries() {
        synchronized (voidedFluids) {
            var out = new java.util.ArrayList<Entry>(voidedFluids.size());
            for (var e : voidedFluids.entrySet()) out.add(new Entry(e.getKey(), e.getValue(), true));
            return out;
        }
    }

    @Override
    public void clearVoidStats() {
        synchronized (voidedFluids) { voidedFluids.clear(); }
        setChanged();
    }

    // MenuProvider / 菜单提供

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        if (isEnergyInput()) return new com.endlessepoch.core.menu.creative.CreativeHatchMenu(id, inv, this);
        if (isFluidVoid()) return new com.endlessepoch.core.menu.creative.CreativeVoidMenu(id, inv, this);
        return super.createMenu(id, inv, player);
    }

    // NBT / 持久化

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("creativeTier", creativeTier);
        tag.putInt("creativeAmp", creativeAmp);
        synchronized (voidedFluids) {
            if (!voidedFluids.isEmpty()) {
                var list = new net.minecraft.nbt.ListTag();
                for (var e : voidedFluids.entrySet()) {
                    var t = new CompoundTag();
                    t.putInt("id", e.getKey());
                    t.putLong("n", e.getValue());
                    list.add(t);
                }
                tag.put("voidedFluids", list);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("creativeTier"))
            creativeTier = Math.max(0, Math.min(tag.getInt("creativeTier"), VoltageTier.values().length - 1));
        if (tag.contains("creativeAmp")) {
            int a = tag.getInt("creativeAmp");
            creativeAmp = (a == 1 || a == 2 || a == 4 || a == 8 || a == 16) ? a : 16;
        }
        if (creativeStorage != null) rebuildStorage();
        if (tag.contains("voidedFluids")) {
            var list = tag.getList("voidedFluids", net.minecraft.nbt.Tag.TAG_COMPOUND);
            synchronized (voidedFluids) {
                voidedFluids.clear();
                for (int i = 0; i < list.size() && i < MAX_TRACKED; i++) {
                    var t = list.getCompound(i);
                    voidedFluids.put(t.getInt("id"), t.getLong("n"));
                }
            }
        }
    }
}
