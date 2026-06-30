package com.endlessepoch.core.blockentity.creative;

import com.endlessepoch.core.api.energy.*;
import com.endlessepoch.core.api.tier.VoltageTier;
import com.endlessepoch.core.block.creative.CreativeConsumerBlock;
import com.endlessepoch.core.menu.creative.CreativeConsumerMenu;
import com.endlessepoch.core.network.SyncConsumerPacket;
import com.endlessepoch.core.registry.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
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

/**
 * Creative consumer block entity.
 * Receives Omega energy, supports auto/manual mode, displays per-second receive rate.
 * <p>
 * 创造模式消耗器方块实体。
 * 接收 Omega 能量，支持自动/手动模式，显示每秒接收速率。
 */
public class CreativeConsumerBlockEntity extends BlockEntity implements MenuProvider, IOmegaEnergyStorage {

    private final OmegaStorage storage = new OmegaStorage(
            VoltageTier.HARD_LIMIT,
            VoltageTier.HARD_LIMIT,
            BigInteger.ZERO,
            VoltageTier.QV
    );
    private OmegaValue totalReceived = OmegaValue.zero();
    private EnergyPacket lastPacket = null;
    private volatile boolean logToChat = false;

    private volatile boolean autoMode = true;
    private volatile VoltageTier manualTier = VoltageTier.LV;

    private volatile VoltageTier currentReceivedTier = VoltageTier.ELV;
    private volatile VoltageTier lastReceivedTier = VoltageTier.ELV;

    private OmegaValue lastSecondReceived = OmegaValue.zero();
    private OmegaValue currentSecondReceived = OmegaValue.zero();
    private OmegaValue lastSecondLoss = OmegaValue.zero();
    private OmegaValue currentSecondLoss = OmegaValue.zero();
    private OmegaValue lastSecondOriginal = OmegaValue.zero();
    private OmegaValue currentSecondOriginal = OmegaValue.zero();
    private OmegaValue lastSecondActual = OmegaValue.zero();
    private OmegaValue currentSecondActual = OmegaValue.zero();
    private int secondTickCounter = 0;

    private final Set<UUID> subscribers = new HashSet<>();
    private final List<String> logs = new ArrayList<>();
    private int tickCounter = 0;
    private int syncCooldown = 0;

    private static final ConcurrentHashMap<BlockPos, Boolean> pendingClear = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, Boolean> pendingLogToChat = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, Boolean> pendingAutoMode = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, VoltageTier> pendingManualTier = new ConcurrentHashMap<>();

