package com.endlessepoch.core.nova.block.part;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bus block entity with configurable item inventory, hopper/pipe compatible via IItemHandler.
 * 可配置格数的总线方块实体，通过 IItemHandler 支持漏斗/管道。
 * <p>
 * Addon mods set slot count via {@link PartBlock#PartBlock(Properties, PartType, int)}.
 * 附属 mod 通过 PartBlock 构造器设置格数。
 */
public class InputBusBlockEntity extends PartBlockEntity implements MenuProvider {

    private final ItemStackHandler inventory;

    public InputBusBlockEntity(BlockPos pos, BlockState state, int slotCount) {
        super(pos, state, PartType.INPUT_BUS);
        int clamped = Math.max(1, Math.min(slotCount, PartBlock.MAX_BUS_SLOTS));
        this.inventory = new ItemStackHandler(clamped) {
            @Override
            protected void onContentsChanged(int slot) { setChanged(); }
        };
    }

    public IItemHandler getInventory() { return inventory; }
    public int getSlotCount() { return inventory.getSlots(); }

    // ===== MenuProvider / 菜单提供者 =====

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new BusMenu(id, inv, this);
    }

    // ===== NBT =====

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
