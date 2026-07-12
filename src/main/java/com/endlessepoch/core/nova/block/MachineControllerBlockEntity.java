package com.endlessepoch.core.nova.block;

import com.endlessepoch.core.api.multiblock.IMultiBlockController;
import com.endlessepoch.core.api.multiblock.IPart;
import com.endlessepoch.core.menu.MachineMenu;
import com.endlessepoch.core.registry.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Generic machine controller BE. No internal inventory — items stay in part inventories.
 * Controller coordinates recipe processing via scanParts() + getInputBuses()/getOutputBuses().
 * 通用机器控制器 BE，无内部库存——物品留在部件中。控制器通过 scanParts 协调配方。
 */
public class MachineControllerBlockEntity extends BlockEntity implements IMultiBlockController, MenuProvider {

    private UUID nodeId = UUID.randomUUID();
    private boolean formed;
    private UUID ownerUUID;
    private String ownerName;
    private ResourceLocation machineId;

    private final List<BlockPos> inputBusPos = new ArrayList<>();
    private final List<BlockPos> outputBusPos = new ArrayList<>();
    private final List<BlockPos> energyInputPos = new ArrayList<>();
    private final List<BlockPos> energyOutputPos = new ArrayList<>();
    private final List<BlockPos> fluidInputPos = new ArrayList<>();
    private final List<BlockPos> fluidOutputPos = new ArrayList<>();
    private boolean partsScanned;

    private static final Logger LOGGER = LogUtils.getLogger();

    // Recipe processing / 配方处理
    private boolean paused;
    private boolean outputBlocked;
    private int progress, maxProgress;
    private java.util.List<net.minecraft.world.item.ItemStack> cachedResults = java.util.List.of();
    private net.minecraft.world.item.ItemStack processingInput = net.minecraft.world.item.ItemStack.EMPTY;
    private ResourceLocation currentProfileId =
            ResourceLocation.fromNamespaceAndPath("eecore", "furnace");

    // Energy / 能源
    private final com.endlessepoch.core.api.energy.OmegaStorage energyStorage =
            new com.endlessepoch.core.api.energy.OmegaStorage(10000, 128, 128,
                    com.endlessepoch.core.api.tier.VoltageTier.LV);

