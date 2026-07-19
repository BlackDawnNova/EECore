package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.PartAbility;
import com.endlessepoch.core.api.multiblock.PartType;
import com.endlessepoch.core.menu.BusMenu;
import com.endlessepoch.core.nova.block.MachineControllerBlockEntity;
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
import org.jetbrains.annotations.Nullable;

/**
 * Bus block entity with configurable item inventory, hopper/pipe compatible via IItemHandler.
 * Output buses are extract-only — items cannot be inserted manually.
 * All inventory access is synchronized to support concurrent usage by EB or pipelines.
 * 可配置格数的总线方块实体，通过 IItemHandler 支持漏斗/管道。
 * 输出总线只许取出，不可手动放入。所有库存访问已同步以支持 EB 或管道并发。
 */
public class InputBusBlockEntity extends PartBlockEntity implements MenuProvider {

    private final ItemStackHandler inventory;
    private final boolean output;

    public InputBusBlockEntity(BlockPos pos, BlockState state, PartType type, int tier, int slotCount) {
        super(pos, state, type, tier);
        this.output = getAbilities().contains(PartAbility.ITEM_OUTPUT);
        this.inventory = createInventory(slotCount);
    }

    /** Inventory factory — creative subclass overrides with an infinite handler. / 库存工厂，创造子类覆写为无限句柄。 */
    protected ItemStackHandler createInventory(int slotCount) {
        return new ItemStackHandler(slotCount) {
            @Override protected void onContentsChanged(int slot) { notifySlotChanged(getStackInSlot(slot)); }
            @Override public synchronized ItemStack extractItem(int slot, int amount, boolean simulate) { return super.extractItem(slot, amount, simulate); }
            @Override public synchronized ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return super.insertItem(slot, stack, simulate); }
            @Override public synchronized ItemStack getStackInSlot(int slot) { return super.getStackInSlot(slot); }
        };
    }

    /** setChanged + wake the controller when an input slot gained items. / 标脏并在输入侧有料时唤醒控制器。 */
    protected final void notifySlotChanged(ItemStack stack) {
        setChanged();
        if (!output && level != null && !level.isClientSide() && getControllerPos() != null && !stack.isEmpty()) {
            var be = level.getBlockEntity(getControllerPos());
            if (be instanceof MachineControllerBlockEntity mc) mc.publishProcessEvent();
        }
    }

    public IItemHandler getInventory() { return inventory; }
    public int getSlotCount() { return inventory.getSlots(); }
    public boolean isOutput() { return output; }
    /** Phantom-template infinite bus? / 是否为幻影模板无限总线。 */
    public boolean isCreative() { return false; }
    /** BigInteger-capable storage? / 是否为 BigInteger 容量存储。 */
    public boolean isOversized() { return false; }

    /**
     * Direction-restricted view for pipes/hoppers: input buses are insert-only, output
     * buses extract-only — automation can't siphon a machine's ingredients or stuff its
     * output. The controller and GUI keep full access via getInventory(); the creative
     * subclass returns its raw handler (its semantics are already directional).
     * 管道/漏斗的方向受限视图：输入总线只进不出、输出总线只出不进——自动化抽不走原料、
     * 塞不进产物。控制器与 GUI 经 getInventory() 保持全权；创造子类直接返回原句柄
     * （自身语义已带方向）。
     */
    public IItemHandler getAutomationHandler() {
        if (automationView == null) automationView = new IItemHandler() {
            @Override public int getSlots() { return inventory.getSlots(); }
            @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return output ? stack : inventory.insertItem(slot, stack, simulate);
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return output ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }
            @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return !output && inventory.isItemValid(slot, stack); }
        };
        return automationView;
    }
    private IItemHandler automationView;

    // MenuProvider / 菜单提供

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        var menu = new BusMenu(id, inv, this);
        // Viewer enables server→client fluid diff pushes (JEI drag, pipes) / 供服务端差异推送流体变更
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) menu.setViewer(sp);
        return menu;
    }

    // NBT / 持久化

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put("Inventory", inventory.serializeNBT(provider));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        int configured = inventory.getSlots();
        inventory.deserializeNBT(provider, tag.getCompound("Inventory"));
        // Saved slot count differs (bus size changed between versions) — migrate stacks
        // 存档槽数与注册槽数不一致（版本间总线扩容）——迁移物品到新尺寸
        if (inventory.getSlots() != configured) {
            var kept = new java.util.ArrayList<ItemStack>();
            for (int i = 0; i < inventory.getSlots(); i++) {
                var s = inventory.getStackInSlot(i);
                if (!s.isEmpty()) kept.add(s);
            }
            inventory.setSize(configured);
            for (int i = 0; i < kept.size() && i < configured; i++)
                inventory.setStackInSlot(i, kept.get(i));
        }
    }
}
