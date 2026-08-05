package com.endlessepoch.core.nova.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * AE pattern core — the collapse-core variant for AE-family pattern providers
 * (AE2/ExtendedAE/ECO): their packed patterns restore directly by right-clicking
 * any pattern container, no detour through the dispatch center. Depositing into a
 * dispatch center still works (unpack is inherited). Restoration runs from the
 * RightClickBlock event (block-side useItemOn would swallow the click to open the
 * GUI); everything is reflective so the class stays compilable and loadable
 * without AE2. Whatever does not fit stays packed.
 * <p>
 * AE 样板坍缩核——AE 系样板供应器（AE2/ExtendedAE/ECO）的坍缩核变体：打包的样板
 * 可直接右键任意样板容器放回，无需绕调度中心。投调度中心解析仍然可用（继承 unpack）。
 * 放回走 RightClickBlock 事件（方块侧 useItemOn 会吞右键开 GUI）；全反射保证无 AE2
 * 也能编译加载。放不下的留在核内。
 */
public class AePatternCoreItem extends CollapseCoreItem {

    public AePatternCoreItem(Properties properties) { super(properties); }

    /**
     * Restore packed patterns into a pattern container (any AE-family provider).
     * Returns true when at least one entry was placed; the core is consumed or
     * re-packed with the remainder. All reflective — no AE2 compile dependency.
     * 把核内样板放回样板容器（任意 AE 系供应器）。至少放入一条返回 true；
     * 核消耗或回写剩余。全反射——无 AE2 编译依赖。
     */
    public static boolean restorePatterns(Level level, BlockPos pos, Player player, ItemStack stack) {
        if (level.isClientSide()) return false;
        var be = level.getBlockEntity(pos);
        if (be == null) return false;
        com.endlessepoch.core.EECore.LOGGER.info("[AE-CORE] restore @{} be={}", pos, be.getClass().getSimpleName());
        Object inv = null;
        Object itemHandler = null;
        try {
            // Probe order:
            // ① ECO's FD bus: private AppEngInternalInventory "inventory" field — the
            //    raw backing store; writing here bypasses the proxy whose size() does a
            //    full pattern decode that wedges any context.
            // ② public "itemHandler" (IItemHandlerModifiable) — same story, kept as fallback.
            // ③ PatternContainer standard path (AE2 blocks/parts).
            // 探测顺序：① ECO 的私有 AppEngInternalInventory "inventory" 字段——纯底层，
            // 直写绕过代理（代理的 size() 全量解码在任何上下文都会卡）；② public
            // itemHandler 兜底；③ PatternContainer 标准路径（AE2 方块/贴片）。
            try {
                java.lang.reflect.Field f = be.getClass().getDeclaredField("inventory");
                if (f.getType().getName().equals("appeng.util.inv.AppEngInternalInventory")) {
                    f.setAccessible(true);
                    inv = f.get(be);
                }
            } catch (NoSuchFieldException ignored) {}
            if (inv == null) {
                try {
                    itemHandler = be.getClass().getField("itemHandler").get(be);
                } catch (NoSuchFieldException ignored) {}
            }
            if (inv == null && itemHandler == null) {
                Class<?> pc = Class.forName("appeng.helpers.patternprovider.PatternContainer");
                if (pc.isInstance(be)) {
                    inv = pc.getMethod("getTerminalPatternInventory").invoke(be);
                } else {
                    // Cable-mounted part: scan all six faces — the clicked face may not host it. / 贴片式：遍历六面找样板容器（命中面可能不是贴片面）。
                    Class<?> cableBe = Class.forName("appeng.blockentity.networking.CableBusBlockEntity");
                    if (cableBe.isInstance(be)) {
                        Method getPart = cableBe.getMethod("getPart", Direction.class);
                        for (Direction d : Direction.values()) {
                            Object part = getPart.invoke(be, d);
                            if (part != null && pc.isInstance(part)) {
                                inv = pc.getMethod("getTerminalPatternInventory").invoke(part);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        if (itemHandler == null && inv == null) return false;
        try {
            var all = readEntries(stack, level.registryAccess());
            List<Entry> remaining;
            if (itemHandler != null) {
                final Object handler = itemHandler;
                Method getSlots = handler.getClass().getMethod("getSlots");
                Method getStack = handler.getClass().getMethod("getStackInSlot", int.class);
                Method setStack = handler.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
                remaining = placePatterns((Integer) getSlots.invoke(handler),
                        i -> {
                            try { return ((ItemStack) getStack.invoke(handler, i)).isEmpty(); }
                            catch (Exception e) { return false; }
                        },
                        (i, s) -> {
                            try { setStack.invoke(handler, i, s); }
                            catch (Exception ignored) {}
                        }, all);
            } else {
                final Object patternInv = inv;
                Method sizeM = patternInv.getClass().getMethod("size");
                Method getM = patternInv.getClass().getMethod("getStackInSlot", int.class);
                Method setM = patternInv.getClass().getMethod("setItemDirect", int.class, ItemStack.class);
                remaining = placePatterns((Integer) sizeM.invoke(patternInv),
                        i -> {
                            try { return ((ItemStack) getM.invoke(patternInv, i)).isEmpty(); }
                            catch (Exception e) { return false; }
                        },
                        (i, s) -> {
                            try { setM.invoke(patternInv, i, s); }
                            catch (Exception ignored) {}
                        }, all);
            }
            // Compare TOTAL amounts, not entry counts — one 10-stack entry partially
            // placed still leaves one remaining entry, which would falsely read as
            // "nothing placed". / 比较总数量而非条目数——10 张的单一条目放 9 张剩 1 张时
            // 条目数仍是 1，会误判"一条没放"。
            long allAmount = all.stream().mapToLong(Entry::amount).sum();
            long remainingAmount = remaining.stream().mapToLong(Entry::amount).sum();
            com.endlessepoch.core.EECore.LOGGER.info("[AE-CORE] all={}, remaining={}", allAmount, remainingAmount);
            if (remainingAmount == allAmount) return false;
            if (remainingAmount == 0) {
                stack.shrink(1);
            } else {
                var rest = createPatternCore(level.registryAccess(), remaining);
                stack.setCount(1);
                stack.applyComponents(rest.getComponents());
            }
            player.displayClientMessage(Component.translatable("eecore.ae_core.restored"), true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<Entry> placePatterns(int size, java.util.function.IntPredicate isEmptySlot,
                                             java.util.function.BiConsumer<Integer, ItemStack> setSlot,
                                             List<Entry> all) {
        var remaining = new ArrayList<Entry>();
        for (var e : all) {
            long remain = e.amount();
            for (int i = 0; i < size && remain > 0; i++) {
                if (isEmptySlot.test(i)) {
                    setSlot.accept(i, e.item().copyWithCount(1));
                    remain--;
                    com.endlessepoch.core.EECore.LOGGER.info("[AE-CORE] placed 1 into slot {}, remain {}", i, remain);
                }
            }
            if (remain > 0) remaining.add(Entry.ofItem(e.item(), remain));
        }
        return remaining;
    }
}
