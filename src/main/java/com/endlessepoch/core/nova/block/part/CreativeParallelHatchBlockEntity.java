package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.PartType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Creative parallel hatch — parallel bonus is a free number [16, 16384] typed into the
 * GUI. Confirm saves the value and pushes a wake-up to the controller; the controller
 * reads getParallelBonus() on demand, so no polling is involved.
 * 创造并行仓——并行加成为 GUI 输入的自由数值 [16, 16384]。确定后保存并推送唤醒控制器；
 * 控制器按需读取 getParallelBonus()，无任何轮询。
 */
public class CreativeParallelHatchBlockEntity extends PartBlockEntity {

    public static final int MIN_PARALLEL = 16;
    public static final int MAX_PARALLEL = 16384;

    private int parallelValue = MAX_PARALLEL;

    public CreativeParallelHatchBlockEntity(BlockPos pos, BlockState state, PartType type, int tier) {
        super(pos, state, type, tier);
    }

    public int getParallelValue() { return parallelValue; }

    /** Clamp, save and notify the controller. / 钳位、保存并通知控制器。 */
    public void setParallelValue(int value) {
        int v = Math.max(MIN_PARALLEL, Math.min(value, MAX_PARALLEL));
        if (v == parallelValue) return;
        parallelValue = v;
        setChanged();
        // Push the change — bigger cap may let a paced batch speed up next tick
        // 推送变更——上限变大时批处理下 tick 即提速
        if (level != null && !level.isClientSide() && getControllerPos() != null
                && level.getBlockEntity(getControllerPos())
                        instanceof com.endlessepoch.core.nova.block.MachineControllerBlockEntity mc)
            mc.publishProcessEvent();
    }

    @Override
    public long getParallelBonus() { return parallelValue; }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new com.endlessepoch.core.menu.creative.CreativeParallelMenu(id, inv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("parallelValue", parallelValue);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("parallelValue"))
            parallelValue = Math.max(MIN_PARALLEL, Math.min(tag.getInt("parallelValue"), MAX_PARALLEL));
    }
}
