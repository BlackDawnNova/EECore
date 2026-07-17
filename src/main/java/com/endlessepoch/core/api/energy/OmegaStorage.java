package com.endlessepoch.core.api.energy;

import com.endlessepoch.core.api.tier.VoltageTier;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Full implementation of {@link IOmegaEnergyStorage} with per-tier tracking, NBT persistence,
 * automatic voltage step-down, and {@link EnergyTransferEvent} firing.
 * 完整实现 IOmegaEnergyStorage，分层追踪+NBT持久化+自动降压+事件通知。
 *
 * @implNote Thread-safe via {@code ReentrantReadWriteLock}. All reads acquire shared lock,
 *           all writes acquire exclusive lock. Events are collected inside the lock and posted afterward.
 */
public class OmegaStorage implements IOmegaEnergyStorage {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<EnergyTransferEvent> pendingEvents = new ArrayList<>(2);

    private final Map<VoltageTier, OmegaValue> tieredEnergy = new EnumMap<>(VoltageTier.class);
    private final BigInteger capacity;
    private final BigInteger maxInput;
    private final BigInteger maxOutput;
    private final VoltageTier tier;
    private final int amperage;
    private OmegaValue energy = OmegaValue.zero();

    public OmegaStorage(long capacity, long maxIO, VoltageTier tier) {
        this(capacity, maxIO, maxIO, tier);
    }

    public OmegaStorage(long capacity, long maxInput, long maxOutput, VoltageTier tier) {
        this(BigInteger.valueOf(capacity), BigInteger.valueOf(maxInput), BigInteger.valueOf(maxOutput), tier, 1);
    }

    public OmegaStorage(BigInteger capacity, BigInteger maxInput, BigInteger maxOutput, VoltageTier tier) {
        this(capacity, maxInput, maxOutput, tier, 1);
    }

    /** Multi-amp version — gate scales with min(hatchAmps, packetAmps). / 多A版——限速随安培缩放。 */
    public OmegaStorage(long capacity, long maxInput, long maxOutput, VoltageTier tier, int amperage) {
        this(BigInteger.valueOf(capacity), BigInteger.valueOf(maxInput), BigInteger.valueOf(maxOutput), tier, amperage);
    }

    public OmegaStorage(BigInteger capacity, BigInteger maxInput, BigInteger maxOutput, VoltageTier tier, int amperage) {
        this.capacity = Objects.requireNonNull(capacity, "capacity must not be null");
        this.maxInput = Objects.requireNonNull(maxInput, "maxInput must not be null");
        this.maxOutput = Objects.requireNonNull(maxOutput, "maxOutput must not be null");
        this.tier = Objects.requireNonNull(tier, "tier must not be null");
        this.amperage = amperage;
        for (VoltageTier vt : VoltageTier.values()) tieredEnergy.put(vt, OmegaValue.zero());
        this.energy = OmegaValue.zero();
    }

    public OmegaStorage(OmegaValue capacity, OmegaValue maxInput, OmegaValue maxOutput, VoltageTier tier) {
        this.capacity = Objects.requireNonNull(capacity, "capacity must not be null").toBigInteger();
        this.maxInput = Objects.requireNonNull(maxInput, "maxInput must not be null").toBigInteger();
        this.maxOutput = Objects.requireNonNull(maxOutput, "maxOutput must not be null").toBigInteger();
        this.amperage = 1;
        this.tier = Objects.requireNonNull(tier, "tier must not be null");
        for (VoltageTier vt : VoltageTier.values()) tieredEnergy.put(vt, OmegaValue.zero());
        this.energy = OmegaValue.zero();
    }

    // === Thread-safe write helpers / 线程安全写辅助 ===

    private void queueEvent(EnergyTransferEvent.Phase phase, EnergyPacket packet, OmegaValue amount) {
        if (amount != null && !amount.isZero())
            pendingEvents.add(new EnergyTransferEvent(phase, this, packet, amount));
    }

