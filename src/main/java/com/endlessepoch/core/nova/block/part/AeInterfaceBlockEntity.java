package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.PartAbility;
import com.endlessepoch.core.api.multiblock.PartType;
import com.endlessepoch.core.nova.block.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Set;

/**
 * Bridges ME network and EECore machine. Placed alongside InputBus/OutputBus.
 * Internal buffer → InputBus at machine rate, OutputBus → buffer, buffer → ME.
 */
public class AeInterfaceBlockEntity extends PartBlockEntity {

    private final ItemStackHandler buffer;
    private Object aeNode; // IGridNode via adjacent IInWorldGridNodeHost
    private int ticksSinceSync;

    public AeInterfaceBlockEntity(BlockPos pos, BlockState state, PartType type, int tier) {
        super(pos, state, type, tier);
        this.buffer = new ItemStackHandler(36) {
            @Override
            protected void onContentsChanged(int slot) { setChanged(); }
        };
    }

    @Override
    public Set<PartAbility> getAbilities() {
        return Set.of(PartAbility.ITEM_INPUT, PartAbility.ITEM_OUTPUT);
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        if (!isFormed() || getControllerPos() == null) return;

        BlockEntity ctrlBe = level.getBlockEntity(getControllerPos());
        if (!(ctrlBe instanceof MachineControllerBlockEntity ctrl)) return;
        if (ctrl.isPaused()) return;

        for (BlockPos ip : ctrl.getInputBuses()) {
            if (ip.equals(worldPosition)) continue;
            BlockEntity be = level.getBlockEntity(ip);
            if (be instanceof InputBusBlockEntity bus && !bus.isOutput())
                pushFromBufferTo(bus.getInventory());
        }

        for (BlockPos op : ctrl.getOutputBuses()) {
            if (op.equals(worldPosition)) continue;
            BlockEntity be = level.getBlockEntity(op);
            if (be instanceof InputBusBlockEntity bus && bus.isOutput())
                pullToBufferFrom(bus.getInventory());
        }

        if (++ticksSinceSync >= 10) {
            ticksSinceSync = 0;
            syncWithAe2();
        }
    }

    private void pushFromBufferTo(IItemHandler dest) {
        for (int s = 0; s < buffer.getSlots(); s++) {
            ItemStack stack = buffer.getStackInSlot(s);
            if (stack.isEmpty()) continue;
            ItemStack extracted = buffer.extractItem(s, stack.getCount(), true);
            if (extracted.isEmpty()) continue;
            ItemStack leftover = insertInto(dest, extracted.copy());
            int taken = extracted.getCount() - leftover.getCount();
            if (taken > 0) buffer.extractItem(s, taken, false);
        }
    }

    private void pullToBufferFrom(IItemHandler src) {
        for (int s = 0; s < src.getSlots(); s++) {
            ItemStack stack = src.getStackInSlot(s);
            if (stack.isEmpty()) continue;
            ItemStack extracted = src.extractItem(s, stack.getCount(), true);
            if (extracted.isEmpty()) continue;
            ItemStack leftover = insertInto(buffer, extracted.copy());
            int taken = extracted.getCount() - leftover.getCount();
            if (taken > 0) src.extractItem(s, taken, false);
        }
    }

    private static ItemStack insertInto(IItemHandler handler, ItemStack stack) {
        ItemStack remaining = stack;
        for (int s = 0; s < handler.getSlots() && !remaining.isEmpty(); s++)
            remaining = handler.insertItem(s, remaining, false);
        return remaining;
    }

    @SuppressWarnings("unchecked")
    private void syncWithAe2() {
        if (level == null) return;
        if (aeNode == null) {
            for (Direction dir : Direction.values()) {
                BlockPos adjPos = worldPosition.relative(dir);
                BlockEntity adjBe = level.getBlockEntity(adjPos);
                if (adjBe == null) continue;
                try {
                    if (adjBe instanceof appeng.api.networking.IInWorldGridNodeHost host) {
                        var node = host.getGridNode(dir.getOpposite());
                        if (node != null) { this.aeNode = node; break; }
                    }
                } catch (Throwable ignored) {}
            }
        }
        if (aeNode == null) return;

        try {
            var node = (appeng.api.networking.IGridNode) aeNode;
            if (!node.isActive()) return;
            var grid = node.getGrid();
            if (grid == null) return;
            var storage = grid.getStorageService().getInventory();
            if (storage == null) return;
            var source = appeng.api.networking.security.IActionSource.empty();

            for (int s = 0; s < buffer.getSlots(); s++) {
                ItemStack stack = buffer.getStackInSlot(s);
                if (stack.isEmpty()) continue;
                var key = appeng.api.stacks.AEItemKey.of(stack);
                if (key == null) continue;
                long inserted = storage.insert(key, stack.getCount(),
                        appeng.api.config.Actionable.MODULATE, source);
                if (inserted > 0) buffer.extractItem(s, (int) inserted, false);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put("aeBuffer", buffer.serializeNBT(provider));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("aeBuffer"))
            buffer.deserializeNBT(provider, tag.getCompound("aeBuffer"));
    }

    public ItemStackHandler getBuffer() { return buffer; }
}
