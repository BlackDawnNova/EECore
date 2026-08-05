package com.endlessepoch.core.mixin;

import com.endlessepoch.core.nova.item.CollapseCoreItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Cable-mounted pattern provider (IPart variant) — same closed-inventory story as
 * the block version, same fix: its content drops flow through IPart.addAdditionalDrops,
 * so at RETURN the appended part (patterns + return buffer) collapses into ONE core
 * and is removed from the list. The part item itself drops via IPart.addPartDrop,
 * untouched. Both AE2's PatternProviderPart and ExtendedAE's independent
 * PartExPatternProvider override the same signature, so one injection covers both.
 * The block entity is fetched via reflection (public inherited method) to stay
 * compilable without AE2; @Pseudo skips silently when AE2 is absent.
 * <p>
 * 贴片式样板供应器（IPart 变体）——与方块版同构：内容掉落流经 IPart.addAdditionalDrops，
 * 在 RETURN 把本方法新增的部分坍缩成 1 个核并移除。贴片本体走 IPart.addPartDrop 不受影响。
 * AE2 的 PatternProviderPart 与 ExtendedAE 独立的 PartExPatternProvider 覆写了同签名方法，
 * 一次注入覆盖两版。BE 经反射获取（public 继承方法），保证无 AE2 也能编译；
 * @Pseudo 无 AE2 环境静默跳过。
 */
@Pseudo
@Mixin(targets = {
        "appeng.parts.crafting.PatternProviderPart",
        "com.glodblock.github.extendedae.common.parts.PartExPatternProvider"
})
public abstract class PatternProviderPartDropsMixin {

    /** List size before this method appends content — only the appended part is collapsed. / 方法追加内容前的列表大小——只接管本方法新增的部分。 */
    @Unique
    private int eecore$dropListSizeAtStart;

    /** IPart.getBlockEntity via reflection — inherited public method, stable across AE2 versions. / 反射取 IPart 的 BE（public 继承方法，跨版本稳定）。 */
    @Unique
    private static BlockEntity eecore$blockEntityOf(Object part) {
        try {
            Method m = part.getClass().getMethod("getBlockEntity");
            return (BlockEntity) m.invoke(part);
        } catch (Exception e) {
            return null;
        }
    }

    @Inject(method = "addAdditionalDrops(Ljava/util/List;Z)V",
            at = @At("HEAD"))
    private void eecore$recordStart(List<ItemStack> drops, boolean includeExtra, CallbackInfo ci) {
        eecore$dropListSizeAtStart = drops.size();
    }

    @Inject(method = "addAdditionalDrops(Ljava/util/List;Z)V",
            at = @At("RETURN"))
    private void eecore$collapseAppended(List<ItemStack> drops, boolean includeExtra, CallbackInfo ci) {
        int start = eecore$dropListSizeAtStart;
        if (drops.size() <= start) return;
        BlockEntity be = eecore$blockEntityOf(this);
        if (be == null || be.getLevel() == null || be.getLevel().isClientSide()) return;
        Level level = be.getLevel();
        var entries = new ArrayList<CollapseCoreItem.Entry>();
        for (int i = start; i < drops.size(); i++) {
            var st = drops.get(i);
            if (!st.isEmpty())
                entries.add(CollapseCoreItem.Entry.ofItem(st.copyWithCount(1), st.getCount()));
        }
        var core = CollapseCoreItem.createPatternCore(level.registryAccess(), entries, be.getBlockPos());
        if (!core.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, be.getBlockPos().getX(), be.getBlockPos().getY(), be.getBlockPos().getZ(), core);
            drops.subList(start, drops.size()).clear();
        }
    }
}
