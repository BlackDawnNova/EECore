package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.PartType;
import com.endlessepoch.core.api.part.ILockedSlotBus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.List;

/**
 * Oversized fluid input bin with auto-locking tanks — uses the same 4×4 BusMenu layout
 * as the item bus, but slots hold fluids instead of items. Each tank locks to the first
 * fluid type that enters and unlocks when drained to zero.
 * <p>
 * 巨量流体输入仓——与物品总线共用 4×4 BusMenu 布局，但槽内存流体。
 * 每罐首次进液自动锁定类型，耗尽自动解锁。
 */
public class LockedOversizedFluidBinBlockEntity extends InputBusBlockEntity implements ILockedSlotBus {

    /** Lock-key fluid per tank, EMPTY = unlocked. / 每罐锁定流体，EMPTY=未锁。 */
    private FluidStack[] lockFluids;

    public LockedOversizedFluidBinBlockEntity(BlockPos pos, BlockState state, PartType type, int tier) {
        super(pos, state, type, tier, 0); // zero item slots / 无物品槽
        int slots = state.getBlock() instanceof PartBlock pb ? pb.fluidSlots : 1;
        this.lockFluids = new FluidStack[Math.max(1, slots)];
        for (int i = 0; i < lockFluids.length; i++)
            lockFluids[i] = FluidStack.EMPTY;
    }

    @Override public boolean isCreative() { return false; }
    @Override public boolean isOversized() { return true; }

    /** Whether the given tank is locked to a fluid type. / 指定罐是否已锁定。 */
    public boolean isTankLocked(int tank) {
        return tank >= 0 && tank < lockFluids.length && !lockFluids[tank].isEmpty();
    }

    /** The lock fluid for a tank, EMPTY if unlocked. / 指定罐的锁定流体。 */
    public FluidStack getLockFluid(int tank) {
        return tank >= 0 && tank < lockFluids.length ? lockFluids[tank].copy() : FluidStack.EMPTY;
    }

    // ── ILockedSlotBus — fluid tank as "slot" / 流体罐当作"槽" ──

    @Override public boolean isSlotLocked(int slot) { return isTankLocked(slot); }

    @Override
    public net.minecraft.world.item.ItemStack getLockItem(int slot) {
        return net.minecraft.world.item.ItemStack.EMPTY; // fluid lock has no item / 流体锁无物品
    }

    @Override
    public long getStoredAmount(int slot) {
        var tanks = getFluidTanks();
        return slot >= 0 && slot < tanks.size() ? tanks.get(slot).getFluidAmount() : 0;
    }

    // ── Auto-locking fluid tank / 自动锁流体罐 ──

    @Override
    protected FluidTank createFluidTank(int capacity, boolean output) {
        return new FluidTank(capacity) {
            @Override protected void onContentsChanged() { setChanged(); }
            @Override
            public int fill(FluidStack resource, FluidAction action) {
                int filled = super.fill(resource, action);
                if (filled > 0 && action.execute() && lockFluids != null) {
                    int idx = getTankIndex(this);
                    if (idx >= 0 && idx < lockFluids.length && lockFluids[idx].isEmpty())
                        lockFluids[idx] = getFluid().copy();
                }
                return filled;
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                FluidStack drained = super.drain(maxDrain, action);
                if (!drained.isEmpty() && action.execute() && lockFluids != null) {
                    int idx = getTankIndex(this);
                    if (idx >= 0 && idx < lockFluids.length && getFluid().isEmpty() && !lockFluids[idx].isEmpty())
                        lockFluids[idx] = FluidStack.EMPTY; // auto-unlock on empty / 排空自动解锁
                }
                return drained;
            }

            @Override
            public boolean isFluidValid(FluidStack stack) {
                if (output) return super.isFluidValid(stack);
                if (lockFluids == null) return true;
                int idx = getTankIndex(this);
                if (idx >= 0 && idx < lockFluids.length && !lockFluids[idx].isEmpty())
                    return lockFluids[idx].getFluid() == stack.getFluid();
                return true;
            }
        };
    }

    private int getTankIndex(FluidTank tank) {
        var tanks = getFluidTanks();
        for (int i = 0; i < tanks.size(); i++)
            if (tanks.get(i) == tank) return i;
        return -1;
    }

    // ── NBT / 持久化 ──

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        ListTag list = new ListTag();
        for (FluidStack f : lockFluids) {
            CompoundTag t = new CompoundTag();
            if (!f.isEmpty()) f.save(provider, t);
            list.add(t);
        }
        tag.put("lockFluids", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("lockFluids")) {
            ListTag list = tag.getList("lockFluids", Tag.TAG_COMPOUND);
            for (int i = 0; i < lockFluids.length && i < list.size(); i++) {
                var t = list.getCompound(i);
                lockFluids[i] = t.isEmpty() ? FluidStack.EMPTY
                        : FluidStack.parseOptional(provider, t);
            }
        }
        // Migrate lock state from existing tank contents / 从已有罐内容迁移锁状态
        var tanks = getFluidTanks();
        for (int i = 0; i < lockFluids.length && i < tanks.size(); i++) {
            if (lockFluids[i].isEmpty() && !tanks.get(i).isEmpty())
                lockFluids[i] = tanks.get(i).getFluid().copy();
        }
    }
}
