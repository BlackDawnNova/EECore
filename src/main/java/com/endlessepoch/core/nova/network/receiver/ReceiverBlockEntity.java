package com.endlessepoch.core.nova.network.receiver;

import com.endlessepoch.core.nova.network.transmitter.TransmitterEnergyBuffer;
import com.endlessepoch.core.api.field.INovaNode;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * Receiver block entity.
 * <p>
 * Holds energy in a buffer and distributes it to nearby machines
 * that implement {@link IOmegaEnergyStorage}.
 */
public class ReceiverBlockEntity extends BlockEntity implements INovaNode, IMultiBlockController {

    private UUID nodeId = UUID.randomUUID();
    private VoltageTier tier = VoltageTier.LV;
    private int range;
    private TransmitterEnergyBuffer buffer;
    private IRangeProvider rangeProvider = IRangeProvider.DEFAULT;

    private boolean formed;
    private UUID teamId;
    private UUID ownerUUID;
    private String ownerName;

    public ReceiverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.tier = VoltageTier.LV;
        this.range = rangeProvider.getRange(tier);
        this.buffer = new TransmitterEnergyBuffer(tier);
    }

    public void init(VoltageTier tier) {
        this.tier = tier;
        this.range = rangeProvider.getRange(tier);
        this.buffer = new TransmitterEnergyBuffer(tier);
    }

    // ===== Server tick =====
    public void serverTick() {
        if (level == null || level.isClientSide() || !formed || !buffer.hasEnergy()) return;

        // Distribute energy to nearby machines
        ReceiverDistributor.distribute(level, worldPosition, range, tier, buffer);
    }

    // ===== IMultiBlockController =====

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

    // ===== INovaNode =====

    @Override
    public BlockPos getBlockPos() { return worldPosition; }

    @Override
    public NodeType getNodeType() { return NodeType.RECEIVER; }

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

    // ===== Getter/setters =====
    public TransmitterEnergyBuffer getBuffer() { return buffer; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; setChanged(); }

    // ===== NBT =====

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
