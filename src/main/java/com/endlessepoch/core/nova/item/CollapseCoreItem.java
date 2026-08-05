package com.endlessepoch.core.nova.item;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Collapse core — the standard drop for ANY broken block with readable inventory:
 * all contents (items + fluids) collapse into ONE core (per-stack drops would lose
 * oversized counts and spawn huge entity counts). A core is inert outside a dispatch
 * center: no right-click open, no unpack. Only depositing it into the dispatch center
 * parses the contents back into the ME network. Fire-immune + glowing so the core
 * can always be recovered and never mistaken for ordinary loot; it stays a plain
 * item inside any backpack/storage mod.
 * <p>
 * 坍缩核——任何带库存方块破坏的标准掉落物：全部内容（物品+流体）坍缩进一个核
 * （逐堆掉落会丢失巨量真实数量并生成海量实体）。核在调度中心之外是死物：无右键
 * 开启、无法解包；只有投入调度中心才把内容解析回 ME 网络。火焰免疫 + 发光描边
 * 保证核总能回收且不会被误认成普通掉落；进入任何背包/存储 mod 后就是普通物品。
 */
public class CollapseCoreItem extends Item {

    /** One slot's worth of packed content: single item (components preserved) or fluid + real long count. / 单槽坍缩内容：单件物品（保留组件）或流体 + 真实 long 数量。 */
    public record Entry(ItemStack item, FluidStack fluid, long amount) {
        public static Entry ofItem(ItemStack item, long amount) { return new Entry(item, FluidStack.EMPTY, amount); }
        public static Entry ofFluid(FluidStack fluid, long amount) { return new Entry(ItemStack.EMPTY, fluid, amount); }
    }

    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_AMOUNT = "amount";

    public CollapseCoreItem(Properties properties) { super(properties); }

    public static boolean isCore(ItemStack stack) { return stack.getItem() instanceof CollapseCoreItem; }

    /** Build a core stack from packed entries; EMPTY when nothing to pack. / 由坍缩条目构建核；无内容返回 EMPTY。 */
    public static ItemStack create(HolderLookup.Provider provider, List<Entry> entries) {
        return create(com.endlessepoch.core.registry.Items.COLLAPSE_CORE.get(), provider, entries, null);
    }

    /** Build with the drop origin recorded — void protection teleports back near it for non-player drops. / 记录掉落来源——虚空防护对非玩家掉落传回来源附近。 */
    public static ItemStack create(HolderLookup.Provider provider, List<Entry> entries, BlockPos origin) {
        return create(com.endlessepoch.core.registry.Items.COLLAPSE_CORE.get(), provider, entries, origin);
    }

    /** AE pattern core — packed AE pattern-provider contents, restorable by right-clicking a provider. / AE 样板坍缩核——AE 样板供应器内容专用，可右键供应器放回。 */
    public static ItemStack createPatternCore(HolderLookup.Provider provider, List<Entry> entries) {
        return create(com.endlessepoch.core.registry.Items.AE_PATTERN_CORE.get(), provider, entries, null);
    }

    /** Build with the drop origin recorded (AE pattern core). / 记录掉落来源构建（AE 样板核）。 */
    public static ItemStack createPatternCore(HolderLookup.Provider provider, List<Entry> entries, BlockPos origin) {
        return create(com.endlessepoch.core.registry.Items.AE_PATTERN_CORE.get(), provider, entries, origin);
    }

