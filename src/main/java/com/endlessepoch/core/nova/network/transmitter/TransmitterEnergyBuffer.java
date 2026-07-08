package com.endlessepoch.core.nova.network.transmitter;

import com.endlessepoch.core.api.energy.*;
import com.endlessepoch.core.api.tier.VoltageTier;
import net.minecraft.nbt.CompoundTag;

/**
 * Energy buffer for a transmitter node.
 * <p>
 * 发射器节点的能量缓冲区。
 * Wraps OmegaStorage and adds network-aware convenience methods.
 * 包装 OmegaStorage 并添加网络感知的便捷方法。
 */
public class TransmitterEnergyBuffer {

    private final OmegaStorage storage;

    /** Create a buffer sized for the transmitter's voltage tier. / 创建一个适配发射器电压等级的缓冲区。 */
    public TransmitterEnergyBuffer(VoltageTier tier) {
        long capacity = 1000L << (tier.ordinal() * 2);
        this.storage = new OmegaStorage(capacity, capacity, 0, tier);
    }

    /** Receive packet into buffer. Returns accepted amount (Omega). / 接收数据包到缓冲区。返回接受量（Omega）。 */
    public OmegaValue receive(EnergyPacket packet, boolean simulate) {
        EnergyPacket accepted = storage.receivePacket(packet, simulate);
        return accepted != null ? accepted.getEnergy() : OmegaValue.zero();
    }

    /** Extract a packet for transmission. / 提取用于传输的数据包。 */
    public EnergyPacket extract(VoltageTier tier, boolean simulate) {
        return storage.extractPacket(tier, simulate);
    }

    /** Current stored energy. / 当前存储的能量。 */
    public OmegaValue getStored() { return storage.getEnergyStored(); }

    /** Maximum buffer capacity. / 最大缓冲区容量。 */
    public OmegaValue getCapacity() { return storage.getCapacity(); }

    /** True if buffer is full. / 缓冲区是否已满。 */
    public boolean isFull() {
        return storage.getEnergyStored().compareTo(storage.getCapacity()) >= 0;
    }

    /** True if buffer has any energy. / 缓冲区是否有能量。 */
    public boolean hasEnergy() {
        return !storage.getEnergyStored().isZero();
    }

    public void saveToNBT(CompoundTag tag) { storage.saveToNBT(tag); }
    public void loadFromNBT(CompoundTag tag) { storage.loadFromNBT(tag); }

    public OmegaStorage getStorage() { return storage; }
}
