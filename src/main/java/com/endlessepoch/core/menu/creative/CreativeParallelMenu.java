package com.endlessepoch.core.menu.creative;

import com.endlessepoch.core.nova.block.part.CreativeParallelHatchBlockEntity;
import com.endlessepoch.core.registry.Menus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Menu for the creative parallel hatch — syncs the saved parallel value for display;
 * the typed value travels via SetParallelPacket on confirm.
 * 创造并行仓菜单——同步已保存的并行数用于显示；输入值经 SetParallelPacket 在确认时提交。
 */
public class CreativeParallelMenu extends AbstractContainerMenu {

    private final CreativeParallelHatchBlockEntity be;
    private final Level level;
    private final BlockPos pos;
    private int clientValue = CreativeParallelHatchBlockEntity.MAX_PARALLEL;

    public CreativeParallelMenu(int id, Inventory inv, CreativeParallelHatchBlockEntity be) {
        super(Menus.CREATIVE_PARALLEL.get(), id);
        this.be = be;
        this.pos = be.getBlockPos();
        this.level = inv.player.level();
        addValueSlot();
        addPlayerSlots(inv);
    }

    public CreativeParallelMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.CREATIVE_PARALLEL.get(), id);
        this.level = inv.player.level();
        this.pos = buf.readBlockPos();
        this.be = level.getBlockEntity(pos) instanceof CreativeParallelHatchBlockEntity ph ? ph : null;
        addValueSlot();
        addPlayerSlots(inv);
    }

    private void addValueSlot() {
        addDataSlot(new DataSlot() {
            @Override public int get() { return be != null ? be.getParallelValue() : clientValue; }
            @Override public void set(int v) { clientValue = v; }
        });
    }

    private void addPlayerSlots(Inventory inv) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new net.minecraft.world.inventory.Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            this.addSlot(new net.minecraft.world.inventory.Slot(inv, col, 8 + col * 18, 142));
    }

    /** Saved parallel value (synced). / 已保存的并行数（已同步）。 */
    public int savedValue() {
        return level.isClientSide() ? clientValue : (be != null ? be.getParallelValue() : clientValue);
    }

    public BlockPos getPos() { return pos; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        if (be == null) return false;
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
