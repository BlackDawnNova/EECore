package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.PartAbility;
import com.endlessepoch.core.api.multiblock.PartType;
import com.endlessepoch.core.menu.BusMenu;
import com.endlessepoch.core.registry.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Bus block entity with configurable item inventory, hopper/pipe compatible via IItemHandler.
 * Output buses are extract-only — items cannot be inserted manually.
 * All inventory access is synchronized to support concurrent usage by EB or pipelines.
 * 可配置格数的总线方块实体，通过 IItemHandler 支持漏斗/管道。
 * 输出总线只许取出，不可手动放入。所有库存访问已同步以支持 EB 或管道并发。
 */
public class InputBusBlockEntity extends PartBlockEntity implements MenuProvider {

    private final ItemStackHandler inventory;
    private final boolean output;

    public InputBusBlockEntity(BlockPos pos, BlockState state, PartType type, int tier, int slotCount) {
        super(pos, state, type, tier);
        this.output = getAbilities().contains(PartAbility.ITEM_OUTPUT);
        this.inventory = new ItemStackHandler(slotCount) {
            @Override protected void onContentsChanged(int slot) { setChanged(); }
            @Override public synchronized ItemStack extractItem(int slot, int amount, boolean simulate) { return super.extractItem(slot, amount, simulate); }
            @Override public synchronized ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return super.insertItem(slot, stack, simulate); }
            @Override public synchronized ItemStack getStackInSlot(int slot) { return super.getStackInSlot(slot); }
        };
    }

    public IItemHandler getInventory() { return inventory; }
    public int getSlotCount() { return inventory.getSlots(); }
    public boolean isOutput() { return output; }

    // MenuProvider / 菜单提供

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new BusMenu(id, inv, this);
    }

    // NBT / 持久化

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put("Inventory", inventory.serializeNBT(provider));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        inventory.deserializeNBT(provider, tag.getCompound("Inventory"));
    }
}
