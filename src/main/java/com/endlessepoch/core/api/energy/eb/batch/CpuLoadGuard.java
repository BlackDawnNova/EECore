package com.endlessepoch.core.api.energy.eb.batch;

/**
 * Three-step CPU load guard: above warn → keep 70%, above high → keep 40%,
 * above critical → keep 20% lightweight shards only. Unsampled (-1) → full.
 * Pure static computation — usage and thresholds injected, unit-testable.
 * CPU 三级梯度限流：超警戒线保留 70%、超高位线保留 40%、超临界线仅保留 20% 轻量分片。
 * 未采样（-1）→ 全开。纯静态计算——占用率与阈值注入，可单测。
 */
public final class CpuLoadGuard {

    static final double SCALE_WARN = 0.7;
    static final double SCALE_HIGH = 0.4;
    static final double SCALE_CRITICAL = 0.2;

    private CpuLoadGuard() {}

    /**
     * Concurrency scale for the given CPU usage (0.0–1.0, negative = unsampled).
     * 按 CPU 占用率返回并发缩放（0.0-1.0，负值=未采样）。
     */
    public static double scale(double cpuUsage, double warnThreshold, double highThreshold, double criticalThreshold) {
        if (cpuUsage < 0) return 1.0;
        if (cpuUsage > criticalThreshold) return SCALE_CRITICAL;
        if (cpuUsage > highThreshold) return SCALE_HIGH;
        if (cpuUsage > warnThreshold) return SCALE_WARN;
        return 1.0;
    }
}