    /** Read the recorded drop origin; null when absent. / 读取记录的掉落来源；无则 null。 */
    public static BlockPos getOrigin(ItemStack core) {
        var cd = core.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        var tag = cd.copyTag();
        if (!tag.contains("origin", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) return null;
        int[] a = tag.getIntArray("origin");
        return a.length == 3 ? new BlockPos(a[0], a[1], a[2]) : null;
    }

    /** Clear the drop origin — server-side when a player throws a core: the stale
     *  break record must not drag it back; the NBT sync keeps both sides consistent.
     *  清除掉落来源——玩家扔出核时服务端清除：旧破坏记录不应把它拉回去；
     *  NBT 同步保证双端一致。 */
    public static void clearOrigin(ItemStack core) {
        var cd = core.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return;
        var tag = cd.copyTag();
        if (tag.contains("origin")) {
            tag.remove("origin");
            core.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static ItemStack create(Item coreItem, HolderLookup.Provider provider, List<Entry> entries, BlockPos origin) {
        // Merge same-kind entries (same item + components / same fluid) so a core holds
        // one line per type instead of one per slot; entries with distinct components stay apart.
        // 同类条目合并（同物品+同组件 / 同流体）——每类一行而非每槽一行；组件不同的不合并。
        var merged = new ArrayList<Entry>();
        for (var e : entries) {
            if (e.amount() <= 0) continue;
            int idx = -1;
            for (int i = 0; i < merged.size(); i++) {
                var m = merged.get(i);
                boolean same = !e.item().isEmpty()
                        ? !m.item().isEmpty() && ItemStack.isSameItemSameComponents(m.item(), e.item())
                        : m.item().isEmpty() && m.fluid().isFluidEqual(e.fluid());
                if (same) { idx = i; break; }
            }
            if (idx >= 0) {
                var m = merged.get(idx);
                merged.set(idx, new Entry(m.item(), m.fluid(), m.amount() + e.amount()));
            } else {
                merged.add(e);
            }
        }
        var stack = new ItemStack(coreItem);
        var list = new ListTag();
        for (var e : merged) {
            var t = new CompoundTag();
            if (!e.item().isEmpty())
                t.put("item", e.item().copyWithCount(1).saveOptional(provider));
            else if (!e.fluid().isEmpty())
                t.put("fluid", e.fluid().copyWithAmount(1).save(provider, new CompoundTag()));
            else continue;
            t.putLong(KEY_AMOUNT, e.amount());
            list.add(t);
        }
        if (list.isEmpty()) return ItemStack.EMPTY;
        var tag = new CompoundTag();
        tag.put(KEY_ENTRIES, list);
        if (origin != null) {
            tag.putIntArray("origin", new int[]{origin.getX(), origin.getY(), origin.getZ()});
            com.endlessepoch.core.EECore.LOGGER.info("[PKG] origin written: {}", origin);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static List<Entry> readEntries(ItemStack core, HolderLookup.Provider provider) {
        var out = new ArrayList<Entry>();
        if (!isCore(core) || provider == null) return out;
        var cd = core.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return out;
        var list = cd.copyTag().getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var t = list.getCompound(i);
            var item = ItemStack.parseOptional(provider, t.getCompound("item"));
            if (!item.isEmpty())
                out.add(Entry.ofItem(item, t.getLong(KEY_AMOUNT)));
            else {
                var fluid = FluidStack.parseOptional(provider, t.getCompound("fluid"));
                if (!fluid.isEmpty())
                    out.add(Entry.ofFluid(fluid, t.getLong(KEY_AMOUNT)));
            }
        }
        return out;
    }

    /**
     * Unpack the core into the ME network — insert every entry, re-pack whatever
     * did not fit (network full) into the returned core. EMPTY = fully consumed.
     * 把核内容解析进 ME 网络——逐条插入，放不下的（网络满）重新坍缩进返回的核；全存完返回 EMPTY。
     */
    public static ItemStack unpack(MEStorage storage, ItemStack core, IActionSource src, HolderLookup.Provider provider) {
        if (!isCore(core)) return core;
        var remaining = new ArrayList<Entry>();
        for (var e : readEntries(core, provider)) {
            if (!e.item().isEmpty()) {
                long inserted = storage.insert(AEItemKey.of(e.item()), e.amount(), Actionable.MODULATE, src);
                if (inserted < e.amount())
                    remaining.add(Entry.ofItem(e.item(), e.amount() - inserted));
            } else if (!e.fluid().isEmpty()) {
                long inserted = storage.insert(AEFluidKey.of(e.fluid()), e.amount(), Actionable.MODULATE, src);
                if (inserted < e.amount())
                    remaining.add(Entry.ofFluid(e.fluid().copyWithAmount((int) (e.amount() - inserted)), e.amount() - inserted));
            }
        }
        return remaining.isEmpty() ? ItemStack.EMPTY : create(provider, remaining);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (context.level() == null) return;
        for (var e : readEntries(stack, context.level().registryAccess())) {
            if (!e.item().isEmpty()) {
                tooltip.add(Component.translatable("eecore.collapse.entry",
                        String.format("%,d", e.amount()), e.item().getHoverName()));
                // Append the item's own detail lines — AE pattern items carry their
                // recipe display here, so packed patterns read as real recipes.
                // 追加条目物品自身的细节行——AE 样板物品自带配方显示，打包的样板
                // 因此直接可读为真实配方。
                appendItemDetails(e.item(), context, tooltip);
            } else if (!e.fluid().isEmpty()) {
                tooltip.add(Component.translatable("eecore.collapse.entry",
                        String.format("%,d mB", e.amount()), e.fluid().getHoverName()));
            }
        }
    }

    /**
     * For AE pattern items show only the recipe outputs — one compact line per
     * output instead of the full item tooltip. All reflective: no AE2 dependency.
     * 样板物品只显示配方输出——每个输出一行紧凑行，不追加全量 tooltip。全反射零 AE2 依赖。
     */
    private static void appendItemDetails(ItemStack stack, Item.TooltipContext context, List<Component> tooltip) {
        var level = context.level();
        if (level == null) return;
        try {
            Class<?> helper = Class.forName("appeng.api.crafting.PatternDetailsHelper");
            Object details = helper.getMethod("decodePattern", ItemStack.class, net.minecraft.world.level.Level.class)
                    .invoke(null, stack, level);
            if (details == null) return;
            Object outputs = details.getClass().getMethod("getOutputs").invoke(details);
            if (!(outputs instanceof List<?> list)) return;
            for (Object o : list) {
                Object key = o.getClass().getMethod("what").invoke(o);
                long amount = (Long) o.getClass().getMethod("amount").invoke(o);
                Component name = (Component) key.getClass().getMethod("getDisplayName").invoke(key);
                tooltip.add(Component.literal("  → ")
                        .append(name.copy().append(" × " + amount)));
            }
        } catch (Exception ignored) {}
    }
}
