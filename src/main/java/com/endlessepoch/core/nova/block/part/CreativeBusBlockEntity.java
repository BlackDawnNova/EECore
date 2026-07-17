package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.PartType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Creative bus — dual mode by suffix (AE2-creative-cell style, for pipeline testing):
 * input variant holds 16 phantom templates, each presenting an endless full stack
 * (extraction never depletes, insertion rejected); output variant is a void sink that
 * accepts and destroys everything. Templates are set via GUI clicks or JEI ghost drag.
 * 创造总线——按后缀双模（类似 AE2 创造元件，管线测试用）：
 * 输入型 16 个幻影模板槽，对外呈现取之不竭的整叠（取出不减、拒绝塞入）；
 * 输出型为虚空槽，无限吞噬一切产物。模板经 GUI 点击或 JEI 拖拽设置。
 */
public class CreativeBusBlockEntity extends InputBusBlockEntity implements VoidStats {

    /** Max per-slot template count. / 单槽模板数量上限。 */
    public static final int MAX_TEMPLATE_COUNT = 1_000_000;
    private static final int DEFAULT_TEMPLATE_COUNT = 64;

    /** Swallowed item counts (output variant), registry id → count. / 吞噬物品计数（输出型）。 */
    private final java.util.LinkedHashMap<Integer, Long> voided = new java.util.LinkedHashMap<>();
    /** Swallowed fluid mB (output assembly), registry id → mB. / 吞噬流体计量（输出总成）。 */
    private final java.util.LinkedHashMap<Integer, Long> voidedFluids = new java.util.LinkedHashMap<>();
    /**
     * Per-slot presented count — how many units one batch snapshot sees. Stored apart
     * from the stacks: vanilla ItemStack NBT codec clamps count to 1..99.
     * 每槽对外呈现的数量——一次批快照能看到多少单元。与物品堆分开存储：
     * 原版 ItemStack NBT 编解码器把 count 钳在 1..99。
     */
    private int[] templateCounts;

    public CreativeBusBlockEntity(BlockPos pos, BlockState state, PartType type, int tier, int slotCount) {
        super(pos, state, type, tier, slotCount);
        this.templateCounts = new int[Math.max(1, slotCount)];
        java.util.Arrays.fill(templateCounts, DEFAULT_TEMPLATE_COUNT);
    }

    @Override
    public boolean isCreative() { return true; }

    /**
     * Assemblies carry fluid tanks too — creative variants get template/void tanks.
     * 总成也带流体罐——创造变体获得模板/虚空罐。
     */
    @Override
    protected net.neoforged.neoforge.fluids.capability.templates.FluidTank createFluidTank(int capacity, boolean output) {
        return output
                ? CreativeFluidTanks.voidSink(capacity, this::recordVoidedFluid)
                : CreativeFluidTanks.template(capacity, () -> { setChanged(); wakeController(); });
    }

    /**
     * Set or clear a tank's fluid template (input assembly) — never consumes the container.
     * 设置或清除罐位流体模板（输入总成）——不消耗容器。
     */
    public void setFluidTemplate(int tankIdx, net.minecraft.world.level.material.Fluid fluid) {
        if (isOutput()) return;
        var tanks = getFluidTanks();
        if (tankIdx < 0 || tankIdx >= tanks.size()) return;
        tanks.get(tankIdx).setFluid(fluid == null
                ? net.neoforged.neoforge.fluids.FluidStack.EMPTY
                : new net.neoforged.neoforge.fluids.FluidStack(fluid, 1));
        setChanged();
        wakeController();
    }

    /** Wake the machine — template changes may start work. / 模板变更可能开工，唤醒机器。 */
    private void wakeController() {
        if (level != null && !level.isClientSide() && getControllerPos() != null
                && level.getBlockEntity(getControllerPos())
                        instanceof com.endlessepoch.core.nova.block.MachineControllerBlockEntity mc)
            mc.publishProcessEvent();
    }

    // VoidStats / 虚空统计

    @Override
    public java.util.List<Entry> voidEntries() {
        var out = new java.util.ArrayList<Entry>();
        synchronized (voided) {
            for (var e : voided.entrySet()) out.add(new Entry(e.getKey(), e.getValue(), false));
        }
        synchronized (voidedFluids) {
            for (var e : voidedFluids.entrySet()) out.add(new Entry(e.getKey(), e.getValue(), true));
        }
        return out;
    }

    @Override
    public void clearVoidStats() {
        synchronized (voided) { voided.clear(); }
        synchronized (voidedFluids) { voidedFluids.clear(); }
        setChanged();
    }

    private void recordVoided(ItemStack stack) {
        int id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(stack.getItem());
        synchronized (voided) {
            if (!voided.containsKey(id) && voided.size() >= MAX_TRACKED) return; // cap types / 种类封顶
            voided.merge(id, (long) stack.getCount(), Long::sum);
        }
        setChanged();
    }

    private void recordVoidedFluid(net.neoforged.neoforge.fluids.FluidStack stack) {
        int id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(stack.getFluid());
        synchronized (voidedFluids) {
            if (!voidedFluids.containsKey(id) && voidedFluids.size() >= MAX_TRACKED) return;
            voidedFluids.merge(id, (long) stack.getAmount(), Long::sum);
        }
        setChanged();
    }

    /**
     * Creative buses expose their raw handler — the infinite source is meant to be
     * pipe-extractable, the void sink pipe-insertable; directionality is built in.
     * 创造总线直接暴露原句柄——无限源本就允许管道抽取、虚空本就允许管道塞入，
     * 方向语义已内建。
     */
    @Override
    public net.neoforged.neoforge.items.IItemHandler getAutomationHandler() {
        return getInventory();
    }