    /** Flush and clear pending energy-transfer events. Caller should post them to EVENT_BUS. / 刷新并清理待发事件。 */
    public List<EnergyTransferEvent> flushPendingEvents() {
        lock.writeLock().lock();
        try {
            if (pendingEvents.isEmpty()) return List.of();
            List<EnergyTransferEvent> copy = List.copyOf(pendingEvents);
            pendingEvents.clear();
            return copy;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // === Writes (exclusive lock) / 写操作 ===

    public void setEnergy(OmegaValue energy) {
        lock.writeLock().lock();
        try {
            this.energy = energy;
            tieredEnergy.put(VoltageTier.ELV, energy);
            pendingEvents.clear(); // discard stale events on external set / 外部赋值时丢弃过期事件
        } finally { lock.writeLock().unlock(); }
    }

    public void saveToNBT(CompoundTag tag) {
        lock.readLock().lock();
        try {
            tag.putString("energy", energy.toBigInteger().toString());
            tag.putString("capacity", capacity.toString());
            tag.putString("maxInput", maxInput.toString());
            tag.putString("maxOutput", maxOutput.toString());
            ListTag list = new ListTag();
            for (Map.Entry<VoltageTier, OmegaValue> entry : tieredEnergy.entrySet()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString("tier", entry.getKey().getShortName());
                entryTag.putString("value", entry.getValue().toBigInteger().toString());
                list.add(entryTag);
            }
            tag.put("tieredEnergy", list);
        } finally { lock.readLock().unlock(); }
    }

    public void loadFromNBT(CompoundTag tag) {
        lock.writeLock().lock();
        try {
            for (VoltageTier vt : VoltageTier.values()) tieredEnergy.put(vt, OmegaValue.zero());
            pendingEvents.clear();

            if (tag.contains("tieredEnergy", Tag.TAG_LIST)) {
                ListTag list = tag.getList("tieredEnergy", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag entry = list.getCompound(i);
                    String tierName = entry.getString("tier");
                    VoltageTier vt = VoltageTier.fromShortName(tierName);
                    OmegaValue val = OmegaValue.zero();
                    if (entry.contains("value", Tag.TAG_STRING)) {
                        String str = entry.getString("value");
                        if (str != null && !str.isEmpty()) {
                            try { val = OmegaValue.of(new BigInteger(str)); }
                            catch (NumberFormatException e) {
                                // Corrupt save data — keep zero but leave a trace / 存档数据损坏——保零值但要留痕
                                com.endlessepoch.core.EECore.LOGGER.warn(
                                        "[Omega] corrupt tiered energy value in NBT: '{}', resetting to 0", str);
                            }
                        }
                    } else if (entry.contains("value", Tag.TAG_LONG)) {
                        val = OmegaValue.of(entry.getLong("value"));
                    }
                    tieredEnergy.put(vt, val);
                }
            }

            OmegaValue total = OmegaValue.zero();
            if (tag.contains("energy", Tag.TAG_STRING)) {
                String str = tag.getString("energy");
                if (str != null && !str.isEmpty()) {
                    try { total = OmegaValue.of(new BigInteger(str)); }
                    catch (NumberFormatException e) {
                        com.endlessepoch.core.EECore.LOGGER.warn(
                                "[Omega] corrupt total energy in NBT: '{}', resetting to 0", str);
                    }
                }
            } else if (tag.contains("energy", Tag.TAG_LONG)) {
                total = OmegaValue.of(tag.getLong("energy"));
            }
            if (!total.isZero()) tieredEnergy.put(VoltageTier.ELV, total);
            this.energy = computeTotal();
        } finally { lock.writeLock().unlock(); }
    }

    @Override
    public EnergyPacket receivePacket(EnergyPacket packet, boolean simulate) {
        if (packet == null || packet.isEmpty()) return null;
        lock.writeLock().lock();
        try {
            if (!canInput(packet.getTier())) {
                EnergyPacket stepped = packet.stepDownTo(tier);
                if (stepped.isEmpty()) return null;
                return receivePacketLocked(stepped, simulate, packet.getAmperage());
            }
            return receivePacketLocked(packet, simulate, packet.getAmperage());
        } finally { lock.writeLock().unlock(); }
    }

    /** Per-packet gate: min(hatch amps, original packet amps) × hatch tier voltage. / 每包限速：min(仓安培, 原始包安培) × 仓电压。 */
    private BigInteger computeGate(BigInteger origAmps) {
        BigInteger effectiveAmps = origAmps.compareTo(BigInteger.valueOf(amperage)) < 0
                ? origAmps : BigInteger.valueOf(amperage);
        return tier.getMinVoltage().multiply(effectiveAmps);
    }

    private EnergyPacket receivePacketLocked(EnergyPacket packet, boolean simulate, BigInteger origAmps) {
        OmegaValue available = OmegaValue.of(capacity).subtract(energy);
        if (available.isZero()) return null;

        BigInteger packetEnergy = packet.getEnergy().toBigInteger();
        BigInteger gate = computeGate(origAmps);
        if (gate.compareTo(BigInteger.ZERO) > 0) {
            if (packetEnergy.compareTo(gate) > 0) {
                OmegaValue gateVal = OmegaValue.of(gate);
                BigInteger limitedEnergy = OmegaValue.min(available, gateVal).toBigInteger();
                if (!simulate) {
                    OmegaValue toStore = OmegaValue.of(limitedEnergy);
                    tieredEnergy.put(packet.getTier(), tieredEnergy.getOrDefault(packet.getTier(), OmegaValue.zero()).add(toStore));
                    energy = energy.add(toStore);
                    queueEvent(EnergyTransferEvent.Phase.RECEIVE, packet, toStore);
                }
                BigInteger newAmps = limitedEnergy.divide(packet.getVoltage());
                if (newAmps.signum() < 1) newAmps = BigInteger.ONE;
                return new EnergyPacket(packet.getTier(), newAmps, OmegaValue.of(limitedEnergy));
            }
        }

        OmegaValue toStore = OmegaValue.of(packetEnergy.min(available.toBigInteger()));
        if (!simulate) {
            tieredEnergy.put(packet.getTier(), tieredEnergy.getOrDefault(packet.getTier(), OmegaValue.zero()).add(toStore));
            energy = energy.add(toStore);
            queueEvent(EnergyTransferEvent.Phase.RECEIVE, packet, toStore);
        }
        BigInteger actualAmps = toStore.toBigInteger().divide(packet.getVoltage());
        if (actualAmps.signum() < 1) actualAmps = BigInteger.ONE;
        return new EnergyPacket(packet.getTier(), actualAmps, toStore);
    }

    @Override
    public EnergyPacket extractPacket(VoltageTier requestedTier, boolean simulate) {
        if (requestedTier == null) return null;
        lock.writeLock().lock();
        try {
            OmegaValue available = tieredEnergy.getOrDefault(requestedTier, OmegaValue.zero());
            if (!available.isZero()) {
                OmegaValue toExtract = maxOutput.compareTo(BigInteger.ZERO) > 0
                        ? OmegaValue.of(available.toBigInteger().min(maxOutput)) : available;
                if (!simulate) {
                    tieredEnergy.put(requestedTier, available.subtract(toExtract));
                    energy = energy.subtract(toExtract);
                    EnergyPacket pkt = new EnergyPacket(requestedTier, 1, toExtract);
                    queueEvent(EnergyTransferEvent.Phase.EXTRACT, pkt, toExtract);
                }
                BigInteger amps = toExtract.toBigInteger().divide(requestedTier.getMinVoltage());
                if (amps.signum() < 1) amps = BigInteger.ONE;
                return new EnergyPacket(requestedTier, amps, toExtract);
            }

            for (VoltageTier higher : VoltageTier.values()) {
                if (higher.ordinal() > requestedTier.ordinal()) {
                    OmegaValue higherEnergy = tieredEnergy.getOrDefault(higher, OmegaValue.zero());
                    if (!higherEnergy.isZero()) {
                        EnergyPacket packet = new EnergyPacket(higher, 1, higherEnergy);
                        EnergyPacket stepped = packet.stepDownTo(requestedTier);
                        if (!stepped.isEmpty()) {
                            if (!simulate) {
                                tieredEnergy.put(higher, OmegaValue.zero());
                                energy = energy.subtract(higherEnergy);
                                queueEvent(EnergyTransferEvent.Phase.EXTRACT, stepped, stepped.getEnergy());
                            }
                            return stepped;
                        }
                    }
                }
            }
            return null;
        } finally { lock.writeLock().unlock(); }
    }

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
    public OmegaValue extractEnergy(OmegaValue amount, boolean simulate) {
        if (amount == null || amount.isZero()) return OmegaValue.zero();
        lock.writeLock().lock();
        try {
            BigInteger limit = maxOutput.compareTo(BigInteger.ZERO) > 0
                    ? amount.toBigInteger().min(maxOutput) : amount.toBigInteger();
            OmegaValue remaining = OmegaValue.of(limit);
            OmegaValue extracted = OmegaValue.zero();

            VoltageTier current = VoltageTier.QV;
            while (!remaining.isZero()) {
                OmegaValue avail = tieredEnergy.getOrDefault(current, OmegaValue.zero());
                if (!avail.isZero()) {
                    OmegaValue toExtract = remaining.compareTo(avail) < 0 ? remaining : avail;
                    if (!simulate) {
                        tieredEnergy.put(current, avail.subtract(toExtract));
                        energy = energy.subtract(toExtract);
                    }
                    extracted = extracted.add(toExtract);
                    remaining = remaining.subtract(toExtract);
                }
                if (current == VoltageTier.ELV) break;
                current = current.prev();
            }
            return extracted;
        } finally { lock.writeLock().unlock(); }
    }

    public void setTieredEnergy(VoltageTier tier, OmegaValue amount) {
        lock.writeLock().lock();
        try {
            tieredEnergy.put(tier, amount);
            this.energy = computeTotal();
        } finally { lock.writeLock().unlock(); }
    }

    // === Reads (shared lock) / 读操作 ===

    @Override
    public OmegaValue getEnergyStored() {
        lock.readLock().lock();
        try { return energy; } finally { lock.readLock().unlock(); }
    }

    @Override
    public OmegaValue getEnergyStored(VoltageTier tier) {
        lock.readLock().lock();
        try { return tieredEnergy.getOrDefault(tier, OmegaValue.zero()); } finally { lock.readLock().unlock(); }
    }

    @Override
    public OmegaValue getCapacity() {
        lock.readLock().lock();
        try { return OmegaValue.of(capacity); } finally { lock.readLock().unlock(); }
    }

    @Override
    public OmegaValue getMaxInput() {
        lock.readLock().lock();
        try { return OmegaValue.of(maxInput); } finally { lock.readLock().unlock(); }
    }

    @Override
    public OmegaValue getMaxOutput() {
        lock.readLock().lock();
        try { return OmegaValue.of(maxOutput); } finally { lock.readLock().unlock(); }
    }

    @Override
    public VoltageTier getTier() {
        lock.readLock().lock();
        try { return tier; } finally { lock.readLock().unlock(); }
    }

    // === Internal / 内部方法 ===

    private OmegaValue computeTotal() {
        OmegaValue total = OmegaValue.zero();
        for (OmegaValue v : tieredEnergy.values()) total = total.add(v);
        return total;
    }
}
