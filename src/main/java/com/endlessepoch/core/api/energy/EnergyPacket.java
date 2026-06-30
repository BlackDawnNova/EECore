package com.endlessepoch.core.api.energy;

import com.endlessepoch.core.api.tier.VoltageTier;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Ω Energy Packet.
 * <p>
 * Energy data packet containing voltage tier, amperage (A), and energy (Ω).
 * Used for energy transfer between machines, supporting step-down, splitting, and merging.
 * <p>
 * Ω 能量数据包。
 * <p>
 * 包含电压等级、电流（A）和能量（Ω）三个核心属性。
 * 在机器之间传输能量时使用，支持降压、分割、合并等操作。
 *
 * <p><b>Key Formulas / 关键公式：</b>
 * <ul>
 *   <li>Power (Ω/t) = Voltage × Current / 功率（Ω/t）= 电压 × 电流</li>
 *   <li>Energy (Ω) = Power × ticks (specified by creator) / 能量（Ω）= 功率 × tick 数（由创建者指定）</li>
 *   <li>After step-down: power conserved (voltage↓ current↑), energy attenuated by loss factor / 降级后：功率守恒（电压↓ 电流↑），能量按损耗因子衰减</li>
 * </ul>
 *
 * <pre>{@code
 * // Create an LV 1A energy packet with 50 Ω energy
 * EnergyPacket pkt = new EnergyPacket(VoltageTier.LV, 1, 50);
 *
 * // Step down to ELV (loss per global config, default 0.8/step)
 * EnergyPacket stepped = pkt.stepDownTo(VoltageTier.ELV);
 * }</pre>
 */
public class EnergyPacket {
    /**
     * 电压降级能量损耗因子。
     * 0.8 表示每降一级保留 80%（损耗 20%）。
     * <p>
     * 可通过 {@link #setStepLoss(double)} 配置，优先级：Config &gt; setter &gt; 默认 0.8。
     * 其他 Mod 可在构造时调用 setStepLoss() 更改全局行为。
     */
    private static double lossPerStep = 0.8;

    /**
     * 设置全局降级损耗因子。
     * @param loss 保留比例 (0.0-1.0)，默认 0.8
     */
    public static void setStepLoss(double loss) {
        lossPerStep = Math.max(0.0, Math.min(1.0, loss));
    }

    /**
     * 获取当前全局降级损耗因子。
     */
    public static double getStepLoss() {
        return lossPerStep;
    }

    private final VoltageTier tier;
    private final BigInteger amperage;
    private final OmegaValue energy;

    public EnergyPacket(VoltageTier tier, BigInteger amperage, OmegaValue energy) {
        this.tier = tier;
        this.amperage = amperage != null && amperage.signum() > 0 ? amperage : BigInteger.ONE;
        this.energy = energy != null ? energy : OmegaValue.zero();
    }

    public EnergyPacket(VoltageTier tier, long amperage, OmegaValue energy) {
        this(tier, BigInteger.valueOf(Math.max(1, amperage)), energy);
    }

    public EnergyPacket(VoltageTier tier, long amperage, long energy) {
        this(tier, BigInteger.valueOf(Math.max(1, amperage)), OmegaValue.of(energy));
    }

    public EnergyPacket(VoltageTier tier, OmegaValue energy) {
        this(tier, BigInteger.ONE, energy);
    }

    public EnergyPacket(VoltageTier tier, long energy) {
        this(tier, BigInteger.ONE, OmegaValue.of(energy));
    }

    public VoltageTier getTier() { return tier; }
    public BigInteger getAmperage() { return amperage; }
    public OmegaValue getEnergy() { return energy; }
    public boolean isEmpty() { return energy.isZero(); }

    public BigInteger getVoltage() { return tier.getMinVoltage(); }

    public BigInteger getPowerPerTick() {
        return tier.getMinVoltage().multiply(amperage);
    }

    public BigInteger getPowerPerSecond() {
        return getPowerPerTick().multiply(BigInteger.valueOf(20));
    }

