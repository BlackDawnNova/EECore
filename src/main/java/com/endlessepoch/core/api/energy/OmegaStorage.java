package com.endlessepoch.core.api.energy;

import com.endlessepoch.core.api.tier.VoltageTier;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Full implementation of {@link IOmegaEnergyStorage}.
 * <p>
 * Provides per-tier energy tracking, NBT persistence, automatic voltage step-down,
 * and {@link EnergyTransferEvent} firing on every receive/extract.
 * <p>
 * 完整实现 IOmegaEnergyStorage，提供分层能量追踪、NBT 持久化、自动降压，收发时触发事件。
 *
 * <pre>{@code
 * // Via MachineSpec builder:
 * OmegaStorage storage = MachineSpec.builder(VoltageTier.MV)
 *         .capacity(10_000).maxIO(128).build().createStorage();
 *
 * // Direct construction:
 * OmegaStorage storage = new OmegaStorage(10_000, 128, 128, VoltageTier.MV);
 * }</pre>
 */
public class OmegaStorage implements IOmegaEnergyStorage {
    private final Map<VoltageTier, OmegaValue> tieredEnergy = new EnumMap<>(VoltageTier.class);
    private final BigInteger capacity;
    private final BigInteger maxInput;
    private final BigInteger maxOutput;
    private final VoltageTier tier;
    private OmegaValue energy = OmegaValue.zero();

    public OmegaStorage(long capacity, long maxIO, VoltageTier tier) {
        this(BigInteger.valueOf(capacity), BigInteger.valueOf(maxIO), BigInteger.valueOf(maxIO), tier);
    }

    public OmegaStorage(long capacity, long maxInput, long maxOutput, VoltageTier tier) {
        this(BigInteger.valueOf(capacity), BigInteger.valueOf(maxInput), BigInteger.valueOf(maxOutput), tier);
    }

    public OmegaStorage(BigInteger capacity, BigInteger maxInput, BigInteger maxOutput, VoltageTier tier) {
        this.capacity = Objects.requireNonNull(capacity, "capacity must not be null");
        this.maxInput = Objects.requireNonNull(maxInput, "maxInput must not be null");
        this.maxOutput = Objects.requireNonNull(maxOutput, "maxOutput must not be null");
        this.tier = Objects.requireNonNull(tier, "tier must not be null");
        for (VoltageTier vt : VoltageTier.values()) tieredEnergy.put(vt, OmegaValue.zero());
        this.energy = OmegaValue.zero();
    }

    public OmegaStorage(OmegaValue capacity, OmegaValue maxInput, OmegaValue maxOutput, VoltageTier tier) {
        this.capacity = Objects.requireNonNull(capacity, "capacity must not be null").toBigInteger();
        this.maxInput = Objects.requireNonNull(maxInput, "maxInput must not be null").toBigInteger();
        this.maxOutput = Objects.requireNonNull(maxOutput, "maxOutput must not be null").toBigInteger();
        this.tier = Objects.requireNonNull(tier, "tier must not be null");
        for (VoltageTier vt : VoltageTier.values()) tieredEnergy.put(vt, OmegaValue.zero());
        this.energy = OmegaValue.zero();
    }

    public void setEnergy(OmegaValue energy) {
        this.energy = energy;
        tieredEnergy.put(VoltageTier.ELV, energy);
    }

    public void saveToNBT(CompoundTag tag) {
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
    }

