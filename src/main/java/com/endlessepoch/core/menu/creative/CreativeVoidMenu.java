package com.endlessepoch.core.menu.creative;

import com.endlessepoch.core.nova.block.part.PartBlockEntity;
import com.endlessepoch.core.nova.block.part.VoidStats;
import com.endlessepoch.core.registry.Menus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Menu for creative void parts (item bus / fluid hatch) — no slots; syncs up to
 * {@link #SHOWN} swallowed entries (registry id + count) plus a fluid flag via
 * ContainerData. The clear button is menu button id 70.
 * 创造虚空部件（物品总线/流体仓）菜单——无格子；经 ContainerData 同步至多
 * SHOWN 条吞噬记录（注册ID+数量）与流体标志。清空按钮 id=70。
 */
public class CreativeVoidMenu extends AbstractContainerMenu {

    /** Entries shown in the GUI. / GUI 显示的条目数。 */
    public static final int SHOWN = 8;
    // Layout: [i*3]=registry id, [i*3+1]=count (clamped to int), [i*3+2]=fluid flag
    // 布局：[i*3]=注册ID，[i*3+1]=数量（钳位 int），[i*3+2]=流体标志
    private static final int DATA_SIZE = SHOWN * 3;

    private final PartBlockEntity be;
    private final BlockPos pos;
    private final ContainerData data;

    public <T extends PartBlockEntity & VoidStats> CreativeVoidMenu(int id, Inventory inv, T be) {
        super(Menus.CREATIVE_VOID.get(), id);
        this.be = be;
        this.pos = be.getBlockPos();
        final VoidStats stats = be;
        this.data = new ContainerData() {
            @Override public int get(int index) {
                List<VoidStats.Entry> entries = stats.voidEntries();
                int i = index / 3;
                if (i >= entries.size()) return index % 3 == 0 ? -1 : 0; // -1 = empty row / 空行
                var e = entries.get(i);
                return switch (index % 3) {
                    case 0 -> e.id();
                    case 1 -> (int) Math.min(e.count(), Integer.MAX_VALUE);
                    default -> e.fluid() ? 1 : 0;
                };
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return DATA_SIZE; }
        };
        addDataSlots(data);
        addPlayerSlots(inv);
    }

    public CreativeVoidMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.CREATIVE_VOID.get(), id);
        this.pos = buf.readBlockPos();
        this.be = null;
        var d = new SimpleContainerData(DATA_SIZE);
        for (int i = 0; i < SHOWN; i++) d.set(i * 3, -1); // empty until synced / 同步前置空
        this.data = d;
        addDataSlots(data);
        addPlayerSlots(inv);
    }

    private void addPlayerSlots(Inventory inv) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new net.minecraft.world.inventory.Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            this.addSlot(new net.minecraft.world.inventory.Slot(inv, col, 8 + col * 18, 142));
    }

    /** Registry id of shown entry i, -1 = empty. / 第 i 条注册ID，-1=空。 */
    public int entryId(int i) { return data.get(i * 3); }
    /** Count of shown entry i (items or mB). / 第 i 条数量（物品或 mB）。 */
    public int entryCount(int i) { return data.get(i * 3 + 1); }
    /** Entry i is a fluid. / 第 i 条是流体。 */
    public boolean entryFluid(int i) { return data.get(i * 3 + 2) != 0; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 70 && be instanceof VoidStats stats) { stats.clearVoidStats(); return true; }
        return super.clickMenuButton(player, id);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
