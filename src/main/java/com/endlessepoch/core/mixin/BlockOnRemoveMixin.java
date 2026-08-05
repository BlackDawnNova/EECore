package com.endlessepoch.core.mixin;

import com.endlessepoch.core.nova.item.CollapseCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

/**
 * Standard drop for ANY block with readable inventory: BlockState.onRemove is the
 * very first hook of every removal path (player break, explosions, pistons, fluids) —
 * it runs BEFORE block-specific onRemove overrides (vanilla chests spill their
 * contents in their own onRemove), so contents are still intact here. EECore parts
 * are skipped: they pack in their own PartBlock.onRemove. Piston movement
 * (moved=true) is untouched. Any handler quirk falls back to the original drop.
 * <p>
 * 全游戏标准掉落：BlockState.onRemove 是所有移除路径（玩家破坏/爆炸/活塞/液体）
 * 的第一个挂点——它先于方块自身的 onRemove 覆写（原版箱子在自身 onRemove 倒内容），
 * 此刻内容完好。EECore 部件跳过（在 PartBlock.onRemove 自打包）；活塞移动
 * （moved=true）不处理；任何 handler 异常回退原掉落。
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockOnRemoveMixin {

    @Inject(method = "onRemove(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V",
            at = @At("HEAD"))
    private void eecore$packContents(Level level, BlockPos pos, BlockState newState, boolean moved, CallbackInfo ci) {
        if (level.isClientSide() || moved) return;
        // State-only guard FIRST — getBlockEntity during world load can re-enter chunk
        // loading (onRemove runs inside chunk generation). Same guard vanilla uses.
        // 先做纯 state 守卫——加载期调 getBlockEntity 会重入 chunk 加载。与原版同款守卫。
        BlockState state = (BlockState) (Object) this;
        if (!state.hasBlockEntity() || state.is(newState.getBlock())) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;
        if (be instanceof com.endlessepoch.core.nova.block.part.PartBlockEntity) return;
        var entries = new ArrayList<CollapseCoreItem.Entry>();
        try {
            if (be instanceof Container c) {
                // Vanilla containers (chests, hoppers…) / 原版容器（箱子/漏斗等）
                for (int i = 0; i < c.getContainerSize(); i++) {
                    var st = c.getItem(i);
                    if (!st.isEmpty())
                        entries.add(CollapseCoreItem.Entry.ofItem(st.copyWithCount(1), st.getCount()));
                }
                for (int i = 0; i < c.getContainerSize(); i++)
                    c.setItem(i, ItemStack.EMPTY);
            } else {
                // Items / 物品
                var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        var sim = handler.extractItem(i, Integer.MAX_VALUE, true);
                        if (!sim.isEmpty())
                            entries.add(CollapseCoreItem.Entry.ofItem(sim.copyWithCount(1), sim.getCount()));
                    }
                    if (!entries.isEmpty()) {
                        // Real drain. Phantom-source guard: an infinite source returns a
                        // constant amount AND its display never shrinks; a real source
                        // empties or changes one of the two (oversized storages cap the
                        // display at 64, rate-limited ones shrink the display). Only
                        // both-stalled means phantom: stop.
                        // 真实清空。幻影源防护：无限源"返回量恒定 且 槽显示不变"；真实源
                        // 会耗尽或至少改变两者之一（巨量存储显示封顶 64、限速存储显示递减）。
                        // 双停滞才判幻影：停止。
                        for (int i = 0; i < handler.getSlots(); i++) {
                            int lastGot = -1, lastDisplay = -1;
                            while (true) {
                                var got = handler.extractItem(i, Integer.MAX_VALUE, false);
                                if (got.isEmpty()) break;
                                int display = handler.getStackInSlot(i).getCount();
                                if (got.getCount() == lastGot && display == lastDisplay) break;
                                lastGot = got.getCount();
                                lastDisplay = display;
                            }
                        }
                    }
                }
                // Fluids / 流体
                var fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
                if (fluidHandler != null) {
                    int beforeFluids = entries.size();
                    for (int t = 0; t < fluidHandler.getTanks(); t++) {
                        var sim = fluidHandler.drain(Integer.MAX_VALUE, FluidAction.SIMULATE);
                        if (!sim.isEmpty())
                            entries.add(CollapseCoreItem.Entry.ofFluid(sim, sim.getAmount()));
                    }
                    if (entries.size() > beforeFluids) {
                        for (int t = 0; t < fluidHandler.getTanks(); t++) {
                            int lastGot = -1, lastDisplay = -1;
                            while (true) {
                                var got = fluidHandler.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
                                if (got.isEmpty()) break;
                                int display = fluidHandler.getFluidInTank(t).getAmount();
                                if (got.getAmount() == lastGot && display == lastDisplay) break;
                                lastGot = got.getAmount();
                                lastDisplay = display;
                            }
                        }
                    }
                }
                if (handler == null && fluidHandler == null) return;
                if (entries.isEmpty()) return;
            }
            var core = CollapseCoreItem.create(level.registryAccess(), entries, pos);
            if (!core.isEmpty())
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), core);
        } catch (Throwable t) {
            // Any handler quirk → original drop path, contents stay intact / 任何异常 → 回退原掉落，内容原样保留
        }
    }
}