    public void loadFromNBT(CompoundTag tag) {
        for (VoltageTier vt : VoltageTier.values()) tieredEnergy.put(vt, OmegaValue.zero());

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
                        try {
                            val = OmegaValue.of(new BigInteger(str));
                        } catch (NumberFormatException ignored) {}
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
                try {
                    total = OmegaValue.of(new BigInteger(str));
                } catch (NumberFormatException ignored) {}
            }
        } else if (tag.contains("energy", Tag.TAG_LONG)) {
            total = OmegaValue.of(tag.getLong("energy"));
        }
        if (!total.isZero()) {
            tieredEnergy.put(VoltageTier.ELV, total);
        }
        this.energy = computeTotal();
    }

    private void fireTransferEvent(EnergyTransferEvent.Phase phase, EnergyPacket packet, OmegaValue amount) {
        if (amount != null && !amount.isZero()) {
            NeoForge.EVENT_BUS.post(new EnergyTransferEvent(phase, this, packet, amount));
        }
    }

    private OmegaValue computeTotal() {
        OmegaValue total = OmegaValue.zero();
        for (OmegaValue v : tieredEnergy.values()) total = total.add(v);
        return total;
    }

    @Override
    public EnergyPacket receivePacket(EnergyPacket packet, boolean simulate) {
        if (packet == null || packet.isEmpty()) return null;
        if (!canInput(packet.getTier())) {
            EnergyPacket stepped = packet.stepDownTo(tier);
            if (stepped.isEmpty()) return null;
            return receivePacket(stepped, simulate);
        }

        OmegaValue available = OmegaValue.of(capacity).subtract(getEnergyStored());
        if (available.isZero()) return null;

        BigInteger packetEnergy = packet.getEnergy().toBigInteger();
        if (maxInput.compareTo(BigInteger.ZERO) > 0) {
            if (packetEnergy.compareTo(maxInput) > 0) {
                BigInteger limitedEnergy = maxInput.min(available.toBigInteger());
                if (!simulate) {
                    OmegaValue toStore = OmegaValue.of(limitedEnergy);
                    tieredEnergy.put(packet.getTier(), tieredEnergy.getOrDefault(packet.getTier(), OmegaValue.zero()).add(toStore));
                    energy = energy.add(toStore);
                    fireTransferEvent(EnergyTransferEvent.Phase.RECEIVE, packet, toStore);
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
            fireTransferEvent(EnergyTransferEvent.Phase.RECEIVE, packet, toStore);
        }
        BigInteger actualAmps = toStore.toBigInteger().divide(packet.getVoltage());
        if (actualAmps.signum() < 1) actualAmps = BigInteger.ONE;
        return new EnergyPacket(packet.getTier(), actualAmps, toStore);
    }

    @Override
    public EnergyPacket extractPacket(VoltageTier requestedTier, boolean simulate) {
        if (requestedTier == null) return null;

        OmegaValue available = tieredEnergy.getOrDefault(requestedTier, OmegaValue.zero());
        if (!available.isZero()) {
            if (!simulate) {
                tieredEnergy.put(requestedTier, OmegaValue.zero());
                energy = energy.subtract(available);
                EnergyPacket extracted = new EnergyPacket(requestedTier, 1, available);
                fireTransferEvent(EnergyTransferEvent.Phase.EXTRACT, extracted, available);
            }
            BigInteger amps = available.toBigInteger().divide(requestedTier.getMinVoltage());
            if (amps.signum() < 1) amps = BigInteger.ONE;
            return new EnergyPacket(requestedTier, amps, available);
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
                            fireTransferEvent(EnergyTransferEvent.Phase.EXTRACT, stepped, stepped.getEnergy());
                        }
                        return stepped;
                    }
                }
            }
        }
        return null;
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
        if (maxOutput.compareTo(BigInteger.ZERO) > 0 && amount.toBigInteger().compareTo(maxOutput) > 0) {
            amount = OmegaValue.of(maxOutput);
        }

        VoltageTier current = VoltageTier.QV;
        OmegaValue remaining = amount;
        OmegaValue extracted = OmegaValue.zero();

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
                if (extracted.compareTo(amount) >= 0) break;
            }
            if (current == VoltageTier.ELV) break;
            current = current.prev();
        }
        return extracted;
    }

    @Override
    public OmegaValue getEnergyStored() { return energy; }

    @Override
    public OmegaValue getEnergyStored(VoltageTier tier) {
        return tieredEnergy.getOrDefault(tier, OmegaValue.zero());
    }

    @Override
    public OmegaValue getCapacity() { return OmegaValue.of(capacity); }

    @Override
    public OmegaValue getMaxInput() { return OmegaValue.of(maxInput); }

    @Override
    public OmegaValue getMaxOutput() { return OmegaValue.of(maxOutput); }

    @Override
    public VoltageTier getTier() { return tier; }

    public void setTieredEnergy(VoltageTier tier, OmegaValue amount) {
        tieredEnergy.put(tier, amount);
        this.energy = computeTotal();
    }
}