    public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.MACHINE_CONTROLLER.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide() && machineId != null) {
            com.endlessepoch.core.event.BlockPlaceHandler.registerController(worldPosition, level.dimension());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            com.endlessepoch.core.event.BlockPlaceHandler.unregisterController(worldPosition);
        }
    }

    // IMultiBlockController / 控制器接口

    @Override public UUID getNodeId() { return nodeId; }
    @Override public boolean isFormed() { return formed; }
    @Override public UUID getOwnerUUID() { return ownerUUID; }
    @Override public String getOwnerName() { return ownerName; }

    public ResourceLocation getMachineId() { return machineId; }
    public void setMachineId(ResourceLocation id) { this.machineId = id; setChanged(); }

    /** All input bus positions in the formed structure. / 结构中所有输入总线位置。 */
    public List<BlockPos> getInputBuses() { if (formed && !partsScanned) scanParts(); return Collections.unmodifiableList(inputBusPos); }
    /** All output bus positions in the formed structure. / 结构中所有输出总线位置。 */
    public List<BlockPos> getOutputBuses() { if (formed && !partsScanned) scanParts(); return Collections.unmodifiableList(outputBusPos); }
    /** All energy input positions. / 能源输入位置。 */
    public List<BlockPos> getEnergyInputs() { if (formed && !partsScanned) scanParts(); return Collections.unmodifiableList(energyInputPos); }
    /** All energy output positions. / 能源输出位置。 */
    public List<BlockPos> getEnergyOutputs() { if (formed && !partsScanned) scanParts(); return Collections.unmodifiableList(energyOutputPos); }
    /** All fluid input positions. / 流体输入位置。 */
    public List<BlockPos> getFluidInputs() { if (formed && !partsScanned) scanParts(); return Collections.unmodifiableList(fluidInputPos); }
    /** All fluid output positions. / 流体输出位置。 */
    public List<BlockPos> getFluidOutputs() { if (formed && !partsScanned) scanParts(); return Collections.unmodifiableList(fluidOutputPos); }

    @Override
    public void onMultiblockFormed() {
        formed = true; partsScanned = false;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void onMultiblockBroken() {
        formed = false;
        autoFormCheckTick = 0;
        processingInput = net.minecraft.world.item.ItemStack.EMPTY; progress = 0; maxProgress = 0; outputBlocked = false;
        cachedResults = java.util.List.of();
        inputBusPos.clear(); outputBusPos.clear();
        energyInputPos.clear(); energyOutputPos.clear();
        fluidInputPos.clear(); fluidOutputPos.clear();
        partsScanned = false;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void stampOwner(UUID owner, String name) {
        this.ownerUUID = owner; this.ownerName = name; setChanged();
    }

    public Direction getFacing() {
        if (getBlockState().hasProperty(MachineControllerBlock.FACING))
            return getBlockState().getValue(MachineControllerBlock.FACING);
        return Direction.NORTH;
    }

    // Recipe control / 配方控制

    public boolean isPaused() { return paused; }
    public boolean isOutputBlocked() { return outputBlocked; }
    public boolean hasWork() { return !processingInput.isEmpty(); }
    public int getProgress() { return progress; }
    public int getMaxProgress() { return maxProgress; }
    public int getProcessingItemId() {
        return processingInput.isEmpty() ? 0
                : net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(processingInput.getItem());
    }

    public ResourceLocation getCurrentProfileId() { return currentProfileId; }

    /** Get the machine's energy storage. / 获取机器能源存储。 */
    public com.endlessepoch.core.api.energy.OmegaStorage getEnergyStorage() { return energyStorage; }

    public void togglePause() { paused = !paused; setChanged(); }

    /** Cycle to previous machine profile. / 切换到上一个机器种类。 */
    public void prevProfile() {
        var profiles = com.endlessepoch.core.api.machine.MachineProfileRegistry.getAll();
        if (profiles.isEmpty()) return;
        int idx = profiles.size() - 1;
        for (int i = 0; i < profiles.size(); i++)
            if (profiles.get(i).id().equals(currentProfileId)) { idx = (i - 1 + profiles.size()) % profiles.size(); break; }
        currentProfileId = profiles.get(idx).id();
        setChanged();
    }

    /** Set machine profile by ID. / 按ID设置机器种类。 */
    public void selectProfile(ResourceLocation id) {
        if (com.endlessepoch.core.api.machine.MachineProfileRegistry.get(id).isPresent()) {
            currentProfileId = id;
            setChanged();
        }
    }

    /** Cycle to next machine profile. / 切换到下一个机器种类。 */
    public void nextProfile() {
        var profiles = com.endlessepoch.core.api.machine.MachineProfileRegistry.getAll();
        if (profiles.isEmpty()) return;
        int idx = 0;
        for (int i = 0; i < profiles.size(); i++)
            if (profiles.get(i).id().equals(currentProfileId)) { idx = (i + 1) % profiles.size(); break; }
        currentProfileId = profiles.get(idx).id();
        setChanged();
    }

    public void retryFormation() {
        if (machineId == null) return;
        var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(machineId);
        if (pattern.isPresent() && !com.endlessepoch.core.api.multiblock.MultiBlockValidator.validate(
                level, pattern.get(), worldPosition, getFacing()))
            tryFormation();
    }

    // Tick / 刻

    private int scheduledCheckTick;
    private int autoFormCheckTick;

    /** Schedule a pattern re-check after delayTicks. / 延迟调度成形重检。 */
    public void schedulePatternCheck(int delayTicks) {
        if (level == null || level.isClientSide() || machineId == null) return;
        if (scheduledCheckTick <= 0 || delayTicks < scheduledCheckTick) {
            scheduledCheckTick = delayTicks;
        }
    }

    public void clientTick() {}

    public void serverTick() {
        if (level == null || level.isClientSide()) return;

        if (scheduledCheckTick > 0 && --scheduledCheckTick == 0) {
            tryFormation();
        }

        if (!formed && machineId != null && ++autoFormCheckTick >= 100) {
            autoFormCheckTick = 0;
            tryFormation();
        }

        if (!formed || machineId == null) return;

        if (!partsScanned) scanParts();
        if (!paused) processRecipe();

        if (++breakCheckTick >= 100) {
            breakCheckTick = 0;
            var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(machineId);
            if (pattern.isPresent() && !com.endlessepoch.core.api.multiblock.MultiBlockValidator.validate(
                    level, pattern.get(), worldPosition, getFacing())) {
                onMultiblockBroken();
                com.endlessepoch.core.api.multiblock.MultiBlockFormHandler.notifyBreak(this, worldPosition, level);
            }
        }
    }

    private int debugTick;

    @SuppressWarnings("unchecked")
    private <C extends net.minecraft.world.item.crafting.RecipeInput,
             T extends net.minecraft.world.item.crafting.Recipe<C>>
    net.minecraft.world.item.crafting.RecipeType<T> getRecipeType() {
        var prof = com.endlessepoch.core.api.machine.MachineProfileRegistry.get(currentProfileId);
        return (net.minecraft.world.item.crafting.RecipeType<T>)
                prof.map(com.endlessepoch.core.api.machine.MachineProfile::recipeType)
                        .orElse(net.minecraft.world.item.crafting.RecipeType.SMELTING);
    }

    private void processRecipe() {
        if (inputBusPos.isEmpty() || outputBusPos.isEmpty()) {
            if (++debugTick % 100 == 0)
                LOGGER.debug("[MachineController] in={} out={} formed={} paused={} machineId={}",
                        inputBusPos.size(), outputBusPos.size(), formed, paused, machineId);
            return;
        }
        // Drain energy from input hatches / 从能源仓吸电
        drainEnergy();
        if (!processingInput.isEmpty()) {
            if (++progress >= maxProgress) {
                var recipeType = getRecipeType();
                var recipe = level.getRecipeManager().getRecipeFor(
                        recipeType,
                        new net.minecraft.world.item.crafting.SingleRecipeInput(processingInput), level);
                if (recipe.isPresent()) {
                    for (var result : cachedResults) {
                        boolean placed = false;
                        for (BlockPos op : outputBusPos) {
                            var be = level.getBlockEntity(op);
                            if (be instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                                var inv = bus.getInventory();
                                for (int si = 0; si < inv.getSlots(); si++) {
                                    if (inv.insertItem(si, result.copy(), true).isEmpty()) {
                                        inv.insertItem(si, result.copy(), false);
                                        LOGGER.debug("[MachineController] output: → {}",
                                                result.getHoverName().getString());
                                        placed = true;
                                        break;
                                    }
                                }
                            }
                            if (placed) break;
                        }
                    }
                    processingInput = net.minecraft.world.item.ItemStack.EMPTY;
                    progress = 0; maxProgress = 0;
                    setChanged(); return;
                }
            }
            setChanged(); return;
        }
        for (BlockPos ip : inputBusPos) {
            var be = level.getBlockEntity(ip);
            if (be instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                var inv = bus.getInventory();
                for (int s = 0; s < inv.getSlots(); s++) {
                    var stack = inv.getStackInSlot(s);
                    if (stack.isEmpty()) continue;
                    var recipe = level.getRecipeManager().getRecipeFor(
                            getRecipeType(),
                            new net.minecraft.world.item.crafting.SingleRecipeInput(stack), level);
                    if (recipe.isPresent()) {
                        var val = recipe.get().value();
                        java.util.List<net.minecraft.world.item.ItemStack> needed;
                        if (val instanceof com.endlessepoch.core.api.recipe.MachineRecipe mr) {
                            needed = mr.getResults();
                            maxProgress = mr.getProcessingTime();
                        } else {
                            needed = java.util.List.of(val.assemble(
                                    new net.minecraft.world.item.crafting.SingleRecipeInput(stack),
                                    level.registryAccess()));
                            if (val instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe ac)
                                maxProgress = ac.getCookingTime();
                            else maxProgress = 200;
                        }
                        if (!canFitOutputs(needed)) {
                            outputBlocked = true;
                            continue;
                        }
                        outputBlocked = false;
                        cachedResults = new java.util.ArrayList<>(needed);
                        processingInput = stack.copyWithCount(1);
                        progress = 0;
                        inv.extractItem(s, 1, false);
                        LOGGER.debug("[MachineController] started recipe: {} → {} ({} ticks)",
                                stack.getHoverName().getString(),
                                val.getResultItem(level.registryAccess()).getHoverName().getString(),
                                maxProgress);
                        setChanged(); return;
                    }
                }
            }
        }
    }

    /** Check if all results fit in output slots. / 检查输出槽是否有空间。 */
    private boolean canFitOutputs(java.util.List<net.minecraft.world.item.ItemStack> results) {
        if (results.isEmpty()) return true;
        record SlotState(BlockPos busPos, int slotIdx, net.minecraft.world.item.ItemStack content) {}
        var slots = new java.util.ArrayList<SlotState>();
        for (BlockPos op : outputBusPos) {
            var be = level.getBlockEntity(op);
            if (be instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                var inv = bus.getInventory();
                for (int s = 0; s < inv.getSlots(); s++)
                    slots.add(new SlotState(op, s, inv.getStackInSlot(s).copy()));
            }
        }
        // For each result, find a slot that can accept it (accounting for previous result placements)
        // 每次结果都要找到能放的槽（计入前面已占的）
        var occupied = new boolean[slots.size()];
        for (var item : results) {
            boolean placed = false;
            for (int i = 0; i < slots.size(); i++) {
                if (occupied[i]) continue;
                var ss = slots.get(i);
                // Simulate: does the slot have room for this item?
                var current = ss.content().copy();
                int maxStack = current.isEmpty() ? item.getMaxStackSize()
                        : current.getMaxStackSize();
                if (current.isEmpty() || (current.getItem() == item.getItem()
                        && current.getCount() + item.getCount() <= maxStack
                        && net.minecraft.world.item.ItemStack.isSameItemSameComponents(current, item))) {
                    occupied[i] = true;
                    placed = true;
                    break;
                }
            }
            if (!placed) return false;
        }
        return true;
    }

    private void tryFormation() {
        if (machineId == null) return;
        var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(machineId);
        if (pattern.isPresent() && com.endlessepoch.core.api.multiblock.MultiBlockValidator.validate(
                level, pattern.get(), worldPosition, getFacing())) {
            com.endlessepoch.core.api.multiblock.MultiBlockFormHandler.tryForm(
                    this, pattern.get(), getFacing(), null);
            if (formed) scanParts();
        }
    }

    private int breakCheckTick;

    /** Pull energy from input hatches into internal storage. / 从能源输入仓吸电到内部存储。 */
    private void drainEnergy() {
        if (level == null || level.isClientSide()) return;
        for (BlockPos ep : energyInputPos) {
            var be = level.getBlockEntity(ep);
            if (be instanceof com.endlessepoch.core.nova.block.part.PartBlockEntity pe) {
                var src = pe.getEnergyStorage();
                if (src == null) continue;
                // Drain up to the hatch's max output / 从仓吸电
                var tier = src.getTier();
                var pkt = src.extractPacket(tier, false);
                if (pkt != null && !pkt.isEmpty())
                    energyStorage.receivePacket(pkt, false);
            }
        }
    }

    private void scanParts() {
        partsScanned = true;
        inputBusPos.clear(); outputBusPos.clear();
        energyInputPos.clear(); energyOutputPos.clear();
        fluidInputPos.clear(); fluidOutputPos.clear();
        if (level == null || machineId == null) return;
        var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(machineId);
        if (pattern.isEmpty()) return;
        var pat = pattern.get();
        Direction facing = getFacing();
        for (int y = 0; y < pat.height; y++)
            for (int z = 0; z < pat.depth; z++)
                for (int x = 0; x < pat.width; x++) {
                    if (pat.getChar(x, y, z) == 'A' || pat.getChar(x, y, z) == ' ') continue;
                    int rx = x - pat.controllerX, ry = y - pat.controllerY, rz = z - pat.controllerZ;
                    BlockPos wp = switch (facing) {
                        case NORTH -> worldPosition.offset(rx, ry, rz);
                        case SOUTH -> worldPosition.offset(-rx, ry, -rz);
                        case EAST  -> worldPosition.offset(-rz, ry, rx);
                        case WEST  -> worldPosition.offset(rz, ry, -rx);
                        default    -> worldPosition.offset(rx, ry, rz);
                    };
                    BlockEntity be = level.getBlockEntity(wp);
                    if (be instanceof IPart part && part.isFormed()
                            && machineId.equals(part.getMachineId())) {
                        var abilities = part.getAbilities();
                        if (abilities.contains(com.endlessepoch.core.api.multiblock.PartAbility.ITEM_INPUT))
                            inputBusPos.add(wp.immutable());
                        if (abilities.contains(com.endlessepoch.core.api.multiblock.PartAbility.ITEM_OUTPUT))
                            outputBusPos.add(wp.immutable());
                        if (abilities.contains(com.endlessepoch.core.api.multiblock.PartAbility.ENERGY_INPUT))
                            energyInputPos.add(wp.immutable());
                        if (abilities.contains(com.endlessepoch.core.api.multiblock.PartAbility.ENERGY_OUTPUT))
                            energyOutputPos.add(wp.immutable());
                        if (abilities.contains(com.endlessepoch.core.api.multiblock.PartAbility.FLUID_INPUT))
                            fluidInputPos.add(wp.immutable());
                        if (abilities.contains(com.endlessepoch.core.api.multiblock.PartAbility.FLUID_OUTPUT))
                            fluidOutputPos.add(wp.immutable());
                    }
                }
        setChanged();
    }

    // MenuProvider / 菜单提供

    @Override
    public Component getDisplayName() { return Component.literal(""); }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new MachineMenu(id, inv, this);
    }

    // NBT / 持久化

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putUUID("nodeId", nodeId);
        tag.putBoolean("formed", formed);
        if (ownerUUID != null) tag.putUUID("ownerUUID", ownerUUID);
        if (ownerName != null) tag.putString("ownerName", ownerName);
        if (machineId != null) tag.putString("machineId", machineId.toString());
        tag.putString("profile", currentProfileId.toString());
        tag.putBoolean("paused", paused);
        tag.putBoolean("outputBlocked", outputBlocked);
        energyStorage.saveToNBT(tag);
        if (!processingInput.isEmpty()) {
            tag.put("procInput", processingInput.saveOptional(provider));
            tag.putInt("progress", progress);
            tag.putInt("maxProgress", maxProgress);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.hasUUID("nodeId")) nodeId = tag.getUUID("nodeId");
        formed = tag.getBoolean("formed");
        if (tag.hasUUID("ownerUUID")) ownerUUID = tag.getUUID("ownerUUID");
        ownerName = tag.getString("ownerName");
        if (tag.contains("machineId"))
            machineId = ResourceLocation.tryParse(tag.getString("machineId"));
        if (tag.contains("profile"))
            currentProfileId = ResourceLocation.tryParse(tag.getString("profile"));
        paused = tag.getBoolean("paused");
        outputBlocked = tag.getBoolean("outputBlocked");
        energyStorage.loadFromNBT(tag);
        if (tag.contains("procInput")) {
            processingInput = net.minecraft.world.item.ItemStack.parseOptional(provider, tag.getCompound("procInput"));
            progress = tag.getInt("progress");
            maxProgress = tag.getInt("maxProgress");
        }
    }

    // Network / 网络同步

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }
}
