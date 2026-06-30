package com.endlessepoch.core.api.energy;

import com.endlessepoch.core.api.tier.VoltageTier;

import java.math.BigInteger;

/**
 * Ω 能量存储接口。
 * <p>
 * Omega energy storage interface.
 * <p>
 * 任何方块/物品要实现 Ω 能量系统，只需实现此接口并通过
 * {@link com.endlessepoch.core.api.EECoreCapabilities#OMEGA_ENERGY} 注册 Capability。
 * 其他 Mod 即可通过 {@code level.getCapability()} 无耦合访问能量存储。
 * <p>
 * Any block/item that wants to implement the Omega energy system should implement this interface
 * and register it as a Capability via {@link com.endlessepoch.core.api.EECoreCapabilities#OMEGA_ENERGY}.
 * Other mods can then access the energy storage without coupling via {@code level.getCapability()}.
 *
 * <pre>{@code
 * // 注册方（机器 BE）：
 * public class MyMachineBE extends BlockEntity implements IOmegaEnergyStorage {
 *     private final OmegaStorage storage = new OmegaStorage(...);
 *     // 委托所有方法给 storage
 * }
 *
 * // 访问方（邻居机器）：
 * var cap = level.getCapability(EECoreCapabilities.OMEGA_ENERGY, pos, side);
 * if (cap != null) cap.receivePacket(packet, false);
 * }</pre>
 *
 * @see OmegaStorage 完整实现（含 NBT、事件触发、降压）
 * @see OmegaStorage Full implementation (NBT, events, step-down)
 * @see com.endlessepoch.core.api.EECoreCapabilities
 * @see com.endlessepoch.core.api.energy.MachineSpec 一行声明机器规格
 * @see com.endlessepoch.core.api.energy.MachineSpec Declare machine spec in one line
 */
public interface IOmegaEnergyStorage {

    /**
     * 接收一个能量包。
     * Receive an energy packet.
     *
     * @param packet  能量包（含电压等级、电流、能量）/ Energy packet (voltage tier, amperage, energy)
     * @param simulate true 表示仅模拟（不写入实际存储）/ true to simulate (no actual storage write)
     * @return 被实际接收的能量包（含接收后的电压、电流、能量），如果全部被拒绝则返回 null。
     *         The actually received packet (with post-receive voltage, current, energy), or null if fully rejected.
     */
    EnergyPacket receivePacket(EnergyPacket packet, boolean simulate);

    /**
     * 从指定电压等级提取一个能量包。
     * Extract an energy packet from the specified voltage tier.
     * <p>
     * 提取时会先尝试目标等级的能量，不足则从更高等级降压提取。
     * Extraction first tries the target tier, then steps down from higher tiers if insufficient.
     *
     * @param requestedTier 请求的电压等级 / Requested voltage tier
     * @param simulate      true 表示仅模拟 / true to simulate
     * @return 被提取的能量包，无可用能量时返回 null。
     *         The extracted packet, or null if no energy is available.
     */
    EnergyPacket extractPacket(VoltageTier requestedTier, boolean simulate);

    /**
     * 接收指定量的能量（简化版，不涉及电压/电流）。
     * Receive a specified amount of energy (simplified, no voltage/current involved).
     * <p>
     * 内部自动匹配电压等级，适用于不想关心 EnergyPacket 细节的调用方。
     * Internally matches voltage tier automatically, suitable for callers who don't want to deal with EnergyPacket details.
     *
     * @param amount   接收量（Ω）/ Amount to receive (Omega)
     * @param simulate true 表示仅模拟 / true to simulate
     * @return 实际接收的能量量 / The amount of energy actually received
     */
    OmegaValue receiveEnergy(OmegaValue amount, boolean simulate);

    /**
     * 提取指定量的能量（简化版）。
     * Extract a specified amount of energy (simplified).
     *
     * @param amount   提取量（Ω）/ Amount to extract (Omega)
     * @param simulate true 表示仅模拟 / true to simulate
     * @return 实际提取的能量量 / The amount of energy actually extracted
     */
    OmegaValue extractEnergy(OmegaValue amount, boolean simulate);

    /**
     * 返回当前存储的总能量（所有等级合计）。
     * Returns the total energy stored across all tiers.
     */
    OmegaValue getEnergyStored();

    /**
     * 返回指定电压等级上的能量。
     * Returns the energy stored at the specified voltage tier.
     */
    OmegaValue getEnergyStored(VoltageTier tier);

    /**
     * 返回最大存储容量。
     * Returns the maximum storage capacity.
     */
    OmegaValue getCapacity();

    /**
     * 返回每 tick 最大可接收能量。
     * Returns the maximum receivable energy per tick.
     */
    OmegaValue getMaxInput();

    /**
     * 返回每 tick 最大可输出能量。
     * Returns the maximum extractable energy per tick.
     */
    OmegaValue getMaxOutput();

    /**
     * 返回机器额定电压等级。
     * Returns the machine's rated voltage tier.
     */
    VoltageTier getTier();

