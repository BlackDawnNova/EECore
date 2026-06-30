package com.endlessepoch.core.nova.network.transmitter;

import com.endlessepoch.core.api.energy.*;
import com.endlessepoch.core.api.tier.VoltageTier;
import net.minecraft.nbt.CompoundTag;

/**
 * Energy buffer for a transmitter node.
 * Wraps OmegaStorage and adds network-aware convenience methods.
 */
public class TransmitterEnergyBuffer {

    private final OmegaStorage storage;

    /** Create a buffer sized for the transmitter's voltage tier. */
    public TransmitterEnergyBuffer(VoltageTier tier) {
        // Buffer size scales with tier: LV=1kΩ, MV=10kΩ, HV=100kΩ, ...
        long capacity = 1000L << (tier.ordinal() * 2);
        this.storage = new OmegaStorage(capacity, capacity, 0, tier);
    }

    /** Receive packet into buffer. Returns accepted amount (Ω). */
    public OmegaValue receive(EnergyPacket packet, boolean simulate) {
        EnergyPacket accepted = storage.receivePacket(packet, simulate);
        return accepted != null ? accepted.getEnergy() : OmegaValue.zero();
    }

    /** Extract a packet for transmission. */
    public EnergyPacket extract(VoltageTier tier, boolean simulate) {
        return storage.extractPacket(tier, simulate);
    }

    /** Current stored energy. */
    public OmegaValue getStored() { return storage.getEnergyStored(); }

    /** Maximum buffer capacity. */
    public OmegaValue getCapacity() { return storage.getCapacity(); }

    /** True if buffer is full. */
    public boolean isFull() {
        return storage.getEnergyStored().compareTo(storage.getCapacity()) >= 0;
    }

    /** True if buffer has any energy. */
    public boolean hasEnergy() {
        return !storage.getEnergyStored().isZero();
    }

    // NBT
    public void saveToNBT(CompoundTag tag) { storage.saveToNBT(tag); }
    public void loadFromNBT(CompoundTag tag) { storage.loadFromNBT(tag); }

    public OmegaStorage getStorage() { return storage; }
}
