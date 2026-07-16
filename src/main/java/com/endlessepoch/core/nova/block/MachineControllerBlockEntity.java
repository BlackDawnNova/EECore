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
    private boolean wasEverFormed;
    private UUID ownerUUID;
    private String ownerName;
    private ResourceLocation machineId;

    private final List<BlockPos> inputBusPos = new ArrayList<>();
    private final List<BlockPos> outputBusPos = new ArrayList<>();
    private final List<BlockPos> energyInputPos = new ArrayList<>();
    private final List<BlockPos> energyOutputPos = new ArrayList<>();
    private final List<BlockPos> fluidInputPos = new ArrayList<>();
    private final List<BlockPos> fluidOutputPos = new ArrayList<>();
    private final List<BlockPos> parallelHatchPos = new ArrayList<>();
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
    private java.util.List<ResourceLocation> supportedTypes = java.util.List.of(
            ResourceLocation.fromNamespaceAndPath("eecore", "furnace"));

    // Energy / 能源
    private final com.endlessepoch.core.api.energy.OmegaStorage energyStorage =
            new com.endlessepoch.core.api.energy.OmegaStorage(10000, 128, 128,
                    com.endlessepoch.core.api.tier.VoltageTier.LV);

    // EB event pipeline / EB事件管线
    private final com.endlessepoch.core.api.energy.eb.Flow ebFlow;

    // Heat tracking / 热量追踪
    private final com.endlessepoch.core.api.energy.eb.HeatComponent heatComponent =
            new com.endlessepoch.core.api.energy.eb.HeatComponent();
    private int currentHeatBoost = 100; // combined speed multiplier ×100 (overclock × heat) / 综合速度倍率×100
    // Event-driven processing / 事件驱动加工
    private long completionTick;
    private long recipeStartedTick;
    private double pendingMaxHeat;
    private volatile boolean bgReady;
    private volatile int bgDuration;
    private volatile long bgEnergyCost; // total Ω for the pending recipe unit / 待启动配方的总能耗
    private volatile int voltageBlockedTier = -1; // lowest requiredTier rejected by voltage gate, -1 = none / 被电压门槛拒绝的最低需求电压，-1=无
    private volatile boolean energyBlocked; // matched recipe but hatches can't pay / 配方已匹配但能源仓付不起
    private volatile java.util.List<net.minecraft.world.item.ItemStack> bgResults;
    private volatile net.minecraft.world.item.ItemStack bgInput;

    public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.MACHINE_CONTROLLER.get(), pos, state);
        this.ebFlow = com.endlessepoch.core.api.energy.eb.Flow.create(
                com.endlessepoch.core.api.energy.eb.Subscriber.machine(this,
                        batch -> {
                            if (!formed || completionTick > 0) return;
                            for (var ev : batch) {
                                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.byId((int)ev.itemId());
                                if (item == net.minecraft.world.item.Items.AIR) continue;
                                var input = new net.minecraft.world.item.ItemStack(item, 1);
                                var recipeInput = new net.minecraft.world.item.crafting.SingleRecipeInput(input);
                                int machineTier = getEffectiveTier();
                                // Deterministic: voltage-eligible match with highest requiredTier wins
                                // 确定性选择：电压达标的匹配中取最高 requiredTier（配方按电压分层）
                                com.endlessepoch.core.api.recipe.MachineRecipe bestMr = null;
                                net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.SingleRecipeInput> vanillaMatch = null;
                                int minRejectedTier = Integer.MAX_VALUE; // voltage-rejected candidates / 被电压拒绝的候选
                                for (var holder : level.getRecipeManager().getAllRecipesFor(
                                        this.<net.minecraft.world.item.crafting.SingleRecipeInput,
                                              net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.SingleRecipeInput>>getRecipeType())) {
                                    var r = holder.value();
                                    if (!r.matches(recipeInput, level)) continue;
                                    if (r instanceof com.endlessepoch.core.api.recipe.MachineRecipe mr) {
                                        // Voltage gate: skip recipes above machine tier / 电压门槛：跳过超出机器电压的配方
                                        if (!com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.canProcess(
                                                machineTier, mr.getRequiredTier().ordinal())) {
                                            minRejectedTier = Math.min(minRejectedTier, mr.getRequiredTier().ordinal());
                                            continue;
                                        }
                                        if (bestMr == null || mr.getRequiredTier().ordinal() > bestMr.getRequiredTier().ordinal())
                                            bestMr = mr;
                                    } else if (vanillaMatch == null) {
                                        vanillaMatch = r;
                                    }
                                }
                                if (bestMr == null && vanillaMatch == null) {
                                    // Item only matched voltage-gated recipes → surface upgrade hint in GUI
                                    // 物品只匹配到被电压拒绝的配方 → GUI 提示升级电压
                                    voltageBlockedTier = minRejectedTier != Integer.MAX_VALUE ? minRejectedTier : -1;
                                    continue;
                                }
                                voltageBlockedTier = -1;
                                java.util.List<net.minecraft.world.item.ItemStack> results;
                                int base;
                                int ocCount = 0;
                                if (bestMr != null) {
                                    // Overclock: speed ×2 / energy ×4 per tier above required
                                    // 超频：每超一级速度×2、能耗×4（总能耗×2）
                                    ocCount = com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.overclockCount(
                                            machineTier, bestMr.getRequiredTier().ordinal(), com.endlessepoch.core.Config.p3MaxOverclock);
                                    results = bestMr.getResults();
                                    base = (int) com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.computeDuration(
                                            bestMr.getProcessingTime(), ocCount);
                                    pendingMaxHeat = bestMr.getMaxHeat();
                                    bgEnergyCost = com.endlessepoch.core.Config.p3EnergyEnabled
                                            ? com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.computeEnergyPerUnit(
                                                    bestMr.getEnergyPerTick(), bestMr.getProcessingTime(), ocCount)
                                            : 0L;
                                } else {
                                    // Vanilla recipes run as ELV: same overclock + default energy cost
                                    // 原版配方按 ELV 电压处理：同样超频 + 默认能耗，杜绝免费加工
                                    int cook = vanillaMatch instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe ac ? ac.getCookingTime() : 200;
                                    ocCount = com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.overclockCount(
                                            machineTier, 0, com.endlessepoch.core.Config.p3MaxOverclock);
                                    results = java.util.List.of(vanillaMatch.assemble(recipeInput, level.registryAccess()));
                                    base = (int) com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.computeDuration(cook, ocCount);
                                    var def = com.endlessepoch.core.api.energy.eb.HeatMapCache.get(currentProfileId);
                                    pendingMaxHeat = def != null ? def.maxHeat() : 10.0;
                                    bgEnergyCost = com.endlessepoch.core.Config.p3EnergyEnabled
                                            ? com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.computeEnergyPerUnit(
                                                    com.endlessepoch.core.Config.p3VanillaEnergyPerTick, cook, ocCount)
                                            : 0L;
                                }
                                int adj = base;
                                double heatFactor = 1.0;
                                if (com.endlessepoch.core.Config.heatEnabled) {
                                    double heat = heatComponent.getHeat(currentProfileId, level.getGameTime());
                                    heatFactor = com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.heatFactor(
                                            heat, pendingMaxHeat, com.endlessepoch.core.Config.heatSpeedBoostMax);
                                    adj = Math.max(1, (int)(base / heatFactor));
                                }
                                // Displayed speed = overclock × heat, as ×100 / 显示倍率 = 超频×热机，×100
                                currentHeatBoost = Math.max(100, (int)Math.round((1L << ocCount) * heatFactor * 100));
                                bgDuration = adj; bgResults = results; bgInput = input; bgReady = true;
                                break;
                            }
                        },
                        () -> {
                            if (!formed || !bgReady || completionTick > 0) return;
                            bgReady = false;
                            // Energy pre-check: never consume input we can't pay for
                            // 能量前置检查：付不起能量就不消耗输入
                            long cost = bgEnergyCost;
                            if (cost > 0 && !consumeEnergy(cost, true)) {
                                energyBlocked = true; // surface "no power" hint in GUI / GUI 提示能量不足
                                return;
                            }
                            energyBlocked = false;
                            boolean ex = false;
                            for (var ip : inputBusPos) {
                                var be = level.getBlockEntity(ip);
                                if (be instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                                    for (int s = 0; s < bus.getInventory().getSlots(); s++) {
                                        if (!bus.getInventory().getStackInSlot(s).isEmpty()
                                                && bus.getInventory().getStackInSlot(s).getItem() == bgInput.getItem()) {
                                            bus.getInventory().extractItem(s, 1, false); ex = true; break;
                                        }
                                    }
                                }
                                if (ex) break;
                            }
                            if (!ex) {
                                return;
                            }
                            if (cost > 0) consumeEnergy(cost, false); // deduct after item committed / 物品消耗后实扣
                            processingInput = bgInput; cachedResults = bgResults;
                            maxProgress = bgDuration;
                            recipeStartedTick = level.getGameTime();
                            completionTick = recipeStartedTick + bgDuration;
                            progress = 1;
                            setChanged();
                        }));
        com.endlessepoch.core.api.energy.eb.EventLifecycleManager.register(
                com.endlessepoch.core.api.energy.eb.HashUtil.hash(pos), ebFlow);
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
        wasEverFormed = false;
        if (level != null && !level.isClientSide()) {
            com.endlessepoch.core.api.energy.eb.EventLifecycleManager.unregister(
                    com.endlessepoch.core.api.energy.eb.HashUtil.hash(worldPosition));
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
    public void setSupportedTypes(java.util.List<ResourceLocation> types) {
        this.supportedTypes = java.util.List.copyOf(types);
        if (!supportedTypes.contains(currentProfileId) && !supportedTypes.isEmpty())
            currentProfileId = supportedTypes.get(0);
        setChanged();
    }

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

    public boolean wasEverFormed() { return wasEverFormed; }

    @Override
    public void onMultiblockFormed() {
        formed = true; wasEverFormed = true; partsScanned = false;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        // Event-driven kick-off / 事件驱动启动：对时 + 扫描部件 + 检测物品
        if (level != null && !level.isClientSide()) {
            scanParts();
            ebFlow.resync(level.getGameTime());
            publishProcessEvent();
        }
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
        parallelHatchPos.clear();
        partsScanned = false;
        completionTick = 0;
        voltageBlockedTier = -1;
        energyBlocked = false;
        heatComponent.reset();
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

    /** Lowest requiredTier ordinal rejected by the voltage gate, -1 = none. / 被电压门槛拒绝的最低需求电压序数，-1=无。 */
    public int getVoltageBlockedTier() { return voltageBlockedTier; }

    /** Matched recipe is waiting for energy. / 配方已匹配但在等能量。 */
    public boolean isEnergyBlocked() { return energyBlocked; }

    /**
     * Energy input hatch received power — retry if we were waiting for energy.
     * Event-driven counterpart of InputBus.onContentsChanged.
     * 能源输入仓收到能量——若正在等电则重试。与 InputBus.onContentsChanged 对应的事件源。
     */
    public void onEnergyReceived() {
        if (formed && energyBlocked) publishProcessEvent();
    }

    /**
     * Effective machine tier: highest energy-input-hatch tier; two or more hatches at that tier
     * boost it +1 (dual-hatch boost, capped at QV). No hatches → controller storage tier.
     * 机器有效电压：能源输入仓最高电压；同级双仓增压+1（封顶QV）；无仓时回退控制器自身电压。
     */
    public int getEffectiveTier() {
        if (level == null || energyInputPos.isEmpty()) return energyStorage.getTier().ordinal();
        int max = -1, countAtMax = 0;
        for (var ep : energyInputPos) {
            if (level.getBlockEntity(ep) instanceof com.endlessepoch.core.nova.block.part.PartBlockEntity pe
                    && pe.getEnergyStorage() != null) {
                int t = pe.getTier();
                if (t > max) { max = t; countAtMax = 1; }
                else if (t == max) countAtMax++;
            }
        }
        if (max < 0) return energyStorage.getTier().ordinal();
        if (countAtMax >= 2)
            max = Math.min(max + 1, com.endlessepoch.core.api.tier.VoltageTier.values().length - 1);
        return max;
    }

    /** Get the machine's energy storage. / 获取机器能源存储。 */
    public com.endlessepoch.core.api.energy.OmegaStorage getEnergyStorage() { return energyStorage; }
    public com.endlessepoch.core.api.energy.eb.HeatComponent getHeatComponent() { return heatComponent; }
    public int getCurrentHeatBoost() { return currentHeatBoost; }

    public void togglePause() { paused = !paused; setChanged(); }

    public java.util.List<ResourceLocation> getSupportedTypes() { return supportedTypes; }

    /** Cycle to previous supported profile. / 切换到上一个支持的机器种类。 */
    public void prevProfile() {
        if (supportedTypes.size() <= 1) return;
        int idx = supportedTypes.indexOf(currentProfileId);
        if (idx < 0) idx = 0;
        idx = (idx - 1 + supportedTypes.size()) % supportedTypes.size();
        currentProfileId = supportedTypes.get(idx);
        setChanged();
    }

    /** Set machine profile by ID (must be in supported list). / 按ID设置机器种类（必须在支持列表内）。 */
    public void selectProfile(ResourceLocation id) {
        if (supportedTypes.contains(id)) {
            currentProfileId = id;
            setChanged();
        }
    }

    /** Cycle to next supported profile. / 切换到下一个支持的机器种类。 */
    public void nextProfile() {
        if (supportedTypes.size() <= 1) return;
        int idx = supportedTypes.indexOf(currentProfileId);
        if (idx < 0) idx = 0;
        idx = (idx + 1) % supportedTypes.size();
        currentProfileId = supportedTypes.get(idx);
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

        if (!formed || machineId == null) {
            return;
        }

        // Unformed machines pay zero heartbeat — onMultiblockFormed resyncs instead
        // 未成型机器零心跳——由 onMultiblockFormed 的 resync 对时
        long tick = level.getGameTime();
        // World load restores formed via NBT without onMultiblockFormed — kick once
        // 读档恢复 formed 但不触发 onMultiblockFormed——补发一次启动
        if (needsKickoff) {
            needsKickoff = false;
            ebFlow.resync(tick);
            publishProcessEvent();
        }
        ebFlow.flush(tick);
        if (completionTick > 0 && tick >= completionTick) completeRecipe(tick);

        if (++breakCheckTick >= 100) {
            breakCheckTick = 0;
            var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(machineId);
            if (pattern.isPresent() && !com.endlessepoch.core.api.multiblock.MultiBlockValidator.validate(
                    level, pattern.get(), worldPosition, getFacing())) {
                onMultiblockBroken();
                com.endlessepoch.core.api.multiblock.MultiBlockFormHandler.notifyBreak(this, worldPosition, level);
                if (wasEverFormed && ownerUUID != null) {
                    var player = level.getPlayerByUUID(ownerUUID);
                    if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                        com.endlessepoch.core.api.multiblock.MultiBlockValidator.validateAndPreview(
                                pattern.get(), machineId, worldPosition, getFacing(), level, sp, true);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <C extends net.minecraft.world.item.crafting.RecipeInput,
             T extends net.minecraft.world.item.crafting.Recipe<C>>
    net.minecraft.world.item.crafting.RecipeType<T> getRecipeType() {
        var prof = com.endlessepoch.core.api.machine.MachineTypeRegistry.get(currentProfileId);
        return (net.minecraft.world.item.crafting.RecipeType<T>)
                prof.map(com.endlessepoch.core.api.machine.MachineType::recipeType)
                        .orElse(net.minecraft.world.item.crafting.RecipeType.SMELTING);
    }

    // ── Event-driven recipe processing / 事件驱动配方处理 ──

    /**
     * Extract Ω across all energy input hatches. Main-thread only.
     * simulate=true checks affordability without deducting.
     * 跨全部能源输入仓扣 Ω，仅主线程调用。simulate=true 只验证不实扣。
     */
    private boolean consumeEnergy(long amount, boolean simulate) {
        if (amount <= 0) return true;
        var remaining = com.endlessepoch.core.api.energy.OmegaValue.of(amount);
        for (var ep : energyInputPos) {
            if (remaining.isZero()) break;
            var be = level.getBlockEntity(ep);
            if (be instanceof com.endlessepoch.core.nova.block.part.PartBlockEntity pe
                    && pe.getEnergyStorage() != null) {
                var extracted = pe.getEnergyStorage().extractEnergy(remaining, simulate);
                remaining = remaining.subtract(extracted);
            }
        }
        return remaining.isZero();
    }

    /**
     * Machine parallel cap = base parallel + Σ parallel hatch bonus (4^(tier+1) each),
     * clamped by maxParallelPerMachine. Used by the batch pipeline (Phase 3 M3).
     * 单机并行上限 = 基础并行 + Σ 并行仓加成（每仓 4^(tier+1)），受配置硬上限钳制。
     */
    public int getParallelCap() {
        long cap = com.endlessepoch.core.Config.p3BaseParallel;
        if (level != null) {
            for (var pp : parallelHatchPos) {
                var be = level.getBlockEntity(pp);
                if (be instanceof com.endlessepoch.core.nova.block.part.PartBlockEntity pe) {
                    int t = Math.min(pe.getTier(), 11); // clamp to QV / 钳位至 QV
                    cap += 1L << (2L * (t + 1)); // 4^(tier+1)
                }
            }
        }
        return (int) Math.min(cap, com.endlessepoch.core.Config.p3MaxParallelPerMachine);
    }

    /** Called by InputBus when items arrive. Publishes EB event. / 物品进总线时发布事件 */
    public void publishProcessEvent() {
        if (level == null || level.isClientSide() || !formed) {
            return;
        }
        if (inputBusPos.isEmpty()) scanParts(); // re-scan if parts unknown / 未扫描则自动扫描
        if (inputBusPos.isEmpty()) {
            return;
        }
        for (var ip : inputBusPos) {
            var be = level.getBlockEntity(ip);
            if (be instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                for (int s = 0; s < bus.getInventory().getSlots(); s++) {
                    var stack = bus.getInventory().getStackInSlot(s);
                    if (stack.isEmpty()) continue;
                    long h = com.endlessepoch.core.api.energy.eb.HashUtil.hash(worldPosition);
                    // Effective-tier voltage, clamped — BigInteger.longValue() truncates above 2^63
                    // 有效电压（钳位）——高电压 BigInteger 直接 longValue 会截断
                    var tv = com.endlessepoch.core.api.tier.VoltageTier.values()[getEffectiveTier()].getMinVoltage();
                    long voltage = tv.bitLength() >= 63 ? Long.MAX_VALUE : tv.longValue();
                    var ev = new com.endlessepoch.core.api.energy.eb.EeEvent(
                            com.endlessepoch.core.api.energy.eb.EeEvent.EventType.ITEM_IN,
                            System.nanoTime(), level.getGameTime(), h,
                            com.endlessepoch.core.api.energy.eb.ItemSnapshotUtil.itemId(stack), 1,
                            com.endlessepoch.core.api.energy.eb.ItemSnapshotUtil.nbtHash(stack),
                            voltage,
                            com.endlessepoch.core.Config.heatEnabled
                                    ? heatComponent.getHeat(currentProfileId, level.getGameTime()) : 0.0);
                    ebFlow.publish(ev);
                    return;
                }
            }
        }
        // No items in any bus — clear stale hints / 总线已空，清除滞留提示
        voltageBlockedTier = -1;
        energyBlocked = false;
    }

    /** Completion tick arrived: output + heat + auto-continue. / 完工: 出货+热量+续投 */
    private void completeRecipe(long tick) {
        for (var result : cachedResults) {
            for (var op : outputBusPos) {
                var be = level.getBlockEntity(op);
                if (be instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                    for (int si = 0; si < bus.getInventory().getSlots(); si++) {
                        if (bus.getInventory().insertItem(si, result.copy(), true).isEmpty()) {
                            bus.getInventory().insertItem(si, result.copy(), false);
                            break;
                        }
                    }
                }
            }
        }
        if (com.endlessepoch.core.Config.heatEnabled && currentProfileId != null)
            heatComponent.bulkHeat(currentProfileId, pendingMaxHeat, recipeStartedTick, tick);
        processingInput = net.minecraft.world.item.ItemStack.EMPTY;
        progress = 0; maxProgress = 0; completionTick = 0; outputBlocked = false;
        setChanged();
        publishProcessEvent();
    }

    private int breakCheckTick;
    private boolean needsKickoff; // re-publish kick-off after world load / 读档后补发启动事件

    private void tryFormation() {
        if (machineId == null) return;
        var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(machineId);
        if (pattern.isPresent() && com.endlessepoch.core.api.multiblock.MultiBlockValidator.validate(
                level, pattern.get(), worldPosition, getFacing())) {
            com.endlessepoch.core.api.multiblock.MultiBlockFormHandler.tryForm(
                    this, pattern.get(), getFacing(), null);
            if (formed) {
                // scanParts + publishProcessEvent are now in onMultiblockFormed()
                if (ownerUUID != null) {
                    var player = level.getPlayerByUUID(ownerUUID);
                    if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                        var clearPkt = new com.endlessepoch.core.network.SyncValidationPacket(machineId,
                                new int[0], new int[0], new int[0], new int[0],
                                0, 0, 0, 0, 0, 0, false);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, clearPkt);
                    }
                }
            }
        }
    }

    private void scanParts() {
        partsScanned = true;
        inputBusPos.clear(); outputBusPos.clear();
        energyInputPos.clear(); energyOutputPos.clear();
        fluidInputPos.clear(); fluidOutputPos.clear();
        parallelHatchPos.clear();
        if (level == null || machineId == null) return;
        var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(machineId);
        if (pattern.isEmpty()) return;
        var pat = pattern.get();
        Direction facing = getFacing();
        for (BlockPos localPos : pat.getNonAirPositions()) {
            int x = localPos.getX(), y = localPos.getY(), z = localPos.getZ();
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
                if (abilities.contains(com.endlessepoch.core.api.multiblock.PartAbility.PARALLEL))
                    parallelHatchPos.add(wp.immutable());
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
        tag.putBoolean("wasEverFormed", wasEverFormed);
        if (ownerUUID != null) tag.putUUID("ownerUUID", ownerUUID);
        if (ownerName != null) tag.putString("ownerName", ownerName);
        if (machineId != null) tag.putString("machineId", machineId.toString());
        tag.putString("profile", currentProfileId.toString());
        net.minecraft.nbt.ListTag st = new net.minecraft.nbt.ListTag();
        for (var t : supportedTypes) st.add(net.minecraft.nbt.StringTag.valueOf(t.toString()));
        tag.put("supportedTypes", st);
        tag.putBoolean("paused", paused);
        tag.putBoolean("outputBlocked", outputBlocked);
        energyStorage.saveToNBT(tag);
        heatComponent.saveToNBT(tag);
        if (!processingInput.isEmpty()) {
            tag.put("procInput", processingInput.saveOptional(provider));
            tag.putInt("progress", progress);
            tag.putInt("maxProgress", maxProgress);
            tag.putLong("completionTick", completionTick);
            tag.putLong("recipeStartedTick", recipeStartedTick);
            tag.putDouble("pendingMaxHeat", pendingMaxHeat);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.hasUUID("nodeId")) nodeId = tag.getUUID("nodeId");
        formed = tag.getBoolean("formed");
        wasEverFormed = tag.getBoolean("wasEverFormed");
        if (tag.hasUUID("ownerUUID")) ownerUUID = tag.getUUID("ownerUUID");
        ownerName = tag.getString("ownerName");
        if (tag.contains("machineId"))
            machineId = ResourceLocation.tryParse(tag.getString("machineId"));
        if (tag.contains("profile"))
            currentProfileId = ResourceLocation.tryParse(tag.getString("profile"));
        if (tag.contains("supportedTypes")) {
            var list = new java.util.ArrayList<ResourceLocation>();
            for (var t : tag.getList("supportedTypes", net.minecraft.nbt.Tag.TAG_STRING))
                list.add(ResourceLocation.tryParse(t.getAsString()));
            supportedTypes = java.util.List.copyOf(list);
        }
        paused = tag.getBoolean("paused");
        outputBlocked = tag.getBoolean("outputBlocked");
        if (formed) needsKickoff = true; // resume idle machines with items in buses / 唤醒总线有料的空闲机器
        energyStorage.loadFromNBT(tag);
        heatComponent.loadFromNBT(tag);
        if (tag.contains("procInput")) {
            processingInput = net.minecraft.world.item.ItemStack.parseOptional(provider, tag.getCompound("procInput"));
            progress = tag.getInt("progress");
            maxProgress = tag.getInt("maxProgress");
            completionTick = tag.getLong("completionTick");
            recipeStartedTick = tag.getLong("recipeStartedTick");
            pendingMaxHeat = tag.getDouble("pendingMaxHeat");
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
