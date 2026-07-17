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
     * Ops sustainable by an energy input rate: rate × duration ÷ energyPerOp,
     * with saturation — near-QV rates overflow a naive multiply, which would
     * wrap negative and clamp the machine to single-parallel at MAX voltage.
     * 供电速率可维持的并行数：速率×耗时÷单次能耗，带饱和——QV 级速率会把朴素乘法
     * 溢出成负数，反而让满压机器被钳到单并行。
     */
    public static long sustainedParallel(long totalRate, long duration, long energyPerOp) {
        if (totalRate <= 0) return 0;
        if (duration <= 0 || energyPerOp <= 0) return Long.MAX_VALUE;
        if (totalRate > Long.MAX_VALUE / duration) return Long.MAX_VALUE; // saturate / 饱和
        return Math.max(1, totalRate * duration / energyPerOp);
    }

    /** Saturating add for rate accumulation across hatches. / 多仓速率累加的饱和加法。 */
    public static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum; // overflow → MAX / 溢出封顶
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

    /**
     * Optimal overclock count: pick the tier (0..maxOc) that maximizes throughput
     * given current energy rate and hardware cap. Each tier doubles speed and energy
     * per op — when power is the bottleneck, sustained parallel drops by 4× per tier
     * while speed only doubles, making overclock a net loss. This method auto-limits
     * to the highest tier that actually improves throughput.
     * 最优超频级数：在当前供电和硬件约束下，选吞吐量最大的级数（0..maxOc）。
     * 每级速度×2、能耗×2 → 功率瓶颈时有效并行跌 4×、速度只涨 2×，越超越慢。
     * 此方法自动取到最后一档"还有正面增益"的级数，避免反直觉降速。
     *
     * @param hardwareCap  machine parallel cap (parallel hatches) / 硬件并行上限
     * @param totalRate    Σ voltage × amperage from all energy inputs / 总供电速率
     * @param baseDuration recipe base processing time (ticks) / 配方基础耗时
     * @param baseEnergyPerTick recipe base Ω/tick / 配方基础功率
     * @param maxOc        config cap on overclock tiers / 允许的最大超频级数
     * @return best overclock count (0 means stay at recipe tier) / 最优超频级数
     */
    public static int optimalOverclock(int hardwareCap, long totalRate,
                                        long baseDuration, long baseEnergyPerTick, int maxOc) {
        if (maxOc <= 0 || hardwareCap <= 0) return 0;
        if (baseEnergyPerTick <= 0) return maxOc; // free energy: every tier helps / 零能耗配方，超频纯赚
        int best = 0;
        long bestScore = Math.min(hardwareCap,
                sustainedParallel(totalRate, baseDuration,
                        computeEnergyPerUnit(baseEnergyPerTick, baseDuration, 0)));
        // score = effectiveParallel × 2^n (normalized throughput) / 吞吐量归一化值
        for (int n = 1; n <= maxOc; n++) {
            long dur = computeDuration(baseDuration, n);
            long energy = computeEnergyPerUnit(baseEnergyPerTick, baseDuration, n);
            long sust = sustainedParallel(totalRate, dur, energy);
            long eff = Math.min(hardwareCap, sust);
            long score = eff << n; // eff × 2^n / 有效并行 × 速度倍率
            if (score > bestScore << best) {
                best = n;
                bestScore = eff;
            }
        }
        return best;
    }
}