    /**
     * 检查能否接收指定电压等级的输入。
     * Checks whether input at the specified voltage tier is accepted.
     * 默认实现：只允许 ≤ 机器等级的输入。
     * Default: only allows input up to the machine's tier.
     */
    default boolean canInput(VoltageTier inputTier) {
        return inputTier.ordinal() <= getTier().ordinal();
    }

    /**
     * 检查能否输出到指定电压等级。
     * Checks whether output to the specified voltage tier is allowed.
     * 默认实现：机器等级 ≥ 目标等级即可。
     * Default: allowed if the machine's tier >= target tier.
     */
    default boolean canOutput(VoltageTier outputTier) {
        return getTier().ordinal() >= outputTier.ordinal();
    }

    /**
     * 检查当前存储量是否 ≥ 指定量。
     * Checks whether the current stored amount is >= the specified amount.
     */
    default boolean hasEnough(OmegaValue amount) {
        return getEnergyStored().compareTo(amount) >= 0;
    }

    /**
     * 返回存储能量（FE 兼容别名）。
     * Returns stored energy (FE compatibility alias).
     * 推荐使用 {@link #getEnergyStored()}。
     * Prefer {@link #getEnergyStored()}.
     */
    default OmegaValue getEnergyStoredFE() {
        return getEnergyStored();
    }

    /**
     * 返回容量（FE 兼容别名）。
     * Returns capacity (FE compatibility alias).
     * 推荐使用 {@link #getCapacity()}。
     * Prefer {@link #getCapacity()}.
     */
    default OmegaValue getCapacityFE() {
        return getCapacity();
    }

    /**
     * 以 FE 为单位接收能量。
     * Receive energy in FE units.
     * 输入输出仍为 OmegaValue 以避免大数溢出。
     * Input and output are still OmegaValue to avoid large number overflow.
     */
    default OmegaValue receiveFE(long amount, boolean simulate) {
        if (amount <= 0) return OmegaValue.zero();
        BigInteger omegaBig = EnergyUnit.FE.convertToOmega(BigInteger.valueOf(amount));
        return receiveEnergy(OmegaValue.of(omegaBig), simulate);
    }

    /**
     * 以 FE 为单位提取能量。
     * Extract energy in FE units.
     * 输入输出仍为 OmegaValue 以避免大数溢出。
     * Input and output are still OmegaValue to avoid large number overflow.
     */
    default OmegaValue extractFE(long amount, boolean simulate) {
        if (amount <= 0) return OmegaValue.zero();
        BigInteger omegaBig = EnergyUnit.FE.convertToOmega(BigInteger.valueOf(amount));
        return extractEnergy(OmegaValue.of(omegaBig), simulate);
    }

    /**
     * @deprecated 大数值会溢出。使用 {@link #getEnergyStoredFE()}（返回 OmegaValue）。
     *             Large values will overflow. Use {@link #getEnergyStoredFE()} (returns OmegaValue).
     */
    @Deprecated
    default long getEnergyStoredFELong() {
        return getEnergyStored().toLong();
    }

    /**
     * @deprecated 大数值会溢出。使用 {@link #getCapacityFE()}（返回 OmegaValue）。
     *             Large values will overflow. Use {@link #getCapacityFE()} (returns OmegaValue).
     */
    @Deprecated
    default long getCapacityFELong() {
        return getCapacity().toLong();
    }

    /**
     * @deprecated 大数值会溢出。使用 {@link #receiveFE(long, boolean)}（返回 OmegaValue）。
     *             Large values will overflow. Use {@link #receiveFE(long, boolean)} (returns OmegaValue).
     */
    @Deprecated
    default long receiveFELong(long amount, boolean simulate) {
        OmegaValue accepted = receiveFE(amount, simulate);
        return accepted.toLong();
    }

    /**
     * @deprecated 大数值会溢出。使用 {@link #extractFE(long, boolean)}（返回 OmegaValue）。
     *             Large values will overflow. Use {@link #extractFE(long, boolean)} (returns OmegaValue).
     */
    @Deprecated
    default long extractFELong(long amount, boolean simulate) {
        OmegaValue extracted = extractFE(amount, simulate);
        return extracted.toLong();
    }

    /**
     * 根据目标等级和总功率计算电流。
     * Calculate amperage based on target tier and total power.
     * <p>
     * 返回 BigInteger 以支持超大功率场景。
     * Returns BigInteger to support very large power scenarios.
     * 功率 ÷ 电压 = 电流，结果至少为 1。
     * Power / voltage = amperage, result is at least 1.
     */
    default BigInteger calculateAmperage(VoltageTier targetTier, BigInteger totalPower) {
        BigInteger voltage = targetTier.getMinVoltage();
        if (voltage == null || voltage.signum() <= 0) return BigInteger.ONE;
        BigInteger amps = totalPower.divide(voltage);
        return amps.signum() < 1 ? BigInteger.ONE : amps;
    }
}
