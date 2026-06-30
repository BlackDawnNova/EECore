package com.endlessepoch.core.nova.network.transmitter;

import com.endlessepoch.core.registry.BlockEntities;
import com.endlessepoch.core.api.energy.*;
import com.endlessepoch.core.api.field.INovaNode;
import com.endlessepoch.core.api.field.NodeType;
import com.endlessepoch.core.api.multiblock.IMultiBlockController;
import com.endlessepoch.core.api.multiblock.MultiBlockFormHandler;
import com.endlessepoch.core.api.tier.VoltageTier;
import com.endlessepoch.core.nova.network.node.IRangeProvider;
import com.endlessepoch.core.nova.network.node.NovaNodeRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.math.BigInteger;
import java.util.UUID;

/**
 * 发射器方块实体 —— NovaNet 节点 + Ω 能量存储。
 * Transmitter (sender) block entity — NovaNet node + Ω energy storage.
 * <p>
 * 通过 {@link TransmitterRangeScanner} 从附近的发电机收集能量，
 * 缓存在 OmegaStorage 中，并通过激光传输到绑定的接收器。
 * 暴露 {@link IOmegaEnergyStorage} 以供跨模组 Capability 访问。
 * <p>
 * Collects energy from nearby generators via {@link TransmitterRangeScanner},
 * buffers it in an OmegaStorage, and transmits to bound receivers via laser.
 * Exposes {@link IOmegaEnergyStorage} for cross-mod Capability access.
 */
public class TransmitterBlockEntity extends BlockEntity implements INovaNode, IMultiBlockController, IOmegaEnergyStorage {

    private UUID nodeId = UUID.randomUUID();
    private VoltageTier tier = VoltageTier.LV;
    private int range;
    private TransmitterEnergyBuffer buffer;
    private IRangeProvider rangeProvider = IRangeProvider.DEFAULT;

    private boolean formed;
    private UUID teamId;
    private UUID ownerUUID;
    private String ownerName;

    public TransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.TEST_TRANSMITTER.get(), pos, state);
        this.tier = VoltageTier.LV;
        this.range = rangeProvider.getRange(tier);
        this.buffer = new TransmitterEnergyBuffer(tier);
    }

    public void serverTick() {
        if (level == null || level.isClientSide() || !formed) return;

        TransmitterRangeScanner.scanAndPull(level, worldPosition, range, tier, buffer, teamId);
    }

    @Override
    public UUID getNodeId() { return nodeId; }

    @Override
    public boolean isFormed() { return formed; }

    @Override
    public void onMultiblockFormed() {
        formed = true;
        NovaNodeRegistration.register(this);
    }

    @Override
    public void onMultiblockBroken() {
        formed = false;
        NovaNodeRegistration.unregister(this);
        MultiBlockFormHandler.notifyBreak(this, worldPosition, level);
    }

    @Override
    public UUID getOwnerUUID() { return ownerUUID; }

    @Override
    public String getOwnerName() { return ownerName; }

    @Override
    public void stampOwner(java.util.UUID owner, String name) {
        this.ownerUUID = owner;
        this.ownerName = name;
        setChanged();
    }

    @Override
    public BlockPos getBlockPos() { return worldPosition; }

    @Override
    public NodeType getNodeType() { return NodeType.TRANSMITTER; }

    @Override
    public VoltageTier getTier() { return tier; }

    @Override
    public int getRange() { return range; }

    @Override
    public long getBufferEnergy() { return buffer.getStored().toLong(); }

    @Override
    public long getBufferCapacity() { return buffer.getCapacity().toLong(); }

    @Override
    public UUID getTeamId() { return teamId; }

    public TransmitterEnergyBuffer getBuffer() { return buffer; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; setChanged(); }
    public void setRangeProvider(IRangeProvider provider) {
        this.rangeProvider = provider;
        this.range = provider.getRange(tier);
    }
    public boolean hasEnergy() { return buffer != null && buffer.hasEnergy(); }
    public void setTier(VoltageTier tier) {
        this.tier = tier;
        this.range = rangeProvider.getRange(tier);
    }

    @Override
    public EnergyPacket receivePacket(EnergyPacket packet, boolean simulate) {
        return buffer.getStorage().receivePacket(packet, simulate);
    }

    @Override
    public EnergyPacket extractPacket(VoltageTier requestedTier, boolean simulate) {
        return buffer.getStorage().extractPacket(requestedTier, simulate);
    }

    @Override
    public OmegaValue receiveEnergy(OmegaValue amount, boolean simulate) {
        return buffer.getStorage().receiveEnergy(amount, simulate);
    }

    @Override
    public OmegaValue extractEnergy(OmegaValue amount, boolean simulate) {
        return buffer.getStorage().extractEnergy(amount, simulate);
    }

    @Override
    public OmegaValue getEnergyStored() { return buffer.getStored(); }

    @Override
    public OmegaValue getEnergyStored(VoltageTier t) { return buffer.getStorage().getEnergyStored(t); }

    @Override
    public OmegaValue getCapacity() { return buffer.getCapacity(); }

    @Override
    public OmegaValue getMaxInput() { return buffer.getStorage().getMaxInput(); }

    @Override
    public OmegaValue getMaxOutput() { return buffer.getStorage().getMaxOutput(); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putUUID("nodeId", nodeId);
        tag.putString("tier", tier.getShortName());
        tag.putBoolean("formed", formed);
        if (ownerUUID != null) tag.putUUID("ownerUUID", ownerUUID);
        if (ownerName != null) tag.putString("ownerName", ownerName);
        if (teamId != null) tag.putUUID("teamId", teamId);
        if (buffer != null) buffer.saveToNBT(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.hasUUID("nodeId")) nodeId = tag.getUUID("nodeId");
        tier = VoltageTier.fromShortName(tag.getString("tier"));
        range = rangeProvider.getRange(tier);
        buffer = new TransmitterEnergyBuffer(tier);
        buffer.loadFromNBT(tag);
        formed = tag.getBoolean("formed");
        if (tag.hasUUID("ownerUUID")) ownerUUID = tag.getUUID("ownerUUID");
        ownerName = tag.getString("ownerName");
        if (tag.hasUUID("teamId")) teamId = tag.getUUID("teamId");
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (formed && level != null && !level.isClientSide()) {
            onMultiblockBroken();
        }
    }
}
