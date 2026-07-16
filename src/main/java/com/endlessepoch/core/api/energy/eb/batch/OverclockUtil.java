package com.endlessepoch.core.api.energy.eb.batch;

/**
 * Pure static utilities for voltage-gating, overclock, and heat-factor computation.
 * Zero MC/config dependencies — callers inject config values as parameters.
 * 纯静态工具：电压门槛判定、超频计算、热机倍率。零 MC/配置依赖，配置值由调用方传入。
 */
public final class OverclockUtil {

    private OverclockUtil() {}

    /**
     * Whether the machine tier meets or exceeds the recipe required tier.
     * 机器电压是否达到配方最低需求电压。
     */
    public static boolean canProcess(int machineTierIndex, long requiredTierIndex) {
        return machineTierIndex >= (int) requiredTierIndex;
    }

    /**
     * How many overclock tiers apply, capped by maxOverclock.
     * 超频级数，受 maxOverclock 上限钳制。
     */
    public static int overclockCount(int machineTierIndex, long requiredTierIndex, int maxOverclock) {
        int raw = machineTierIndex - (int) requiredTierIndex;
        return Math.max(0, Math.min(raw, maxOverclock));
    }

    /**
     * Base duration reduced by factor 2 per overclock tier (integer division, min 1).
     * 每超一级加工时长减半（整数除，最少 1）。
     */
    public static long computeDuration(long baseDuration, int overclockCount) {
        return Math.max(1, baseDuration >> Math.min(overclockCount, 62));
    }

    /**
     * Total Ω per recipe unit after overclock.
     * baseEnergyPerTick × baseDuration × 2^n （每超一级总能耗翻倍）。
     * 超频后每配方单元的总 Ω 能耗。
     */
    public static long computeEnergyPerUnit(long baseEnergyPerTick, long baseDuration, int overclockCount) {
        if (baseEnergyPerTick <= 0) return 0L;
        long energy = baseEnergyPerTick * baseDuration;
        // 逐级翻倍并防溢出 / double per tier, guard against overflow
        for (int i = 0; i < overclockCount && energy < Long.MAX_VALUE / 2; i++)
            energy <<= 1;
        return energy;
    }

    /**
     * Heat-based speed factor：{@code 1.0 + (heat / max(1, maxHeat)) × (speedBoostMax - 1.0)}.
     * 热机速度倍率。
     */
    public static double heatFactor(double heat, double maxHeat, double speedBoostMax) {
        double denom = Math.max(1.0, maxHeat);
        double ratio = Math.min(1.0, Math.max(0.0, heat) / denom); // clamp to [0, maxHeat] / 钳位
        return 1.0 + ratio * (speedBoostMax - 1.0);
    }

    /**
     * Final duration after overclock × heat. / 超频 × 热机后的最终耗时。
     */
    public static long finalDuration(long baseDuration, int overclockCount,
                                     double heat, double maxHeat, double speedBoostMax) {
        long ocDur = computeDuration(baseDuration, overclockCount);
        double factor = heatFactor(heat, maxHeat, speedBoostMax);
        return Math.max(1, (long) (ocDur / factor));
    }
}
