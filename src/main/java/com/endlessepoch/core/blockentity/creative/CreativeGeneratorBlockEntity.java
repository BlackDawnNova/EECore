package com.endlessepoch.core.blockentity.creative;

import com.endlessepoch.core.api.energy.*;
import com.endlessepoch.core.api.tier.VoltageTier;
import com.endlessepoch.core.menu.creative.CreativeGeneratorMenu;
import com.endlessepoch.core.network.SyncGeneratorPacket;
import com.endlessepoch.core.registry.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Creative generator block entity.
 * Infinite Omega energy source for testing. Supports all voltage tiers ELV~QV.
 * <p>
 * 创造模式发电机方块实体。
 * 用于测试的无限 Omega 能量源。支持所有电压等级 ELV~QV。
 */
public class CreativeGeneratorBlockEntity extends BlockEntity
        implements MenuProvider, IOmegaEnergyStorage {

    private volatile VoltageTier selectedTier = VoltageTier.LV;
    private volatile BigInteger amperage = BigInteger.ONE;
    private volatile BigInteger outputPerTick = BigInteger.valueOf(50);
    private final AtomicBoolean outputEnabled = new AtomicBoolean(true);
    private volatile boolean logToChat = false;

    private final Set<UUID> subscribers = new HashSet<>();
    private final List<String> logs = new ArrayList<>();
    private OmegaValue totalGenerated = OmegaValue.zero();
    private int tickCounter = 0;

    private static final ConcurrentHashMap<BlockPos, Boolean> pendingEnable = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, VoltageTier> pendingTier = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, String> pendingOutput = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, Boolean> pendingLogToChat = new ConcurrentHashMap<>();

    public CreativeGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.CREATIVE_GENERATOR.get(), pos, state);
        this.outputPerTick = selectedTier.getMinVoltage().multiply(amperage);
        pendingEnable.remove(pos);
        pendingTier.remove(pos);
        pendingOutput.remove(pos);
        pendingLogToChat.remove(pos);
        addLog(Component.translatable("eecore.generator.log.started"));
        addLog(Component.translatable("eecore.generator.log.tier_set",
                selectedTier.getShortName(), outputPerTick.toString()));
    }

    public void clientTick() {
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        tickCounter++;

        applyPendingState();

        if (!outputEnabled.get()) return;

        if (tickCounter % 1 == 0) {
            EnergyPacket packet = new EnergyPacket(selectedTier, amperage, OmegaValue.of(outputPerTick));
            boolean sent = false;
            for (Direction dir : Direction.values()) {
                // Capability lookup — reaches energy hatches too, not just BEs implementing the interface
                // 走 Capability 查询——能源仓等仅暴露能力的方块也能收到，而非只认实现接口的 BE
                var receiver = level.getCapability(
                        com.endlessepoch.core.api.EECoreCapabilities.OMEGA_ENERGY,
                        worldPosition.relative(dir), dir.getOpposite());
                if (receiver != null && receiver != this) {
                    EnergyPacket accepted = receiver.receivePacket(packet, false);
                    if (accepted != null && !accepted.isEmpty()) {
                        sent = true;
                        totalGenerated = totalGenerated.add(accepted.getEnergy());
                        break;
                    }
                }
            }
            if (!sent) {
            }
        }

        if (tickCounter % 20 == 0) {
            BigInteger perSecond = outputPerTick.multiply(BigInteger.valueOf(20));
            addLog(Component.translatable("eecore.generator.log.output_per_second",
                    OmegaValue.of(perSecond).toDisplayString(), selectedTier.getShortName()));
        }
    }

    private void applyPendingState() {
        BlockPos pos = worldPosition;
        Boolean enabled = pendingEnable.remove(pos);
        VoltageTier tier = pendingTier.remove(pos);
        String outputStr = pendingOutput.remove(pos);
        Boolean logToChat = pendingLogToChat.remove(pos);

        if (enabled != null) {
            this.outputEnabled.set(enabled);
            if (tier != null) {
                this.selectedTier = tier;
                this.outputPerTick = tier.getMinVoltage().multiply(amperage);
            }
            if (outputStr != null) {
                try {
                    this.outputPerTick = new BigInteger(outputStr);
                } catch (NumberFormatException ignored) {}
            }
            setChanged();
            sendSyncToClients();
        }

        if (logToChat != null) {
            this.logToChat = logToChat;
            setChanged();
            sendSyncToClients();
        }
    }

    private void sendSyncToClients() {
        if (level == null || level.isClientSide()) return;

        Set<UUID> currentSubscribers;
        synchronized (subscribers) {
            if (subscribers.isEmpty()) return;
            currentSubscribers = new HashSet<>(subscribers);
        }

        var packet = new SyncGeneratorPacket(
                worldPosition,
                selectedTier.getShortName(),
                outputPerTick,
                amperage,
                outputEnabled.get()
        );

        for (UUID uuid : currentSubscribers) {
            var player = ((ServerLevel) level).getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    public void updateFromSync(SyncGeneratorPacket packet) {
        this.selectedTier = VoltageTier.fromShortName(packet.tierName());
        this.outputPerTick = packet.output();
        this.amperage = packet.amperage();
        this.outputEnabled.set(packet.enabled());
        setChanged();
        if (level != null && level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void toggleOutput() {
        boolean newState = !outputEnabled.get();
        outputEnabled.set(newState);
        addLog(Component.translatable(newState ? "eecore.generator.log.enabled" : "eecore.generator.log.paused"));
        setChanged();
        pendingEnable.put(worldPosition, newState);
        sendSyncToClients();
    }

    public void setTier(VoltageTier tier) {
        if (tier == null || tier == this.selectedTier) return;
        if (tier == VoltageTier.ELV) {
            addLog(Component.translatable("eecore.generator.log.elv_blocked"));
            return;
        }
        this.selectedTier = tier;
        this.outputPerTick = tier.getMinVoltage().multiply(amperage);
        addLog(Component.translatable("eecore.generator.log.power_set",
                tier.getShortName(), outputPerTick.toString()));
        setChanged();
        pendingEnable.put(worldPosition, outputEnabled.get());
        pendingTier.put(worldPosition, tier);
        pendingOutput.put(worldPosition, outputPerTick.toString());
        sendSyncToClients();
    }

    /** Cycle up: 1 → 2 → 4 → 8 → 16 → 1. / 循环增加：1 → 2 → 4 → 8 → 16 → 1。 */
    public void cycleAmperageUp() {
        int[] options = VoltageTier.COMMON_AMPERAGES;
        int idx = 0;
        for (int i = 0; i < options.length; i++) {
            if (BigInteger.valueOf(options[i]).equals(amperage)) {
                idx = (i + 1) % options.length;
                break;
            }
        }
        applyAmperage(BigInteger.valueOf(options[idx]));
    }

    /** Cycle down: 16 → 8 → 4 → 2 → 1 → 16. / 循环减少：16 → 8 → 4 → 2 → 1 → 16。 */
    public void cycleAmperageDown() {
        int[] options = VoltageTier.COMMON_AMPERAGES;
        int idx = options.length - 1;
        for (int i = 0; i < options.length; i++) {
            if (BigInteger.valueOf(options[i]).equals(amperage)) {
                idx = (i - 1 + options.length) % options.length;
                break;
            }
        }
        applyAmperage(BigInteger.valueOf(options[idx]));
    }

    private void applyAmperage(BigInteger newAmp) {
        this.amperage = newAmp;
        this.outputPerTick = selectedTier.getMinVoltage().multiply(amperage);
        addLog(Component.translatable("eecore.generator.log.amperage_set",
                amperage.toString(), outputPerTick.toString()));
        setChanged();
        sendSyncToClients();
    }

    public void resetToLV() {
        this.selectedTier = VoltageTier.LV;
        this.amperage = BigInteger.ONE;
        this.outputPerTick = VoltageTier.LV.getMinVoltage().multiply(amperage);
        this.outputEnabled.set(true);
        totalGenerated = OmegaValue.zero();
        logs.clear();
        addLog(Component.translatable("eecore.generator.log.reset"));
        setChanged();
        pendingEnable.put(worldPosition, outputEnabled.get());
        pendingTier.put(worldPosition, VoltageTier.LV);
        pendingOutput.put(worldPosition, outputPerTick.toString());
        sendSyncToClients();
    }

    public void setLogToChat(boolean logToChat) {
        this.logToChat = logToChat;
        addLog(Component.translatable(logToChat ? "eecore.generator.log.chat_log_enabled" : "eecore.generator.log.chat_log_disabled"));
        pendingLogToChat.put(worldPosition, logToChat);
        setChanged();
        sendSyncToClients();
    }

    public void setOutputPerTick(BigInteger v) {
        if (v == null || v.signum() < 0) v = BigInteger.ZERO;
        this.outputPerTick = v;
        addLog(Component.translatable("eecore.generator.log.output_set", outputPerTick.toString()));
        setChanged();
        pendingOutput.put(worldPosition, outputPerTick.toString());
        sendSyncToClients();
    }

    public void addSubscriber(Player player) {
        if (player != null) {
            synchronized (subscribers) {
                subscribers.add(player.getUUID());
            }
            sendSyncToClients();
        }
    }

    public void removeSubscriber(Player player) {
        if (player != null) {
            synchronized (subscribers) {
                subscribers.remove(player.getUUID());
            }
        }
    }

    public void addLog(Component msg) {
        logs.add(msg.getString());
        if (logs.size() > 100) logs.remove(0);
        if (logToChat && level != null && !level.isClientSide()) {
            synchronized (subscribers) {
                for (UUID uuid : subscribers) {
                    Player p = level.getServer().getPlayerList().getPlayer(uuid);
                    if (p instanceof ServerPlayer sp) {
                        sp.sendSystemMessage(
                                getChatPrefix().copy()
                                        .append(msg.copy().withStyle(net.minecraft.ChatFormatting.WHITE)),
                                false);
                    }
                }
            }
        }
    }

    public void addLog(String msg) {
        addLog(Component.literal(msg));
    }

    private Component getChatPrefix() {
        return Component.translatable("eecore.generator.chat_prefix").withStyle(net.minecraft.ChatFormatting.GREEN);
    }

    @Override
    public EnergyPacket receivePacket(EnergyPacket packet, boolean simulate) {
        return packet;
    }

    @Override
    public EnergyPacket extractPacket(VoltageTier requestedTier, boolean simulate) {
        return null;
    }

    @Override
    public OmegaValue receiveEnergy(OmegaValue amount, boolean simulate) {
        return amount;
    }

    @Override
    public OmegaValue extractEnergy(OmegaValue amount, boolean simulate) {
        return OmegaValue.zero();
    }

    @Override
    public OmegaValue getEnergyStored() {
        return totalGenerated;
    }

    @Override
    public OmegaValue getEnergyStored(VoltageTier tier) {
        return totalGenerated;
    }

    @Override
    public OmegaValue getCapacity() {
        return OmegaValue.max();
    }

    @Override
    public OmegaValue getMaxInput() {
        return OmegaValue.max();
    }

    @Override
    public OmegaValue getMaxOutput() {
        return OmegaValue.max();
    }

    @Override
    public VoltageTier getTier() {
        return VoltageTier.QV;
    }

    public VoltageTier getSelectedTier() { return selectedTier; }
    public BigInteger getAmperage() { return amperage; }
    public BigInteger getOutputPerTick() { return outputPerTick; }
    public boolean isOutputEnabled() { return outputEnabled.get(); }
    public OmegaValue getTotalGenerated() { return totalGenerated; }
    public OmegaValue getDisplayEnergy() { return totalGenerated; }
    public List<String> getLogMessages() { return new ArrayList<>(logs); }
    public boolean isLogToChat() { return logToChat; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("eecore.generator.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        addSubscriber(player);
        return new CreativeGeneratorMenu(id, inv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider prov) {
        super.saveAdditional(tag, prov);
        tag.putString("tier", selectedTier.getShortName());
        tag.putString("amperage", amperage.toString());
        tag.putString("output", outputPerTick.toString());
        tag.putBoolean("enabled", outputEnabled.get());
        tag.putString("totalGenerated", totalGenerated.toBigInteger().toString());
        tag.putBoolean("logToChat", logToChat);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider prov) {
        super.loadAdditional(tag, prov);
        selectedTier = VoltageTier.fromShortName(tag.getString("tier"));
        try {
            amperage = new BigInteger(tag.getString("amperage"));
        } catch (Exception e) {
            amperage = BigInteger.ONE;
        }
        try {
            outputPerTick = new BigInteger(tag.getString("output"));
        } catch (NumberFormatException e) {
            outputPerTick = BigInteger.valueOf(tag.getLong("output"));
        }
        outputEnabled.set(tag.getBoolean("enabled"));
        totalGenerated = OmegaValue.of(tag.getString("totalGenerated"));
        logToChat = tag.getBoolean("logToChat");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, lookup);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookup) {
        loadAdditional(tag, lookup);
    }
}