    public boolean canBeReceivedBy(VoltageTier machineTier) {
        return tier.getMinVoltage().compareTo(machineTier.getMinVoltage()) <= 0;
    }

    public boolean canBeReceivedBy(BigInteger maxVoltage) {
        return tier.getMinVoltage().compareTo(maxVoltage) <= 0;
    }

    public EnergyPacket stepDownTo(VoltageTier targetTier) {
        if (targetTier.getMinVoltage().compareTo(tier.getMinVoltage()) >= 0) return this;

        BigInteger currentVoltage = tier.getMinVoltage();
        BigInteger targetVoltage = targetTier.getMinVoltage();
        BigInteger currentPower = currentVoltage.multiply(amperage);

        BigInteger newAmperage = currentPower.divide(targetVoltage);
        if (newAmperage.signum() < 1) newAmperage = BigInteger.ONE;

        int steps = tier.ordinal() - targetTier.ordinal();
        OmegaValue currentEnergy = energy;
        if (lossPerStep == 1.0) {
        } else if (lossPerStep == 0.0) {
            currentEnergy = OmegaValue.zero();
        } else {
            // 有理数精度：将 lossPerStep 转为分子/分母（分母固定 100）
            BigInteger numerator = BigInteger.valueOf(Math.round(lossPerStep * 100));
            BigInteger denominator = BigInteger.valueOf(100);
            for (int i = 0; i < steps; i++) {
                currentEnergy = currentEnergy.multiply(numerator).divide(denominator);
            }
        }

        return new EnergyPacket(targetTier, newAmperage, currentEnergy);
    }

    public List<EnergyPacket> split(int count) {
        if (count <= 1) return List.of(this);
        OmegaValue perPacket = energy.divide(count);
        OmegaValue remainder = energy.subtract(perPacket.multiply(count));
        List<EnergyPacket> result = new ArrayList<>();
        for (int i = 0; i < count - 1; i++) {
            result.add(new EnergyPacket(tier, amperage, perPacket));
        }
        result.add(new EnergyPacket(tier, amperage, perPacket.add(remainder)));
        return result;
    }

    public static EnergyPacket merge(List<EnergyPacket> packets) {
        if (packets.isEmpty()) {
            return new EnergyPacket(VoltageTier.ELV, BigInteger.ONE, OmegaValue.zero());
        }
        VoltageTier firstTier = packets.get(0).getTier();
        BigInteger firstAmperage = packets.get(0).getAmperage();
        OmegaValue total = OmegaValue.zero();
        for (EnergyPacket p : packets) {
            if (p.getTier() != firstTier) throw new IllegalArgumentException("Tier mismatch");
            total = total.add(p.getEnergy());
        }
        return new EnergyPacket(firstTier, firstAmperage, total);
    }

    public BigInteger getFEBigInteger() {
        return EnergyUnit.FE.convertFromOmega(energy.toBigInteger());
    }

    public BigInteger getFEPerTickBigInteger() {
        return EnergyUnit.FE.convertFromOmega(getPowerPerTick());
    }

    public BigInteger getFEPerSecondBigInteger() {
        return EnergyUnit.FE.convertFromOmega(getPowerPerSecond());
    }

    /**
     * @deprecated 大数值会溢出。使用 {@link #getFEBigInteger()}.
     */
    @Deprecated
    public long getFE() {
        return getFEBigInteger().longValue();
    }

    /**
     * @deprecated 大数值会溢出。使用 {@link #getFEPerTickBigInteger()}.
     */
    @Deprecated
    public long getFEPerTick() {
        return getFEPerTickBigInteger().longValue();
    }

    /**
     * @deprecated 大数值会溢出。使用 {@link #getFEPerSecondBigInteger()}.
     */
    @Deprecated
    public long getFEPerSecond() {
        return getFEPerSecondBigInteger().longValue();
    }

    @Override
    public String toString() {
        return energy.toDisplayString() + " @ " + tier.getShortName() + " " + amperage + "A";
    }
}
