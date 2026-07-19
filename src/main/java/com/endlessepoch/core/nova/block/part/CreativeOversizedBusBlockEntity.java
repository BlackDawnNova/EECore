package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.PartType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Output bus with unlimited per-slot count — a {@code storedAmount[]} long array holds
 * the real count while the physical {@code ItemStack} stays a count-1 placeholder;
 * {@code getStackInSlot} reconstructs the stack via {@code copyWithCount}, so extraction
 * works normally.
 * 巨量输出总线——storedAmount[] 长整型数组存真实数量，物理 ItemStack 仅为 count=1 占位，
 * getStackInSlot 用 copyWithCount 重建真实计数，取出逻辑不受影响。
 */
public class CreativeOversizedBusBlockEntity extends InputBusBlockEntity {

    private long[] storedAmount;

    public CreativeOversizedBusBlockEntity(BlockPos pos, BlockState state, PartType type, int tier, int slotCount) {
        super(pos, state, type, tier, slotCount);
        this.storedAmount = new long[Math.max(1, slotCount)];
    }

    @Override public boolean isCreative() { return true; } // 4×4 grid / 4×4 网格
    @Override public boolean isOversized() { return true; }

    /** Real count for tooltip. / 悬浮显示的真实数量。 */
    public long getStoredAmount(int slot) {
        return slot >= 0 && slot < storedAmount.length ? storedAmount[slot] : 0;
    }

    @Override
    protected ItemStackHandler createInventory(int slotCount) {
        return new ItemStackHandler(slotCount) {
            @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
            @Override protected void onContentsChanged(int slot) { setChanged(); }

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
                int take = (int) Math.min(amount,
                        Math.min(storedAmount[slot], Integer.MAX_VALUE));
                var stack = super.getStackInSlot(slot);
                if (stack.isEmpty()) return ItemStack.EMPTY;
                var result = stack.copyWithCount(take);
                if (!simulate) {
                    storedAmount[slot] -= take;
                    if (storedAmount[slot] <= 0) {
                        storedAmount[slot] = 0;
                        super.setStackInSlot(slot, ItemStack.EMPTY);
                    }
                    setChanged();
                }
                return result;
            }

            @Override
            public synchronized ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (stack.isEmpty() || slot >= storedAmount.length) return stack;
                var existing = super.getStackInSlot(slot);
                if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack))
                    return stack;
                long add = (long) stack.getCount() & 0xFFFFFFFFL;
                if (!simulate) {
                    storedAmount[slot] += add;
                    if (existing.isEmpty()) {
                        super.setStackInSlot(slot, stack.copyWithCount(1));
                    }
                    setChanged();
                }
                return ItemStack.EMPTY;
            }

            @Override
            public void setStackInSlot(int slot, ItemStack stack) {
                if (slot < storedAmount.length) {
                    storedAmount[slot] = stack.isEmpty() ? 0
                            : (long) stack.getCount() & 0xFFFFFFFFL;
                }
                super.setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putLongArray("storedAmount", storedAmount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("storedAmount")) {
            long[] arr = tag.getLongArray("storedAmount");
            storedAmount = java.util.Arrays.copyOf(arr, storedAmount.length);
        }
    }
}
