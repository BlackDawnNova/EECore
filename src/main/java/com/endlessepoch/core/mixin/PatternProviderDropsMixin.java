package com.endlessepoch.core.mixin;

import com.endlessepoch.core.nova.item.CollapseCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * AE2/ExtendedAE/ECO pattern providers keep their contents in a closed inventory
 * (GenericStackInv / AppEngInternalInventory — no item capability, no vanilla
 * Container), so the standard BlockState.onRemove packing cannot see them. Their
 * drops DO flow through addAdditionalDrops: at RETURN the appended part (patterns
 * + return buffer) is collapsed into ONE core and removed from the list — the
 * block itself loots normally. ExtendedAE's multi-pattern provider inherits this
 * method, and ECO's pattern bus overrides the same signature, so one injection
 * covers all three. String target + @Pseudo: compiles without AE2 and silently
 * skips when AE2 is absent.
 * <p>
 * AE2/ExtendedAE/ECO 样板供应器内容是封闭库存（GenericStackInv /
 * AppEngInternalInventory——无物品能力、非原版 Container），标准坍缩核挂点读不到。
 * 但掉落会流经 addAdditionalDrops：在 RETURN 把本方法新增的部分（样板+发料缓存）
 * 坍缩成 1 个核并从列表移除——方块本体照常掉落。ExtendedAE 多槽版继承此方法、
 * ECO 样板总线覆写同签名方法，一次注入覆盖三家。字符串目标 + @Pseudo：
 * 无 AE2 也能编译，无 AE2 环境静默跳过。
 */
@Pseudo
@Mixin(targets = {
        "appeng.blockentity.crafting.PatternProviderBlockEntity",
        "cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity"
})
public abstract class PatternProviderDropsMixin {

    /** List size before this method appends content — only the appended part is collapsed. / 方法追加内容前的列表大小——只接管本方法新增的部分。 */
    @Unique
    private int eecore$dropListSizeAtStart;

    @Inject(method = "addAdditionalDrops(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Ljava/util/List;)V",
            at = @At("HEAD"))
    private void eecore$recordStart(Level level, BlockPos pos, List<ItemStack> drops, CallbackInfo ci) {
        eecore$dropListSizeAtStart = drops.size();
    }

    @Inject(method = "addAdditionalDrops(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Ljava/util/List;)V",
            at = @At("RETURN"))
    private void eecore$collapseAppended(Level level, BlockPos pos, List<ItemStack> drops, CallbackInfo ci) {
        if (level.isClientSide()) return;
        int start = eecore$dropListSizeAtStart;
        if (drops.size() <= start) return;
        var entries = new ArrayList<CollapseCoreItem.Entry>();
        for (int i = start; i < drops.size(); i++) {
            var st = drops.get(i);
            if (!st.isEmpty())
                entries.add(CollapseCoreItem.Entry.ofItem(st.copyWithCount(1), st.getCount()));
        }
        var core = CollapseCoreItem.createPatternCore(level.registryAccess(), entries, pos);
        if (!core.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), core);
            drops.subList(start, drops.size()).clear();
        }
    }
}