    public CreativeConsumerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.CREATIVE_CONSUMER.get(), pos, state);
        pendingClear.remove(pos);
        pendingLogToChat.remove(pos);
        pendingAutoMode.remove(pos);
        pendingManualTier.remove(pos);
        addLog(Component.translatable("eecore.consumer.log.started"));
        addLog(Component.translatable(
                autoMode ? "eecore.consumer.log.mode_auto_start" : "eecore.consumer.log.mode_manual_start",
                manualTier.getShortName()));
    }

    public void clientTick() {
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;

        applyPendingState();

        tickCounter++;
        secondTickCounter++;

        if (secondTickCounter >= 20) {
            lastSecondReceived = currentSecondReceived;
            lastSecondLoss = currentSecondLoss;
            lastSecondOriginal = currentSecondOriginal;
            lastSecondActual = currentSecondActual;
            currentSecondReceived = OmegaValue.zero();
            currentSecondLoss = OmegaValue.zero();
            currentSecondOriginal = OmegaValue.zero();
            currentSecondActual = OmegaValue.zero();
            secondTickCounter = 0;

            if (logToChat && !lastSecondReceived.isZero()) {
                sendRateMessageToSubscribers();
            }
        }

        syncCooldown++;
        if (syncCooldown >= 5) {
            syncCooldown = 0;
            sendSyncToClients();
        }
    }

    private void sendRateMessageToSubscribers() {
        if (!logToChat) return;
        if (level == null || level.isClientSide()) return;
        if (lastSecondReceived.isZero()) return;

        String rateDisplay = lastSecondReceived.toDisplayString() + "/s";
        String actualDisplay = lastSecondActual.toDisplayString() + "/s";

        if (autoMode) {
            addLog(Component.translatable("eecore.consumer.log.receive_rate",
                    rateDisplay, lastReceivedTier.getShortName()));
        } else {
            String setTier = manualTier.getShortName();
            String actualTier = lastReceivedTier.getShortName();
            if (setTier.equals(actualTier)) {
                addLog(Component.translatable("eecore.consumer.log.receive_rate_manual",
                        rateDisplay, actualDisplay, setTier, actualTier));
            } else {
                String pctDisplay = "0%";
                if (!lastSecondOriginal.isZero()) {
                    long pct = lastSecondLoss.toBigInteger().multiply(BigInteger.valueOf(100))
                            .divide(lastSecondOriginal.toBigInteger()).longValue();
                    pctDisplay = Math.min(100, pct) + "%";
                }
                addLog(Component.literal("")
                        .append(Component.translatable("eecore.consumer.log.receive_rate_manual_mismatch",
                                rateDisplay, actualDisplay, setTier, actualTier, pctDisplay))
                        .append(Component.literal(" [!]").withStyle(net.minecraft.ChatFormatting.RED))
                        .append(Component.literal(")").withStyle(net.minecraft.ChatFormatting.WHITE)));
            }
        }
    }

    private void applyPendingState() {
        BlockPos pos = worldPosition;
        Boolean clear = pendingClear.remove(pos);
        Boolean logToChat = pendingLogToChat.remove(pos);
        Boolean autoMode = pendingAutoMode.remove(pos);
        VoltageTier manualTier = pendingManualTier.remove(pos);

        if (clear != null && clear) {
            storage.setEnergy(OmegaValue.zero());
            totalReceived = OmegaValue.zero();
            lastPacket = null;
            lastSecondReceived = OmegaValue.zero();
            currentSecondReceived = OmegaValue.zero();
            logs.clear();
            addLog(Component.translatable("eecore.consumer.log.cleared"));
            setChanged();
            sendSyncToClients();
        }

        if (logToChat != null) {
            this.logToChat = logToChat;
            addLog(Component.translatable(logToChat ? "eecore.consumer.log.chat_log_enabled" : "eecore.consumer.log.chat_log_disabled"));
            setChanged();
            sendSyncToClients();
        }

        if (autoMode != null) {
            this.autoMode = autoMode;
            addLog(Component.translatable(
                    autoMode ? "eecore.consumer.log.mode_auto" : "eecore.consumer.log.mode_manual"));
            setChanged();
            sendSyncToClients();
        }

        if (manualTier != null) {
            this.manualTier = manualTier;
            addLog(Component.translatable("eecore.consumer.log.manual_tier_set", manualTier.getShortName()));
            setChanged();
            sendSyncToClients();
        }
    }

    @Override
    public EnergyPacket receivePacket(EnergyPacket packet, boolean simulate) {
        if (packet == null || packet.isEmpty()) return null;

        if (packet.getTier().ordinal() > currentReceivedTier.ordinal()) {
            currentReceivedTier = packet.getTier();
        }

        VoltageTier targetTier = autoMode ? packet.getTier() : manualTier;

        EnergyPacket packetToStore = packet;
        OmegaValue stepLoss = OmegaValue.zero();
        if (packet.getTier().ordinal() > targetTier.ordinal()) {
            packetToStore = packet.stepDownTo(targetTier);
            if (packetToStore.isEmpty()) return null;
            stepLoss = packet.getEnergy().subtract(packetToStore.getEnergy());
        }

        EnergyPacket accepted = storage.receivePacket(packetToStore, simulate);
        if (accepted == null || accepted.isEmpty()) return null;
        if (!simulate) {
            this.lastPacket = accepted;
            this.lastReceivedTier = packet.getTier();
            this.totalReceived = this.totalReceived.add(accepted.getEnergy());
            OmegaValue acceptedEnergy = accepted.getEnergy();
            this.currentSecondReceived = this.currentSecondReceived.add(acceptedEnergy);
            this.currentSecondLoss = this.currentSecondLoss.add(stepLoss);
            this.currentSecondOriginal = this.currentSecondOriginal.add(packet.getEnergy());
            this.currentSecondActual = this.currentSecondActual.add(packetToStore.getEnergy());

            setChanged();
            if (level != null && !level.isClientSide()) {
                level.setBlock(worldPosition, getBlockState().setValue(CreativeConsumerBlock.LIT, true), 3);
                level.scheduleTick(worldPosition, getBlockState().getBlock(), 20);
                sendParticles();
                syncCooldown = 0;
                sendSyncToClients();
            }
        }
        return accepted;
    }

    private void sendParticles() {
        if (level == null || level.isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) level;
        serverLevel.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5,
                10,
                0.3, 0.3, 0.3,
                0.1
        );
    }

    private void sendSyncToClients() {
        if (level == null || level.isClientSide()) return;

        Set<UUID> currentSubscribers;
        synchronized (subscribers) {
            if (subscribers.isEmpty()) return;
            currentSubscribers = new HashSet<>(subscribers);
        }

        var packet = new SyncConsumerPacket(
                worldPosition,
                storage.getEnergyStored().toBigInteger(),
                totalReceived.toBigInteger(),
                logToChat
        );

        for (UUID uuid : currentSubscribers) {
            var player = ((ServerLevel) level).getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    public void updateFromSync(SyncConsumerPacket packet) {
        this.totalReceived = OmegaValue.of(packet.totalReceived());
        this.storage.setEnergy(OmegaValue.of(packet.storedEnergy()));
        this.logToChat = packet.logToChat();
        setChanged();
        if (level != null && level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void clearAll() {
        OmegaValue cleared = storage.getEnergyStored();
        addLog(Component.translatable("eecore.consumer.log.clear_amount", cleared.toDisplayString()));
        pendingClear.put(worldPosition, true);
        setChanged();
    }

    public void setLogToChat(boolean logToChat) {
        this.logToChat = logToChat;
        addLog(Component.translatable(logToChat ? "eecore.consumer.log.chat_log_enabled" : "eecore.consumer.log.chat_log_disabled"));
        pendingLogToChat.put(worldPosition, logToChat);
        setChanged();
    }

    public void toggleAutoMode() {
        this.autoMode = !this.autoMode;
        addLog(Component.translatable(
                autoMode ? "eecore.consumer.log.mode_auto" : "eecore.consumer.log.mode_manual"));
        pendingAutoMode.put(worldPosition, this.autoMode);
        setChanged();
        sendSyncToClients();
    }

    public void setManualTier(VoltageTier tier) {
        if (tier == null || tier == this.manualTier) return;
        this.manualTier = tier;
        addLog(Component.translatable("eecore.consumer.log.manual_tier_set", tier.getShortName()));
        pendingManualTier.put(worldPosition, tier);
        setChanged();
        sendSyncToClients();
    }

    public boolean isAutoMode() { return autoMode; }
    public VoltageTier getManualTier() { return manualTier; }
    public VoltageTier getCurrentReceivedTier() { return currentReceivedTier; }

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
        return Component.translatable("eecore.consumer.chat_prefix").withStyle(net.minecraft.ChatFormatting.YELLOW);
    }

    @Override
    public EnergyPacket extractPacket(VoltageTier requestedTier, boolean simulate) { return null; }

    @Override
    public OmegaValue receiveEnergy(OmegaValue amount, boolean simulate) {
        if (amount == null || amount.isZero()) return OmegaValue.zero();
        VoltageTier sourceTier = VoltageTier.fromVoltage(amount);
        if (sourceTier == null) return OmegaValue.zero();
        EnergyPacket packet = new EnergyPacket(sourceTier, 1, amount);
        EnergyPacket accepted = receivePacket(packet, simulate);
        return accepted != null ? accepted.getEnergy() : OmegaValue.zero();
    }

    @Override
    public OmegaValue extractEnergy(OmegaValue amount, boolean simulate) { return OmegaValue.zero(); }

    @Override
    public OmegaValue getEnergyStored() { return storage.getEnergyStored(); }

    @Override
    public OmegaValue getEnergyStored(VoltageTier tier) { return storage.getEnergyStored(tier); }

    @Override
    public OmegaValue getCapacity() { return storage.getCapacity(); }

    @Override
    public OmegaValue getMaxInput() { return storage.getMaxInput(); }

    @Override
    public OmegaValue getMaxOutput() { return storage.getMaxOutput(); }

    @Override
    public VoltageTier getTier() { return storage.getTier(); }

    public OmegaValue getStoredEnergy() { return storage.getEnergyStored(); }
    public OmegaValue getTotalReceived() { return totalReceived; }
    public EnergyPacket getLastReceivedPacket() { return lastPacket; }
    public OmegaValue getLastSecondReceived() { return lastSecondReceived; }
    public List<String> getLogMessages() { return new ArrayList<>(logs); }
    public OmegaStorage getStorage() { return storage; }
    public boolean isLogToChat() { return logToChat; }

    @Override
    public Component getDisplayName() { return Component.translatable("eecore.consumer.title"); }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        addSubscriber(player);
        return new CreativeConsumerMenu(id, inv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider prov) {
        super.saveAdditional(tag, prov);
        storage.saveToNBT(tag);
        tag.putString("totalReceived", totalReceived.toBigInteger().toString());
        tag.putBoolean("logToChat", logToChat);
        tag.putBoolean("autoMode", autoMode);
        tag.putString("manualTier", manualTier.getShortName());
        tag.putString("currentReceivedTier", currentReceivedTier.getShortName());
        tag.putString("lastReceivedTier", lastReceivedTier.getShortName());
        if (lastPacket != null) {
            tag.putString("lastTier", lastPacket.getTier().getShortName());
            tag.putString("lastEnergy", lastPacket.getEnergy().toBigInteger().toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider prov) {
        super.loadAdditional(tag, prov);
        storage.loadFromNBT(tag);
        totalReceived = OmegaValue.of(tag.getString("totalReceived"));
        logToChat = tag.getBoolean("logToChat");
        autoMode = tag.getBoolean("autoMode");
        manualTier = VoltageTier.fromShortName(tag.getString("manualTier"));
        currentReceivedTier = VoltageTier.fromShortName(tag.getString("currentReceivedTier"));
        lastReceivedTier = tag.contains("lastReceivedTier")
                ? VoltageTier.fromShortName(tag.getString("lastReceivedTier"))
                : VoltageTier.ELV;
        if (tag.contains("lastTier")) {
            VoltageTier tier = VoltageTier.fromShortName(tag.getString("lastTier"));
            lastPacket = new EnergyPacket(tier, 1, OmegaValue.of(tag.getString("lastEnergy")));
        } else {
            lastPacket = null;
        }
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
