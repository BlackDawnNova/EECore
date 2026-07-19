package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.PartType;
import com.endlessepoch.core.api.part.ILockedSlotBus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Oversized input bus with lockable slots — BigInteger-capable storage where each
 * slot can be locked to a specific item type. The machine batch pipeline only reads
 * from locked slots; unlocked slots serve as manual bypass storage.
 * <p>
 * 巨量锁定输入总线——BigInteger 容量存储，每槽可锁定到特定物品类型。
 * 机器批处理管线仅读锁定槽；未锁定槽作为手动旁路存储。
 */
public class LockedOversizedBusBlockEntity extends InputBusBlockEntity implements ILockedSlotBus {

    private long[] storedAmount;
    private ItemStack[] lockItems;
    private FluidStack[] lockFluids;

    public LockedOversizedBusBlockEntity(BlockPos pos, BlockState state, PartType type, int tier, int slotCount) {
        super(pos, state, type, tier, slotCount);
        this.storedAmount = new long[Math.max(1, slotCount)];
        this.lockItems = new ItemStack[Math.max(1, slotCount)];
        java.util.Arrays.fill(lockItems, ItemStack.EMPTY);
        int fs = state.getBlock() instanceof PartBlock pb ? pb.fluidSlots : 0;
        this.lockFluids = new FluidStack[Math.max(1, fs)];
        for (int i = 0; i < lockFluids.length; i++) lockFluids[i] = FluidStack.EMPTY;
    }

    @Override public boolean isCreative() { return false; }
    @Override public boolean isOversized() { return true; }

    // ── ILockedSlotBus / 锁槽接口 ──

    @Override
    public ItemStack getLockItem(int slot) {
        return slot >= 0 && slot < lockItems.length ? lockItems[slot].copy() : ItemStack.EMPTY;
    }

    @Override
    public boolean isSlotLocked(int slot) {
        if (slot < lockItems.length) return !lockItems[slot].isEmpty(); // item / 物品锁
        int fi = slot - lockItems.length;
        return fi >= 0 && fi < lockFluids.length && !lockFluids[fi].isEmpty(); // fluid / 流体锁
    }

    // ── Fluid auto-lock for assemblies / 总成流体自动锁 ──

    @Override
    protected FluidTank createFluidTank(int capacity, boolean output) {
        return new FluidTank(capacity) {
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
                        lockFluids[idx] = FluidStack.EMPTY;
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

            @Override protected void onContentsChanged() { setChanged(); }
        };
    }

    private int getTankIndex(FluidTank tank) {
        var tanks = getFluidTanks();
        for (int i = 0; i < tanks.size(); i++)
            if (tanks.get(i) == tank) return i;
        return -1;
    }

    @Override
    public long getStoredAmount(int slot) {
        return slot >= 0 && slot < storedAmount.length ? storedAmount[slot] : 0;
    }

    /** Auto-lock the slot if it was previously unlocked. / 空槽首次进料自动锁定。 */
    private void autoLock(int slot, ItemStack stack) {
        if (slot < 0 || slot >= lockItems.length || stack.isEmpty()) return;
        if (lockItems[slot].isEmpty()) {
            lockItems[slot] = stack.copyWithCount(1);
            setChanged();
        }
    }

    /** Auto-unlock when the slot drains to zero. / 槽位耗尽自动解锁。 */
    private void autoUnlock(int slot) {
        if (slot < 0 || slot >= lockItems.length || storedAmount[slot] > 0) return;
        if (!lockItems[slot].isEmpty()) {
            lockItems[slot] = ItemStack.EMPTY;
            setChanged();
        }
    }

    // ── Inventory / 库存 ──

    @Override
    protected ItemStackHandler createInventory(int slotCount) {
        return new ItemStackHandler(slotCount) {
            @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                if (isSlotLocked(slot) && !stack.isEmpty())
                    return ItemStack.isSameItemSameComponents(lockItems[slot], stack);
                return super.isItemValid(slot, stack);
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                var stack = super.getStackInSlot(slot);
                if (!stack.isEmpty() && slot < storedAmount.length && storedAmount[slot] > 0) {
                    int display = storedAmount[slot] > 64 ? 64 : (int) storedAmount[slot];
                    stack = stack.copyWithCount(display);
                }
                return stack;
            }

            @Override
            public synchronized ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (slot >= storedAmount.length || storedAmount[slot] <= 0)
                    return ItemStack.EMPTY;
                int take = (int) Math.min(amount, Math.min(storedAmount[slot], Integer.MAX_VALUE));
                var stack = super.getStackInSlot(slot);
                if (stack.isEmpty()) return ItemStack.EMPTY;
                var result = stack.copyWithCount(take);
                if (!simulate) {
                    storedAmount[slot] -= take;
                    if (storedAmount[slot] <= 0) {
                        storedAmount[slot] = 0;
                        super.setStackInSlot(slot, ItemStack.EMPTY);
                    }
                    autoUnlock(slot);
                    setChanged();
                }
                return result;
            }

            @Override
            public synchronized ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (stack.isEmpty() || slot >= storedAmount.length) return stack;
                if (isSlotLocked(slot) && !ItemStack.isSameItemSameComponents(lockItems[slot], stack))
                    return stack;
                var existing = super.getStackInSlot(slot);
                if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack))
                    return stack;
                long add = (long) stack.getCount() & 0xFFFFFFFFL;
                if (!simulate) {
                    storedAmount[slot] += add;
                    if (existing.isEmpty()) {
                        autoLock(slot, stack);
                        super.setStackInSlot(slot, stack.copyWithCount(1));
                    }
                    setChanged();
                    notifySlotChanged(getStackInSlot(slot));
                }
                return ItemStack.EMPTY;
            }

            @Override
            public void setStackInSlot(int slot, ItemStack stack) {
                if (slot < storedAmount.length)
                    storedAmount[slot] = stack.isEmpty() ? 0 : (long) stack.getCount() & 0xFFFFFFFFL;
                super.setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
            }

            @Override
            protected void onContentsChanged(int slot) {
                notifySlotChanged(getStackInSlot(slot));
            }
        };
    }

    // ── NBT / 持久化 ──

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putLongArray("storedAmount", storedAmount);
        ListTag lockList = new ListTag();
        for (ItemStack item : lockItems)
            lockList.add(item.isEmpty() ? new CompoundTag() : item.saveOptional(provider));
        tag.put("lockItems", lockList);
        // Fluid lock list / 流体锁列表
        ListTag flList = new ListTag();
        for (FluidStack f : lockFluids) {
            CompoundTag t = new CompoundTag();
            if (!f.isEmpty()) f.save(provider, t);
            flList.add(t);
        }
        tag.put("lockFluids", flList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("storedAmount")) {
            long[] arr = tag.getLongArray("storedAmount");
            storedAmount = java.util.Arrays.copyOf(arr, storedAmount.length);
        }
        if (tag.contains("lockItems")) {
            ListTag lockList = tag.getList("lockItems", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < lockItems.length && i < lockList.size(); i++) {
                var t = lockList.getCompound(i);
                lockItems[i] = t.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(provider, t);
            }
        }
        if (tag.contains("lockFluids")) {
            ListTag flList = tag.getList("lockFluids", Tag.TAG_COMPOUND);
            for (int i = 0; i < lockFluids.length && i < flList.size(); i++) {
                var t = flList.getCompound(i);
                lockFluids[i] = t.isEmpty() ? FluidStack.EMPTY : FluidStack.parseOptional(provider, t);
            }
        }
    }
}
