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
import com.endlessepoch.core.Config;

/** No internal inventory — items stay in part inventories.
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
    private boolean heatEnabled;
    private boolean overclockEnabled = true;
    // Backpressure: debounced state machine replaces raw booleans / 背压：防抖状态机替代裸 boolean
    private final com.endlessepoch.core.api.energy.eb.BackpressureStateMachine backpressure =
            new com.endlessepoch.core.api.energy.eb.BackpressureStateMachine();
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
    // Flow rate / 流速
    final com.endlessepoch.core.api.energy.eb.FlowRateTracker flowTracker = new com.endlessepoch.core.api.energy.eb.FlowRateTracker();
    private int currentHeatBoost = 100; // combined speed multiplier ×100 (overclock × heat) / 综合速度倍率×100
    // Event-driven processing / 事件驱动加工
    private long completionTick;
    private long recipeStartedTick;
    private double pendingMaxHeat;
    private volatile boolean bgReady;
    private volatile int bgDuration;
    private volatile long bgEnergyCost; // total Ω for the pending recipe unit / 待启动配方的总能耗
    private volatile int voltageBlockedTier = -1; // lowest requiredTier rejected by voltage gate, -1 = none / 被电压门槛拒绝的最低需求电压，-1=无
    private volatile java.util.List<net.minecraft.world.item.ItemStack> bgResults;
    private volatile net.minecraft.world.item.ItemStack bgInput;

    // Batch mode (Phase 3) / 批处理模式
    private boolean batchActive;
    private boolean batchDeferred; // prime-offset stagger postponed the start / 被质数偏移错峰推迟，serverTick 续试
    private long kickoffAt;        // staggered world-load kickoff tick, 0=idle / 读档启动散列目标 tick，0=空闲
    private int lastEffParallel;   // live effective parallel for GUI / 供 GUI 显示的当前有效并行
    private double lastSpeedHeat = -1;
    private int lastSpeedOcMul = -1; // oc part of the displayed multiplier — recalc when the plan's oc changes / 显示倍率的超频部分——计划超频变化时重算
    private double batchMaxHeat = 10.0;
    // Phase 4: unified plan version — replaces tier/oc/energy/lock-hash fields
    private final java.util.concurrent.atomic.AtomicLong planVersion = new java.util.concurrent.atomic.AtomicLong(0);
    private long batchSnapshotVersion = -1;
    int batchCompletions;
    public static int globalBatchCompletions; // /eeadmin stats / 全局批完成计数
    private long batchTotal, batchProcessed;
    private double batchQuotaAcc;
    private boolean batchUnitExhausted;
    private final java.util.ArrayDeque<com.endlessepoch.core.api.energy.eb.batch.ShardResultUnit>
            batchPending = new java.util.ArrayDeque<>();

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
                                com.endlessepoch.core.api.recipe.AbstractMachineRecipe bestMr = null;
                                net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.SingleRecipeInput> vanillaMatch = null;
                                int minRejectedTier = Integer.MAX_VALUE; // voltage-rejected candidates / 被电压拒绝的候选
                                for (var holder : level.getRecipeManager().getAllRecipesFor(
                                        this.<net.minecraft.world.item.crafting.SingleRecipeInput,
                                              net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.SingleRecipeInput>>getRecipeType())) {
                                    var r = holder.value();
                                    if (!r.matches(recipeInput, level)) continue;
                                    if (r instanceof com.endlessepoch.core.api.recipe.AbstractMachineRecipe mr) {
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
                                    voltageBlockedTier = minRejectedTier != Integer.MAX_VALUE ? minRejectedTier : -1;
                                    backpressure.tick(minRejectedTier != Integer.MAX_VALUE
                                            ? com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.VOLTAGE_LOW
                                            : com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.RECIPE_MISMATCH);
                                    continue;
                                }
                                voltageBlockedTier = -1;
                                java.util.List<net.minecraft.world.item.ItemStack> results;
                                int base;
                                int ocCount = 0;
                                if (bestMr != null) {
                                    int rawOc = com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.overclockCount(
                                            machineTier, bestMr.getRequiredTier().ordinal(), com.endlessepoch.core.Config.p3MaxOverclock);
                                    ocCount = overclockEnabled
                                            ? com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.optimalOverclock(
                                                    getParallelCap(), getEnergyRate(),
                                                    bestMr.getProcessingTime(), bestMr.getEnergyPerTick(), rawOc)
                                            : 0;
                                    results = bestMr.getResults();
                                    base = (int) com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.computeDuration(
                                            bestMr.getProcessingTime(), ocCount);
                                    pendingMaxHeat = bestMr.getMaxHeat() > 0 ? bestMr.getMaxHeat()
                                            : java.util.Optional.ofNullable(com.endlessepoch.core.api.energy.eb.HeatMapCache.get(currentProfileId))
                                                    .map(com.endlessepoch.core.api.energy.eb.HeatConfig::maxHeat).orElse(10.0);
                                    bgEnergyCost = com.endlessepoch.core.Config.p3EnergyEnabled
                                            ? com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.computeEnergyPerUnit(
                                                    bestMr.getEnergyPerTick(), bestMr.getProcessingTime(), ocCount)
                                            : 0L;
                                } else {
                                    // Vanilla recipes run as ELV: same overclock + default energy cost
                                    // 原版配方按 ELV 电压处理：同样超频 + 默认能耗，杜绝免费加工
                                    int cook = vanillaMatch instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe ac ? ac.getCookingTime() : 200;
                                    ocCount = overclockEnabled
                                            ? com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.optimalOverclock(
                                                    getParallelCap(), getEnergyRate(),
                                                    cook, com.endlessepoch.core.Config.p3VanillaEnergyPerTick,
                                                    com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.overclockCount(
                                                            machineTier, 0, com.endlessepoch.core.Config.p3MaxOverclock))
                                            : 0;
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
                                if (com.endlessepoch.core.Config.heatEnabled && heatEnabled) {
                                    double heat = heatComponent.getHeat(currentProfileId, level.getGameTime());
                                    heatFactor = com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.heatFactor(
                                            heat, pendingMaxHeat, com.endlessepoch.core.Config.heatSpeedBoostMax);
                                    adj = Math.max(1, (int)(base / heatFactor));
                                    // Cold start: matched recipe but machine heat is zero / 配方已匹配但机器热量为零
                                    if (heat == 0.0 && pendingMaxHeat > 0) {
                                        tickBackpressure(com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.COLD_START);
                                    }
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
                            if (paused) return;
                            // Energy pre-check: never consume input we can't pay for
                            // 能量前置检查：付不起能量就不消耗输入
                            long cost = bgEnergyCost;
                            if (cost > 0 && !consumeEnergy(cost, true)) {
                                tickBackpressure(com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.VOLTAGE_LOW);
                                return;
                            }
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
            clearBatchState();
            com.endlessepoch.core.api.energy.eb.EventLifecycleManager.unregister(
                    com.endlessepoch.core.api.energy.eb.HashUtil.hash(worldPosition));
            com.endlessepoch.core.event.BlockPlaceHandler.unregisterController(worldPosition);
        }
    }

    /**
     * Discard the in-flight batch plan — lossless: inputs are only consumed at
     * write-back, so dropped plans cost nothing but the wasted computation.
     * 作废在途批计划——无损：输入仅在写回时消耗，丢弃计划只损失已算的 CPU。
     */
    private void invalidateBatch() {
        long ph = com.endlessepoch.core.api.energy.eb.HashUtil.hash(worldPosition);
        com.endlessepoch.core.api.energy.eb.batch.MachineLoadLimiter.cancel(ph);
        com.endlessepoch.core.api.energy.eb.batch.SegmentMergeManager.clear(ph);
        batchPending.clear();
        batchActive = false;
        batchQuotaAcc = 0;
        lastEffParallel = 0;
        currentHeatBoost = 100;
        batchTotal = 0;
        batchProcessed = 0;
        batchSnapshotVersion = -1;
        setChanged();
    }

    private void clearBatchState() {
        batchActive = false;
        batchDeferred = false;
        kickoffAt = 0;
        lastEffParallel = 0;
        batchPending.clear();
        batchTotal = 0;
        batchProcessed = 0;
        batchQuotaAcc = 0;
        com.endlessepoch.core.api.energy.eb.batch.MachineLoadLimiter.cancel(
                com.endlessepoch.core.api.energy.eb.HashUtil.hash(worldPosition));
        com.endlessepoch.core.api.energy.eb.batch.SegmentMergeManager.clear(
                com.endlessepoch.core.api.energy.eb.HashUtil.hash(worldPosition));
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
        if (supportedTypes.isEmpty()) {
            currentProfileId = null; // structural machine with no recipes / 结构型机器无需加工
        } else if (!supportedTypes.contains(currentProfileId))
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
        processingInput = net.minecraft.world.item.ItemStack.EMPTY; progress = 0; maxProgress = 0;
        cachedResults = java.util.List.of();
        inputBusPos.clear(); outputBusPos.clear();
        energyInputPos.clear(); energyOutputPos.clear();
        fluidInputPos.clear(); fluidOutputPos.clear();
        parallelHatchPos.clear();
        partsScanned = false;
        completionTick = 0;
        voltageBlockedTier = -1;
        currentHeatBoost = 100;
        backpressure.reset();
        clearBatchState();
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

    /** Tick backpressure and log state transitions. / 推进背压状态机并记录状态转换。 */
    private void tickBackpressure(com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State desired) {
        var before = backpressure.getState();
        backpressure.tick(desired);
        if (backpressure.getState() != before) {
            LOGGER.info("[EB-BP] backpressure {} -> {}", before, backpressure.getState());
        }
    }

    public boolean isOutputBlocked() { return backpressure.getState() == com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.OUTPUT_FULL; }
    public boolean hasWork() { return batchActive || !processingInput.isEmpty(); }
    public long getBatchOpsProcessed() { return totalOpsProcessed; }
    long totalOpsProcessed;
    public int getProgress() {
        if (batchActive) return batchTotal <= 0 ? 0 : (int) (batchProcessed * 1000 / Math.max(1, batchTotal));
        return progress;
    }
    public int getMaxProgress() { return batchActive ? 1000 : maxProgress; }
    public int getProcessingItemId() {
        if (batchActive) {
            var head = batchPending.peekFirst();
            return head != null ? (int) head.inputItemId() : 0;
        }
        return processingInput.isEmpty() ? 0
                : net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(processingInput.getItem());
    }

    /** Batch pipeline currently running. / 批处理进行中。 */
    public boolean isBatchActive() { return batchActive; }

    /** Effective parallel for GUI: live batch value, else hardware cap. / GUI 显示的有效并行：批中取实时值，否则硬件上限。 */
    public int getDisplayEffectiveParallel() {
        return batchActive && lastEffParallel > 0 ? lastEffParallel : getParallelCap();
    }

    public ResourceLocation getCurrentProfileId() { return currentProfileId; }

    /** Lowest requiredTier ordinal rejected by the voltage gate, -1 = none. / 被电压门槛拒绝的最低需求电压序数，-1=无。 */
    public int getVoltageBlockedTier() { return voltageBlockedTier; }

    /** Matched recipe is waiting for energy. / 配方已匹配但在等能量。 */
    public boolean isEnergyBlocked() { return backpressure.getState() == com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.VOLTAGE_LOW; }

    /**
     * Energy input hatch received power — retry if we were waiting for energy.
     * Event-driven counterpart of InputBus.onContentsChanged.
     * 能源输入仓收到能量——若正在等电则重试。与 InputBus.onContentsChanged 对应的事件源。
     */
    public void onEnergyReceived() {
        if (formed && isEnergyBlocked()) publishProcessEvent();
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

    public void togglePause() {
        paused = !paused;
        setChanged();
        // Resume kick — the pipeline is publish-gated while paused / 恢复时补发启动（暂停期间管线被发布门拦截）
        if (!paused) publishProcessEvent();
    }
    public void toggleHeat() { heatEnabled = !heatEnabled; setChanged(); }
    public void toggleOverclock() { overclockEnabled = !overclockEnabled; setChanged(); }
    public boolean isHeatEnabled() { return heatEnabled; }
    public boolean isOverclockEnabled() { return overclockEnabled; }
    private boolean effectsEnabled = true;
    public boolean isEffectEnabled() { return effectsEnabled; }
    public boolean hasEffect() { return machineId != null && com.endlessepoch.core.api.multiblock.MachineRegistry.get(machineId).map(def -> def.hasEffect()).orElse(false); }
    public void toggleEffect() { effectsEnabled = !effectsEnabled; setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

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
        // World load restores formed via NBT without onMultiblockFormed — stagger the
        // first kickoff across 2 seconds by posHash so machines don't all pile onto
        // the same tick.
        // 读档恢复 formed 但不触发 onMultiblockFormed——按 posHash 散列到 2 秒窗口，
        // 防止多机同时苏醒挤爆单 tick。
        if (needsKickoff) {
            needsKickoff = false;
            ebFlow.resync(tick);
            int delay = (int) (Math.floorMod(
                    com.endlessepoch.core.api.energy.eb.HashUtil.hash(worldPosition), 40));
            batchDeferred = delay > 0;
            if (batchDeferred) {
                kickoffAt = tick + delay;
            } else {
                publishProcessEvent(); // slot 0 → fire now / 槽位 0 → 立即踢
            }
        }
        if (kickoffAt > 0 && tick >= kickoffAt) {
            kickoffAt = 0;
            batchDeferred = false;
            publishProcessEvent();
        }
        ebFlow.flush(tick);
        // Prime-offset deferred batch start — retry when the stagger slot opens
        // 被质数偏移推迟的批启动——错峰时隙到点续试
        if (batchDeferred && com.endlessepoch.core.api.energy.eb.batch.PrimeOffsetScheduler.canProcess(
                com.endlessepoch.core.api.energy.eb.HashUtil.hash(worldPosition), tick,
                com.endlessepoch.core.Config.p3PrimeOffsetMode)) {
            batchDeferred = false;
            publishProcessEvent();
        }
        long ph = com.endlessepoch.core.api.energy.eb.HashUtil.hash(worldPosition);
        com.endlessepoch.core.api.energy.eb.batch.SpecResult seg;
        boolean anyDelivered = false;
        while ((seg = com.endlessepoch.core.api.energy.eb.batch.SegmentMergeManager.poll(ph)) != null) {
            anyDelivered = true;
            if (batchActive && seg.version() == batchSnapshotVersion)
                batchPending.addAll(seg.results());
        }
        if (!batchPending.isEmpty()) tickBatch(tick);
        else if (batchActive && anyDelivered) {
            // ForkJoin returned no results / ForkJoin 返回空结果
            LOGGER.warn("[EB] batch delivered empty — no recipe match @{}", worldPosition.toShortString());
            invalidateBatch();
        }
        else if (completionTick > 0 && tick >= completionTick) completeRecipe(tick);
        if (isBatchCapable() && tick % 5 == 0) flowTracker.record(countPendingItems());

        if (++breakCheckTick >= 100) {
            breakCheckTick = 0;
            var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(machineId);
            if (pattern.isEmpty()) return;
            var pat = pattern.get();
            boolean intact = pat.isFrameBased()
                    ? com.endlessepoch.core.api.multiblock.MultiBlockValidator.validateFrame(level, pat, worldPosition, getFacing()) != null
                    : com.endlessepoch.core.api.multiblock.MultiBlockValidator.validate(level, pat, worldPosition, getFacing());
            if (!intact) {
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
        if (currentProfileId == null) return null;
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
     * Machine parallel cap = base parallel + Σ part-provided parallel bonus,
     * clamped by maxParallelPerMachine. Parts own their formula (tier curve or
     * creative/addon custom values) — see PartBlockEntity.getParallelBonus().
     * 单机并行上限 = 基础并行 + Σ 部件提供的并行加成，受配置硬上限钳制。
     * 公式归部件所有（电压曲线或创造/附属自定义）——见 PartBlockEntity.getParallelBonus()。
     */
    public int getParallelCap() {
        if (level == null) return com.endlessepoch.core.Config.p3BaseParallel;
        long cap = 0;
        for (var pp : parallelHatchPos) {
            var be = level.getBlockEntity(pp);
            if (be instanceof com.endlessepoch.core.nova.block.part.PartBlockEntity pe)
                cap += pe.getParallelBonus();
        }
        if (cap > 0) return (int) Math.min(cap, com.endlessepoch.core.Config.p3MaxParallelPerMachine);
        return com.endlessepoch.core.Config.p3BaseParallel;
    }

    /** Σ voltage × amperage from all energy input hatches. / 所有能源输入仓的电压×安培合计。 */
    private long getEnergyRate() {
        long totalRate = 0;
        for (var ep : energyInputPos) {
            if (level.getBlockEntity(ep) instanceof com.endlessepoch.core.nova.block.part.PartBlockEntity pe
                    && pe.getEnergyStorage() != null) {
                var tv = pe.getEnergyStorage().getTier().getMinVoltage();
                long rate = tv.bitLength() >= 63 ? Long.MAX_VALUE
                        : tv.longValue() * pe.getAmperage();
                totalRate = com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.saturatingAdd(totalRate, rate);
            }
        }
        return totalRate;
    }

    /**
     * Effective parallel: hardware cap auto-scaled by sustained energy input rate,
     * so under-powered machines slow down instead of oscillating start-stop-start.
     * 有效并行：硬件上限按持续能量输入速率自动缩放，发电不足时降速而非振荡停启。
     */
    public int getEffectiveParallelCap(long energyPerOp, long duration) {
        int hardware = getParallelCap();
        if (!com.endlessepoch.core.Config.p3EnergyEnabled) return hardware;
        if (energyPerOp <= 0 || duration <= 0) return hardware;
        long totalRate = getEnergyRate();
        if (totalRate <= 0) return 1;
        long sustained = com.endlessepoch.core.api.energy.eb.batch.OverclockUtil
                .sustainedParallel(totalRate, duration, energyPerOp);
        return (int) Math.min(hardware, Math.min(sustained, com.endlessepoch.core.Config.p3MaxParallelPerMachine));
    }

    /**
     * Called by InputBus when items arrive. Two-tier dispatch: <32 inline compute with
     * parallel write-back → ≥32 full ForkJoin batch. The light/heavy write-back split
     * lives inside writeBackOps, not here.
     * Non-machine profiles (vanilla recipes) keep the Subscriber-based light path.
     * 物品进总线时发布事件。两档分发：<32 内联计算+并行写回→≥32 全 ForkJoin 批处理。
     * 写回轻/重路径是 writeBackOps 内部的另一层切分，与此无关。
     * 非机器配方（原版）保留 Subscriber 轻路径。
     */
    public void publishProcessEvent() {
        if (level == null || level.isClientSide() || !formed) return;
        if (paused) return;
        if (inputBusPos.isEmpty()) scanParts();
        if (inputBusPos.isEmpty()) return;
        if (batchActive) return;

        if (currentProfileId == null) return;
        if (isBatchCapable()) {
            long pending = countPendingItems();
            if (pending == 0) {
                currentHeatBoost = 100;
                voltageBlockedTier = -1;
                tickBackpressure(com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.IDLE);
                return;
            }
            if (!hasAnyMatchingRecipe()) {
                currentHeatBoost = 100;
                tickBackpressure(com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.RECIPE_MISMATCH);
                voltageBlockedTier = -1;
                return;
            }
            if (pending >= 32) {
                // Heavy tier (≥32): full ForkJoin batch with prime-offset stagger
                // 重档（≥32）：完整 ForkJoin 批处理 + 质数偏移错峰
                if (!com.endlessepoch.core.api.energy.eb.batch.PrimeOffsetScheduler.canProcess(
                        com.endlessepoch.core.api.energy.eb.HashUtil.hash(worldPosition),
                        level.getGameTime(), com.endlessepoch.core.Config.p3PrimeOffsetMode)) {
                    if (!batchDeferred && Config.ebDebugLog && LOGGER.isDebugEnabled())
                        LOGGER.debug("[EB-DBG] batch deferred by prime offset @{}",
                                worldPosition.toShortString());
                    batchDeferred = true;
                    return;
                }
                if (startBatch()) return;
            }
            if (pending >= 1) {
                startInlineBatch();
            } else {
                // Out of inputs — clear the stale multiplier so the ⚡ label disappears
                // 断料——清除滞留倍率，⚡ 标签随之消失
                currentHeatBoost = 100;
                lastSpeedHeat = -1;
                lastSpeedOcMul = -1;
                voltageBlockedTier = -1;
                tickBackpressure(com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.IDLE);
            }
            return;
        }

        // Non-machine-profile: Subscriber-based light path (vanilla recipes etc.)
        // 非机器配方：Subscriber 轻路径（原版配方等）
        for (var ip : inputBusPos) {
            var be = level.getBlockEntity(ip);
            if (be instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                for (int s = 0; s < bus.getInventory().getSlots(); s++) {
                    var stack = bus.getInventory().getStackInSlot(s);
                    if (stack.isEmpty()) continue;
                    long h = com.endlessepoch.core.api.energy.eb.HashUtil.hash(worldPosition);
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
        if (completionTick == 0) currentHeatBoost = 100; // idle only, keep it while cooking / 仅空闲清除，加工中保留
        tickBackpressure(com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.IDLE);
    }

    // ── Batch pipeline (Phase 3) / 批处理管线 ──

    /** Whether the active profile's recipe type is registered for batch processing. / 当前档位配方类型是否已注册可批处理。 */
    private boolean isBatchCapable() {
        if (currentProfileId == null) return false;
        return com.endlessepoch.core.api.recipe.RecipeSnapshotCache.isBatchCapable(getRecipeType());
    }

    /** Quick pre-check: does ANY input bus item have a matching recipe? / 快速预检：输入总线里是否有任何物品能匹配到配方？ */
    private boolean hasAnyMatchingRecipe() {
        var rt = getRecipeType();
        if (rt == null) return false;
        for (var ip : inputBusPos) {
            if (level.getBlockEntity(ip) instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                for (int s = 0; s < bus.getInventory().getSlots(); s++) {
                    var stack = bus.getInventory().getStackInSlot(s);
                    if (stack.isEmpty()) continue;
                    long id = com.endlessepoch.core.api.energy.eb.ItemSnapshotUtil.itemId(stack);
                    if (com.endlessepoch.core.api.recipe.RecipeSnapshotCache.get(rt, id) != null)
                        return true;
                }
            }
        }
        return false;
    }

    /** Total items across input buses (creative buses report their template counts). / 输入总线物品总数（创造总线按模板数量计）。 */
    private long countPendingItems() {
        long total = 0;
        for (var ip : inputBusPos) {
            if (level.getBlockEntity(ip) instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                long busTotal = 0;
                for (int s = 0; s < bus.getInventory().getSlots(); s++) {
                    var stack = bus.getInventory().getStackInSlot(s);
                    if (stack.isEmpty()) continue;
                    if (bus instanceof com.endlessepoch.core.api.part.ILockedSlotBus lsb && !lsb.isSlotLocked(s)) continue;
                    long c = bus instanceof com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity cb
                            ? cb.getTemplateCount(s) : bus.getStoredAmount(s);
                    busTotal += c;
                }
                total += busTotal;
            }
        }
        return total;
    }

    /**
     * Snapshot inputs → submit to ForkJoin. Main thread only. Returns false when the
     * global shard budget is saturated (caller falls back to the light path).
     * 拍输入快照→提交 ForkJoin。仅主线程。全局分片额度耗尽返回 false（回退轻路径）。
     */
    private boolean startBatch() {
        var units = new java.util.ArrayList<com.endlessepoch.core.api.energy.eb.batch.InputUnit>();
        long totalItems = 0;
        for (int bi = 0; bi < inputBusPos.size(); bi++) {
            if (level.getBlockEntity(inputBusPos.get(bi))
                    instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                for (int s = 0; s < bus.getInventory().getSlots(); s++) {
                    var stack = bus.getInventory().getStackInSlot(s);
                    if (stack.isEmpty()) continue;
                    // Creative buses feed their configured template count / 创造总线按配置的模板数量入批
                    long unitCount = bus instanceof com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity cb
                            ? cb.getTemplateCount(s) : bus.getStoredAmount(s);
                    units.add(new com.endlessepoch.core.api.energy.eb.batch.InputUnit(
                            com.endlessepoch.core.api.energy.eb.ItemSnapshotUtil.itemId(stack),
                            unitCount,
                            com.endlessepoch.core.api.energy.eb.ItemSnapshotUtil.nbtHash(stack),
                            bi, s));
                    totalItems += unitCount;
                }
            }
        }
        if (units.isEmpty()) return false;
        double heat = (com.endlessepoch.core.Config.heatEnabled && heatEnabled)
                ? heatComponent.getHeat(currentProfileId, level.getGameTime()) : 0.0;
        batchMaxHeat = java.util.Optional.ofNullable(
                        com.endlessepoch.core.api.energy.eb.HeatMapCache.get(currentProfileId))
                .map(com.endlessepoch.core.api.energy.eb.HeatConfig::maxHeat).orElse(10.0);
        int hw = getParallelCap();
        long totalRate = getEnergyRate();
        int cv = 0; for (var ip : inputBusPos) { if (level.getBlockEntity(ip) instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity ib) { cv = ib.getCircuitValue(); break; } }
        batchSnapshotVersion = planVersion.incrementAndGet();
        var task = new com.endlessepoch.core.api.energy.eb.batch.BatchTask(
                com.endlessepoch.core.api.energy.eb.HashUtil.hash(worldPosition),
                getEffectiveTier(), heat,
                com.endlessepoch.core.Config.heatSpeedBoostMax,
                overclockEnabled ? com.endlessepoch.core.Config.p3MaxOverclock : 0,
                com.endlessepoch.core.Config.p3EnergyEnabled,
                hw, totalRate, cv, batchSnapshotVersion,
                getRecipeType(),
                java.util.List.copyOf(units));
        if (!com.endlessepoch.core.api.energy.eb.batch.MachineLoadLimiter.submit(task))
            return false;
        batchActive = true;
        batchTotal = totalItems;
        batchProcessed = 0;
        batchQuotaAcc = 0;
        batchPending.clear();
        setChanged();
        return true;
    }

    /**
     * Inline batch: snapshots inputs, computes on the main thread (no ForkJoin overhead),
     * and populates batchPending directly — tickBatch handles parallel write-back.
     * Used for every batch below the ForkJoin threshold (<32 units).
     * 内联批处理：拍输入快照→主线程直接计算（无 ForkJoin 开销）→填入 batchPending，
     * 由 tickBatch 统一并行写回。供 ForkJoin 阈值以下（<32）的全部批次使用。
     */
    private void startInlineBatch() {
        var units = new java.util.ArrayList<com.endlessepoch.core.api.energy.eb.batch.InputUnit>();
        long totalItems = 0;
        for (int bi = 0; bi < inputBusPos.size(); bi++) {
            if (level.getBlockEntity(inputBusPos.get(bi))
                    instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                for (int s = 0; s < bus.getInventory().getSlots(); s++) {
                    var stack = bus.getInventory().getStackInSlot(s);
                    if (stack.isEmpty()) continue;
                    long unitCount = bus instanceof com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity cb
                            ? cb.getTemplateCount(s) : bus.getStoredAmount(s);
                    units.add(new com.endlessepoch.core.api.energy.eb.batch.InputUnit(
                            com.endlessepoch.core.api.energy.eb.ItemSnapshotUtil.itemId(stack),
                            unitCount,
                            com.endlessepoch.core.api.energy.eb.ItemSnapshotUtil.nbtHash(stack),
                            bi, s));
                    totalItems += unitCount;
                }
            }
        }
        if (units.isEmpty()) return;
        double heat = (com.endlessepoch.core.Config.heatEnabled && heatEnabled)
                ? heatComponent.getHeat(currentProfileId, level.getGameTime()) : 0.0;
        batchMaxHeat = java.util.Optional.ofNullable(
                        com.endlessepoch.core.api.energy.eb.HeatMapCache.get(currentProfileId))
                .map(com.endlessepoch.core.api.energy.eb.HeatConfig::maxHeat).orElse(10.0);
        int hw = getParallelCap();
        long totalRate = getEnergyRate();
        int cv = 0; for (var ip : inputBusPos) { if (level.getBlockEntity(ip) instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity ib) { cv = ib.getCircuitValue(); break; } }
        batchSnapshotVersion = planVersion.incrementAndGet();
        var task = new com.endlessepoch.core.api.energy.eb.batch.BatchTask(
                com.endlessepoch.core.api.energy.eb.HashUtil.hash(worldPosition),
                getEffectiveTier(), heat,
                com.endlessepoch.core.Config.heatSpeedBoostMax,
                overclockEnabled ? com.endlessepoch.core.Config.p3MaxOverclock : 0,
                com.endlessepoch.core.Config.p3EnergyEnabled,
                hw, totalRate, cv, batchSnapshotVersion,
                getRecipeType(),
                java.util.List.copyOf(units));
        var results = com.endlessepoch.core.api.energy.eb.batch.BatchExecutor.computeInline(task);
        if (results.isEmpty()) {
            voltageBlockedTier = -1;
            if (!processingInput.isEmpty()) {
                processingInput = net.minecraft.world.item.ItemStack.EMPTY; progress = 0; maxProgress = 0; setChanged();
            }
            return;
        }
        batchPending.addAll(results);
        batchActive = true;
        batchTotal = totalItems;
        batchProcessed = 0;
        batchQuotaAcc = 0;
        if (Config.ebDebugLog && LOGGER.isDebugEnabled())
            LOGGER.debug("[EB-DBG] inline batch started @{}: {} items, {} result units",
                    worldPosition.toShortString(), totalItems, results.size());
        setChanged();
    }

    /**
     * Batch tick: drain delivered segments, pace write-back by parallelCap/duration,
     * hard-capped at mainThreadLimit (≤256) ops per tick.
     * 批处理 tick：取回结果分段，按 并行上限/耗时 节拍写回，主线程硬限 ≤256 单元/tick。
     */
    private void tickBatch(long tick) {
        long ph = com.endlessepoch.core.api.energy.eb.HashUtil.hash(worldPosition);
        // Phase 4: unified version check replaces 4-field stale-plan guard
        if (planVersion.get() != batchSnapshotVersion) {
            LOGGER.info("[EB-P4] batch plan invalidated at {} (planVersion {} != snapshot {})", worldPosition, planVersion.get(), batchSnapshotVersion);
            invalidateBatch();
            publishProcessEvent();
            return;
        }
        com.endlessepoch.core.api.energy.eb.batch.SpecResult seg;
        while ((seg = com.endlessepoch.core.api.energy.eb.batch.SegmentMergeManager.poll(ph)) != null) {
            if (seg.version() != batchSnapshotVersion) {
                LOGGER.warn("[EB-P4] segment discarded @{} (v{} != plan v{})", worldPosition.toShortString(), seg.version(), batchSnapshotVersion);
                continue;
            }
            batchPending.addAll(seg.results());
        }
        // Chunked submission: totals only settle once the limiter drained every chunk
        // 分块提交：限流器所有分块跑完后 totals 才收敛
        boolean computing = !com.endlessepoch.core.api.energy.eb.batch.MachineLoadLimiter.isIdle(ph);
        if (Config.ebDebugLog && LOGGER.isDebugEnabled() && tick % Config.ebDebugInterval == 0) {
            long remainingOps = 0;
            for (var u : batchPending) remainingOps += u.ops();
            LOGGER.debug("[EB-DBG] batch progress @{}: {}/{} ops ({}%), effParallel={}, budget={}",
                    worldPosition.toShortString(), batchProcessed, batchProcessed + remainingOps,
                    String.format("%.1f", batchTotal > 0 ? 100.0 * batchProcessed / batchTotal : 0.0),
                    lastEffParallel, com.endlessepoch.core.api.energy.eb.batch.MainThreadRateLimiter.remaining());
        }
        if (!computing) {
            long remaining = 0;
            for (var u : batchPending) remaining += u.ops();
            batchTotal = batchProcessed + remaining; // snap to matched ops / 校正为实际匹配数
        }
        if (batchPending.isEmpty()) {
            if (computing) return;
            batchActive = false;
            batchQuotaAcc = 0;
            batchCompletions++;
            globalBatchCompletions++;
            setChanged();
            publishProcessEvent();
            return;
        }
        if (paused) return;
        var head = batchPending.peekFirst();
        // Live speed multiplier: ocMul × current heat factor, lazy — recalc when heat
        // OR the plan's oc multiplier changes (pegged heat froze the old display otherwise)
        // 实时倍率：超频 × 当前热量，惰性更新——热量或计划超频倍率变化时重算
        // （否则热量满格时旧显示永远冻结）
        if (com.endlessepoch.core.Config.heatEnabled && heatEnabled && currentProfileId != null) {
            double liveHeat = heatComponent.getHeatRaw(currentProfileId);
            if (Math.abs(liveHeat - lastSpeedHeat) > 0.01 || head.ocMulX100() != lastSpeedOcMul) {
                lastSpeedHeat = liveHeat;
                lastSpeedOcMul = head.ocMulX100();
                double liveHf = com.endlessepoch.core.api.energy.eb.batch.OverclockUtil.heatFactor(
                        liveHeat, batchMaxHeat, com.endlessepoch.core.Config.heatSpeedBoostMax);
                currentHeatBoost = Math.max(100, (int) Math.round(head.ocMulX100() * liveHf));
            }
        } else {
            currentHeatBoost = head.ocMulX100();
            // Invalidate the lazy cache — re-enabling heat must recalc even when heat is pegged
            // 作废惰性缓存——重开热量时即使热量满格也必须重算
            lastSpeedHeat = -1;
            lastSpeedOcMul = -1;
        }
        // Effective parallel auto-scales to sustained energy rate — no oscillation
        // 有效并行随能量输入速率自动缩放——不振荡
        int effParallel = getEffectiveParallelCap(head.energyPerOp(), head.finalDuration());
        lastEffParallel = effParallel;
        batchQuotaAcc += (double) effParallel / Math.max(1, head.finalDuration());
        int budget = (int) Math.min(Math.min(batchQuotaAcc,
                        com.endlessepoch.core.api.energy.eb.batch.MainThreadRateLimiter.currentLimit()),
                head.ops());
        // Global cross-machine write-back budget — acquire, write, release the unused
        // 全局跨机写回预算——先领额度，写回后归还未用完部分
        budget = com.endlessepoch.core.api.energy.eb.batch.MainThreadRateLimiter.acquire(budget);
        if (budget <= 0) return;
        batchUnitExhausted = false;
        int done = writeBackOps(head, budget, tick);
        if (done < budget)
            com.endlessepoch.core.api.energy.eb.batch.MainThreadRateLimiter.release(budget - done);
        if (done > 0) {
            batchQuotaAcc -= done;
            batchProcessed += done;
            totalOpsProcessed += done;
        }
        if (batchUnitExhausted) {
            // Inputs vanished (player/pipe) — drop the unit's remainder / 输入被取走，丢弃该单元剩余
            batchPending.pollFirst();
            batchTotal -= (head.ops() - done);
        } else if (done >= head.ops()) {
            batchPending.pollFirst();
        } else if (done > 0) {
            batchPending.pollFirst();
            batchPending.addFirst(head.withOps(head.ops() - done));
        }
        if (done > 0) setChanged();
        if (!computing && batchPending.isEmpty()) {
            batchActive = false;
            batchQuotaAcc = 0;
            batchCompletions++;
            globalBatchCompletions++;
            setChanged();
            publishProcessEvent();
        }
    }


    /**
     * Apply up to budget ops of one result on the main thread. Operations are batched —
     * N identical ops are merged into one insert/extract/deduct cycle, then heat is
     * applied per-op. This drops the write-back cost from O(budget) to O(1) when
     * consecutive ops share the same recipe outputs.
     * 主线程写回，批量合并——N 个相同 op 合并为一次插入/抽取/扣能，热量仍逐 op 计算。
     * 将写回开销从 O(budget) 降到 O(1)。
     */
    private int writeBackOps(com.endlessepoch.core.api.energy.eb.batch.ShardResultUnit r,
                             int budget, long tick) {
        // Light path: budget too small for batching overhead / 轻路径：预算太小不值得批量
        if (budget <= 64) return writeBackOpsLight(r, budget, tick);
        return writeBackOpsHeavy(r, budget, tick);
    }

    /** Per-op loop for light write-back (budget ≤ 64). / 单 op 循环（轻路径）。 */
    private int writeBackOpsLight(com.endlessepoch.core.api.energy.eb.batch.ShardResultUnit r,
                                  int budget, long tick) {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.byId((int) r.inputItemId());
        if (item == net.minecraft.world.item.Items.AIR) { batchUnitExhausted = true; return 0; }
        int done = 0;
        while (done < budget) {
            if (!insertOutputs(r, true)) {
                tickBackpressure(com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.OUTPUT_FULL);
                break;
            }
            if (r.energyPerOp() > 0 && !consumeEnergy(r.energyPerOp(), true)) {
                tickBackpressure(com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.VOLTAGE_LOW);
                break;
            }
            if (!extractOneInput(item)) { batchUnitExhausted = true; break; }
            if (r.energyPerOp() > 0) consumeEnergy(r.energyPerOp(), false);
            insertOutputs(r, false);
            if (com.endlessepoch.core.Config.heatEnabled && heatEnabled && currentProfileId != null) {
                double mh = r.maxHeat() > 0 ? r.maxHeat()
                        : java.util.Optional.ofNullable(com.endlessepoch.core.api.energy.eb.HeatMapCache.get(currentProfileId))
                                .map(com.endlessepoch.core.api.energy.eb.HeatConfig::maxHeat).orElse(10.0);
                heatComponent.bulkHeat(currentProfileId, mh, tick, tick);
            }
            tickBackpressure(com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.IDLE);
            done++; totalOpsProcessed++;
        }
        return done;
    }

    /** Batched write-back for heavy load (budget > 64). / 批量写回（重路径）。 */
    private int writeBackOpsHeavy(com.endlessepoch.core.api.energy.eb.batch.ShardResultUnit r,
                                   int budget, long tick) {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.byId((int) r.inputItemId());
        if (item == net.minecraft.world.item.Items.AIR) { batchUnitExhausted = true; return 0; }

        // Determine max batchable / 计算最大批量
        int outputSlots = countOutputSpace(r);
        if (outputSlots <= 0) {
            tickBackpressure(com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.OUTPUT_FULL);
            return 0;
        }
        int energyAffordable = r.energyPerOp() <= 0 ? budget
                : countEnergyAffordable(r.energyPerOp(), budget);
        if (energyAffordable <= 0) {
            tickBackpressure(com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.VOLTAGE_LOW);
            return 0;
        }
        int inputAvailable = countMatchingInputs(item, Math.min(budget, Math.min(outputSlots, energyAffordable)));
        if (inputAvailable <= 0) { batchUnitExhausted = true; return 0; }
        int batch = Math.min(budget, Math.min(outputSlots, Math.min(energyAffordable, inputAvailable)));

        // Execute / 执行
        if (!extractInputs(item, batch)) { batchUnitExhausted = true; return 0; }
        if (r.energyPerOp() > 0) consumeEnergy(r.energyPerOp() * (long) batch, false);
        insertOutputsBatched(r, batch);
        if (com.endlessepoch.core.Config.heatEnabled && heatEnabled && currentProfileId != null) {
            double mh = r.maxHeat() > 0 ? r.maxHeat()
                    : java.util.Optional.ofNullable(com.endlessepoch.core.api.energy.eb.HeatMapCache.get(currentProfileId))
                            .map(com.endlessepoch.core.api.energy.eb.HeatConfig::maxHeat).orElse(10.0);
            for (int i = 0; i < batch; i++)
                heatComponent.bulkHeat(currentProfileId, mh, tick, tick);
        }
        tickBackpressure(com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.IDLE);
        return batch;
    }

    // Batched write-back helpers / 批量写回辅助方法

    /** Max ops that fit in output buses for this shard unit. / 该分片单元可写入输出总线的最大 ops 数。 */
    private int countOutputSpace(com.endlessepoch.core.api.energy.eb.batch.ShardResultUnit r) {
        // MAX_VALUE doubles as "unlimited" (oversized bus) — track "any real output seen"
        // separately so unlimited capacity isn't mistaken for the empty-outputs sentinel.
        // MAX_VALUE 兼作"无限"（巨量总线）——"是否见到真实产物"单独跟踪，
        // 防止无限容量被误判为"无产物"哨兵而归零。
        int max = Integer.MAX_VALUE;
        boolean found = false;
        for (int i = 0; i < r.outputItemIds().length; i++) {
            var out = net.minecraft.core.registries.BuiltInRegistries.ITEM.byId((int) r.outputItemIds()[i]);
            if (out == net.minecraft.world.item.Items.AIR) continue;
            found = true;
            int perOp = (int) r.outputCounts()[i];
            int slots = countInsertCapacity(out, perOp);
            if (slots < max) max = slots;
            if (max == 0) break;
        }
        return found ? max : 0;
    }

    /** Ops affordable from energy hatches (simulate, cap at budget). / 能源仓可支撑的 op 数。 */
    private int countEnergyAffordable(long energyPerOp, int budgetCap) {
        if (energyPerOp <= 0) return budgetCap;
        long total = energyPerOp;
        int n = 1;
        while (n < budgetCap && total <= Long.MAX_VALUE - energyPerOp) {
            total += energyPerOp;
            n++;
        }
        if (!consumeEnergy(total, true)) {
            // Binary search for max affordable / 二分查找最大可负担量
            int lo = 1, hi = n - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (consumeEnergy(energyPerOp * (long) mid, true)) lo = mid;
                else hi = mid - 1;
            }
            return lo;
        }
        return n;
    }

    /** Count how many matching items exist in input buses, capped. / 输入总线中匹配物品数量。 */
    private int countMatchingInputs(net.minecraft.world.item.Item item, int cap) {
        int total = 0;
        for (var ip : inputBusPos) {
            if (level.getBlockEntity(ip) instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                for (int s = 0; s < bus.getInventory().getSlots(); s++) {
                    var stack = bus.getInventory().getStackInSlot(s);
                    if (!stack.isEmpty() && stack.getItem() == item) {
                        if (bus instanceof com.endlessepoch.core.api.part.ILockedSlotBus lsb && !lsb.isSlotLocked(s)) continue;
                        // Creative templates present their configured count, not the count-1 placeholder
                        // 创造模板按配置数量计，而非 count=1 占位堆
                        total += bus instanceof com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity cb
                                ? cb.getTemplateCount(s) : bus.getStoredAmount(s);
                        if (total >= cap) return cap;
                    }
                }
            }
        }
        return total;
    }

    /** Extract {@code count} matching items from input buses. / 从输入总线取出 count 个匹配物品。 */
    private boolean extractInputs(net.minecraft.world.item.Item item, int count) {
        int remaining = count;
        for (var ip : inputBusPos) {
            if (level.getBlockEntity(ip) instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                for (int s = 0; s < bus.getInventory().getSlots() && remaining > 0; s++) {
                    var stack = bus.getInventory().getStackInSlot(s);
                    if (!stack.isEmpty() && stack.getItem() == item) {
                        if (bus instanceof com.endlessepoch.core.api.part.ILockedSlotBus lsb && !lsb.isSlotLocked(s)) continue;
                        // Infinite source never depletes — extraction is a no-op, count it fulfilled
                        // 无限源永不减少——实扣是空操作，直接计为已满足
                        if (bus instanceof com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity) {
                            remaining = 0;
                            break;
                        }
                        int take = Math.min(remaining, stack.getCount());
                        bus.getInventory().extractItem(s, take, false);
                        remaining -= take;
                    }
                }
            }
        }
        return remaining == 0;
    }

    /** Insert batched outputs: multiply each output count by {@code batch}. / 批量写入：每种产物数量 ×batch。 */
    private void insertOutputsBatched(com.endlessepoch.core.api.energy.eb.batch.ShardResultUnit r, int batch) {
        for (int i = 0; i < r.outputItemIds().length; i++) {
            var out = net.minecraft.core.registries.BuiltInRegistries.ITEM.byId((int) r.outputItemIds()[i]);
            if (out == net.minecraft.world.item.Items.AIR) continue;
            var stack = new net.minecraft.world.item.ItemStack(out, (int) r.outputCounts()[i] * batch);
            insertStack(stack, false);
        }
    }

    /** How many stacks of (item, count) fit in output buses. / 输出总线能塞进多少组 (item, perOp)。 */
    private int countInsertCapacity(net.minecraft.world.item.Item item, int perOp) {
        int total = 0;
        for (var op : outputBusPos) {
            if (level.getBlockEntity(op) instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                if (bus instanceof com.endlessepoch.core.nova.block.part.CreativeOversizedBusBlockEntity)
                    return Integer.MAX_VALUE;
                int maxStack = Math.min(item.getDefaultInstance().getMaxStackSize(), bus.getInventory().getSlotLimit(0));
                for (int si = 0; si < bus.getInventory().getSlots(); si++) {
                    var existing = bus.getInventory().getStackInSlot(si);
                    if (existing.isEmpty()) { total += maxStack / perOp; continue; }
                    if (existing.getItem() == item) {
                        int space = maxStack - existing.getCount();
                        if (space > 0) total += space / perOp;
                    }
                }
            }
        }
        return total;
    }

    /** Extract one matching item from any input bus. / 从任一输入总线取出 1 个匹配物品。 */
    private boolean extractOneInput(net.minecraft.world.item.Item item) {
        for (var ip : inputBusPos) {
            if (level.getBlockEntity(ip) instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                for (int s = 0; s < bus.getInventory().getSlots(); s++) {
                    var stack = bus.getInventory().getStackInSlot(s);
                    if (bus instanceof com.endlessepoch.core.api.part.ILockedSlotBus lsb && !lsb.isSlotLocked(s)) continue;
                    if (!stack.isEmpty() && stack.getItem() == item
                            && !bus.getInventory().extractItem(s, 1, false).isEmpty())
                        return true;
                }
            }
        }
        return false;
    }

    /** Insert one op's outputs into output buses. / 将单次加工的全部产物写入输出总线。 */
    private boolean insertOutputs(com.endlessepoch.core.api.energy.eb.batch.ShardResultUnit r,
                                  boolean simulate) {
        for (int i = 0; i < r.outputItemIds().length; i++) {
            var out = net.minecraft.core.registries.BuiltInRegistries.ITEM.byId((int) r.outputItemIds()[i]);
            if (out == net.minecraft.world.item.Items.AIR) continue;
            var stack = new net.minecraft.world.item.ItemStack(out, (int) r.outputCounts()[i]);
            if (!insertStack(stack, simulate)) return false;
        }
        return true;
    }

    /** Insert a stack fully into any output bus slot. / 将整叠完整塞进任一输出总线槽位。 */
    private boolean insertStack(net.minecraft.world.item.ItemStack stack, boolean simulate) {
        for (var op : outputBusPos) {
            if (level.getBlockEntity(op) instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity bus) {
                for (int si = 0; si < bus.getInventory().getSlots(); si++) {
                    if (bus.getInventory().insertItem(si, stack.copy(), true).isEmpty()) {
                        if (!simulate) bus.getInventory().insertItem(si, stack.copy(), false);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Completion tick arrived: output + heat + auto-continue. / 完工: 出货+热量+续投 */
    private void completeRecipe(long tick) {        for (var result : cachedResults) {
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
        if (com.endlessepoch.core.Config.heatEnabled && heatEnabled && currentProfileId != null)
            heatComponent.bulkHeat(currentProfileId, pendingMaxHeat, recipeStartedTick, tick);
        processingInput = net.minecraft.world.item.ItemStack.EMPTY;
        progress = 0; maxProgress = 0; completionTick = 0;
        tickBackpressure(com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.IDLE);
        setChanged();
        publishProcessEvent();
    }

    private int breakCheckTick;
    private boolean needsKickoff; // re-publish kick-off after world load / 读档后补发启动事件

    private void tryFormation() {
        if (machineId == null) return;
        var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(machineId);
        if (pattern.isEmpty()) return;
        var pat = pattern.get();
        boolean ok = pat.isFrameBased()
                || com.endlessepoch.core.api.multiblock.MultiBlockValidator.validate(level, pat, worldPosition, getFacing());
        if (ok) {
            com.endlessepoch.core.api.multiblock.MultiBlockFormHandler.tryForm(
                    this, pat, getFacing(), null);
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
        tag.putBoolean("heatEnabled", heatEnabled);
        tag.putBoolean("overclockEnabled", overclockEnabled);
        tag.putBoolean("effectsEnabled", effectsEnabled);
        tag.putInt("backpressureState", backpressure.getState().ordinal());
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
        if (tag.contains("batchEnabled")) { /* forward compat: old B-button saves, value discarded / 旧版 B 按钮存档，值抛弃 */ }
        if (tag.contains("heatEnabled")) heatEnabled = tag.getBoolean("heatEnabled");
        if (tag.contains("overclockEnabled")) overclockEnabled = tag.getBoolean("overclockEnabled");
        if (tag.contains("effectsEnabled")) effectsEnabled = tag.getBoolean("effectsEnabled");
        // Recover backpressure from new ordinal or migrate old outputBlocked boolean
        // 从新序号恢复背压，或从旧 outputBlocked 布尔迁移
        if (tag.contains("backpressureState")) {
            int ord = tag.getInt("backpressureState");
            var states = com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.values();
            if (ord >= 0 && ord < states.length) {
                // Fast-forward the state: tick past the debounce counter
                // 快进到该状态：跳过防抖计数器
                for (int i = 0; i < 5; i++) backpressure.tick(states[ord]);
            }
        } else if (tag.getBoolean("outputBlocked")) {
            for (int i = 0; i < 5; i++)
                tickBackpressure(com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.OUTPUT_FULL);
        }
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
