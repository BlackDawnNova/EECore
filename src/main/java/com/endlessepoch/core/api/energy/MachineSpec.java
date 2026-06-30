package com.endlessepoch.core.api.energy;

import com.endlessepoch.core.api.tier.VoltageTier;

import java.math.BigInteger;

/**
 * Declares omega energy specs for a machine.
 * 机器的 Ω 能量规格声明。
 * <p>
 * Third-party mods can create a complete energy storage in one line via this record,
 * without manually assembling OmegaStorage.
 * It can also be used to declare a machine's electrical characteristics
 * (tier, capacity, IO limits, amperage) for integration by others.
 * 第三方 Mod 通过此记录一行创建完整能量存储，无需手动装配 OmegaStorage。
 * 也可以用来声明机器的电气特性（等级、容量、IO 限制、电流）供他人集成。
 *
 * <pre>{@code
 * // 一行创建 MV 级 10000Ω 容量、128Ω/t IO 的存储
 * OmegaStorage storage = MachineSpec.builder(VoltageTier.MV)
 *         .capacity(10000)
 *         .maxIO(128)
 *         .build()
 *         .createStorage();
 *
 * // 简便方式（相同效果）
 * OmegaStorage storage = MachineSpec.simple(VoltageTier.MV, 10000, 128).createStorage();
 * }</pre>
 */
public record MachineSpec(
        VoltageTier tier,
        OmegaValue capacity,
        OmegaValue maxInput,
        OmegaValue maxOutput,
        int maxAmperage
) {

    /**
     * Creates a default OmegaStorage from this spec (zero initial energy).
     * 从此规格创建默认的 OmegaStorage（初始能量为零）。
     */
    public OmegaStorage createStorage() {
        return new OmegaStorage(capacity, maxInput, maxOutput, tier);
    }

    /**
     * Creates an OmegaStorage with the given initial energy.
     * 创建带初始能量的 OmegaStorage。
     */
    public OmegaStorage createStorage(OmegaValue initialEnergy) {
        OmegaStorage storage = createStorage();
        storage.setEnergy(initialEnergy);
        return storage;
    }

    /**
     * Creates a simple machine spec (equal input/output, 1A amperage).
     * 创建简单的机器规格（输入/输出相等，1A 电流）。
     *
     * @param tier    voltage tier / 电压等级
     * @param capacity max capacity in Omega / 最大容量（Ω）
     * @param maxIO   max input/output per tick / 最大输入/输出（Ω/t）
     */
    public static MachineSpec simple(VoltageTier tier, long capacity, long maxIO) {
        return new MachineSpec(tier, OmegaValue.of(capacity), OmegaValue.of(maxIO), OmegaValue.of(maxIO), 1);
    }

    /**
     * Creates a simple machine spec (BigInteger variant).
     * 创建简单的机器规格（BigInteger 版）。
     */
    public static MachineSpec simple(VoltageTier tier, BigInteger capacity, BigInteger maxIO) {
        return new MachineSpec(tier, OmegaValue.of(capacity), OmegaValue.of(maxIO), OmegaValue.of(maxIO), 1);
    }

    public static Builder builder(VoltageTier tier) {
        return new Builder(tier);
    }

    public static class Builder {
        private final VoltageTier tier;
        private OmegaValue capacity = OmegaValue.max();
        private OmegaValue maxInput = OmegaValue.zero();
        private OmegaValue maxOutput = OmegaValue.zero();
        private int maxAmperage = 1;

        Builder(VoltageTier tier) {
            this.tier = tier;
        }

        public Builder capacity(OmegaValue v) { this.capacity = v; return this; }
        public Builder capacity(long v) { return capacity(OmegaValue.of(v)); }
        public Builder capacity(BigInteger v) { return capacity(OmegaValue.of(v)); }

        public Builder maxInput(OmegaValue v) { this.maxInput = v; return this; }
        public Builder maxInput(long v) { return maxInput(OmegaValue.of(v)); }

        public Builder maxOutput(OmegaValue v) { this.maxOutput = v; return this; }
        public Builder maxOutput(long v) { return maxOutput(OmegaValue.of(v)); }

        public Builder maxIO(OmegaValue v) { this.maxInput = v; this.maxOutput = v; return this; }
        public Builder maxIO(long v) { return maxIO(OmegaValue.of(v)); }

        public Builder maxAmperage(int a) { this.maxAmperage = Math.max(1, a); return this; }

        public MachineSpec build() {
            return new MachineSpec(tier, capacity, maxInput, maxOutput, maxAmperage);
        }
    }
}
