package com.endlessepoch.core.api.energy.eb.batch;

/**
 * Three-step CPU load guard: above warn → keep 70%, above high → keep 40%,
 * above critical → keep 20% lightweight shards only. Unsampled (-1) → full.
 * Throttling down applies immediately; scaling back up requires the usage to clear
 * the boundary by a dead band, so a load hovering at a threshold cannot flap the
 * tier (and spam the tier-change log) every sample.
 * Pure static computation — usage, previous scale and thresholds injected, unit-testable.
 * CPU 三级梯度限流：超警戒线保留 70%、超高位线保留 40%、超临界线仅保留 20% 轻量分片。
 * 未采样（-1）→ 全开。降档立即生效；升档需低于边界减死区——负载贴着阈值抖动时
 * 档位不会每次采样横跳（也不会刷档位变化日志）。
 * 纯静态计算——占用率、上一档与阈值注入，可单测。
 */
public final class CpuLoadGuard {

    static final double SCALE_WARN = 0.7;
    static final double SCALE_HIGH = 0.4;
    static final double SCALE_CRITICAL = 0.2;
    /** Recovery dead band below the tier boundary. / 升档死区。 */
    static final double DEADBAND = 0.03;

    private CpuLoadGuard() {}

    /**
     * Concurrency scale for the given CPU usage (0.0–1.0, negative = unsampled),
     * with hysteresis against the previous scale.
     * 按 CPU 占用率返回并发缩放（0.0-1.0，负值=未采样），相对上一档带迟滞。
     */
    public static double scale(double cpuUsage, double lastScale,
                               double warnThreshold, double highThreshold, double criticalThreshold) {
        if (cpuUsage < 0) return 1.0;
        double raw = cpuUsage > criticalThreshold ? SCALE_CRITICAL
                : cpuUsage > highThreshold ? SCALE_HIGH
                : cpuUsage > warnThreshold ? SCALE_WARN
                : 1.0;
        if (raw <= lastScale) return raw; // throttle down (or hold) immediately / 降档或持平立即生效
        // Boundary of the tier being left / 离开的档位边界
        double boundary = lastScale == SCALE_CRITICAL ? criticalThreshold
                : lastScale == SCALE_HIGH ? highThreshold
                : warnThreshold;
        return cpuUsage <= boundary - DEADBAND ? raw : lastScale;
    }
}