    @Override
    protected ItemStackHandler createInventory(int slotCount) {
        if (getAbilities().contains(com.endlessepoch.core.api.multiblock.PartAbility.ITEM_OUTPUT))
            return voidSink(slotCount);
        return infiniteSource(slotCount);
    }

    /** Backing stacks hold count-1 templates; display stays count-1 (no overflowing
     *  number in the slot) — batch logic reads getTemplateCount() instead.
     *  底层与显示都是 1 个模板（槽位不再画溢出的大数字）——批逻辑改读 getTemplateCount()。 */
    private ItemStackHandler infiniteSource(int slotCount) {
        return new ItemStackHandler(slotCount) {
            @Override public synchronized ItemStack getStackInSlot(int slot) {
                var t = super.getStackInSlot(slot);
                return t.isEmpty() ? ItemStack.EMPTY : t.copyWithCount(1);
            }
            @Override public synchronized ItemStack extractItem(int slot, int amount, boolean simulate) {
                var t = super.getStackInSlot(slot);
                if (t.isEmpty() || amount <= 0) return ItemStack.EMPTY;
                return t.copyWithCount(Math.min(amount, t.getMaxStackSize())); // never depletes / 永不减少
            }
            @Override public synchronized ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack; // configure via GUI/JEI only / 仅可通过 GUI/JEI 配置
            }
            @Override protected void onContentsChanged(int slot) { notifySlotChanged(getStackInSlot(slot)); }
        };
    }

    private int templateCount(int slot) {
        if (templateCounts == null || slot < 0 || slot >= templateCounts.length) return DEFAULT_TEMPLATE_COUNT;
        return templateCounts[slot];
    }

    /** Per-slot presented count. / 该槽对外呈现数量。 */
    public int getTemplateCount(int slot) { return templateCount(slot); }

    /** Set per-slot count (clamped [1, 1,000,000]) and wake the machine. / 设槽数量（钳位）并唤醒机器。 */
    public void setTemplateCount(int slot, int count) {
        if (isOutput() || templateCounts == null || slot < 0 || slot >= templateCounts.length) return;
        templateCounts[slot] = Math.max(1, Math.min(count, MAX_TEMPLATE_COUNT));
        setChanged();
        wakeController();
    }

    /** Accepts and destroys everything; slots always read empty. / 无限吞噬，槽位恒为空。 */
    private ItemStackHandler voidSink(int slotCount) {
        return new ItemStackHandler(slotCount) {
            @Override public synchronized ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
            @Override public synchronized ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
            @Override public synchronized ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!simulate && !stack.isEmpty()) recordVoided(stack); // tally / 记账
                return ItemStack.EMPTY; // fully accepted (voided) / 全部吞掉
            }
        };
    }

    /** Set or clear a phantom template — clearing also resets the count. / 设置或清除幻影模板——清除同时重置数量。 */
    public void setTemplate(int slot, ItemStack stack) {
        if (isOutput() || slot < 0 || slot >= getSlotCount()) return;
        if (stack.isEmpty() && templateCounts != null && slot < templateCounts.length)
            templateCounts[slot] = DEFAULT_TEMPLATE_COUNT; // clear resets count / 清除即重置数量
        ((ItemStackHandler) getInventory()).setStackInSlot(slot,
                stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    // MenuProvider — void variant opens the stats GUI / 虚空型打开统计 GUI

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int id, net.minecraft.world.entity.player.Inventory inv, net.minecraft.world.entity.player.Player player) {
        if (isOutput()) return new com.endlessepoch.core.menu.creative.CreativeVoidMenu(id, inv, this);
        return super.createMenu(id, inv, player);
    }

    // NBT / 持久化

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        saveCounts(tag, "voided", voided);
        saveCounts(tag, "voidedFluids", voidedFluids);
        if (templateCounts != null) tag.putIntArray("templateCounts", templateCounts);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        loadCounts(tag, "voided", voided);
        loadCounts(tag, "voidedFluids", voidedFluids);
        if (tag.contains("templateCounts") && templateCounts != null) {
            int[] saved = tag.getIntArray("templateCounts");
            for (int i = 0; i < templateCounts.length; i++)
                templateCounts[i] = i < saved.length
                        ? Math.max(1, Math.min(saved[i], MAX_TEMPLATE_COUNT)) : 64;
        }
    }

    private static void saveCounts(net.minecraft.nbt.CompoundTag tag, String key,
                                   java.util.LinkedHashMap<Integer, Long> map) {
        synchronized (map) {
            if (map.isEmpty()) return;
            var list = new net.minecraft.nbt.ListTag();
            for (var e : map.entrySet()) {
                var t = new net.minecraft.nbt.CompoundTag();
                t.putInt("id", e.getKey());
                t.putLong("n", e.getValue());
                list.add(t);
            }
            tag.put(key, list);
        }
    }

    private static void loadCounts(net.minecraft.nbt.CompoundTag tag, String key,
                                   java.util.LinkedHashMap<Integer, Long> map) {
        if (!tag.contains(key)) return;
        var list = tag.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND);
        synchronized (map) {
            map.clear();
            for (int i = 0; i < list.size() && i < MAX_TRACKED; i++) {
                var t = list.getCompound(i);
                map.put(t.getInt("id"), t.getLong("n"));
            }
        }
    }
}
