package com.endlessepoch.core.api.energy.eb.batch;

/**
 * 20-tick sliding-median TPS sampler with dead-band tiering.
 * Median ignores outlier spikes (auto-save etc.) that would falsely trigger
 * emergency mode with a mean-based window.
 * Tiers: above full threshold → 1.0 (full concurrency); between → 0.8 (reduced);
 * at or below reduced threshold → 0.0 (emergency: effectively single serial shard).
 * A ±0.1 TPS dead band keeps the tier from flapping at the boundaries.
 * Pure computation — nanoTime and thresholds are injected, unit-testable.
 * 20-tick 滑动中位数 TPS 采样器 + 死区分档。中位数免疫异常尖峰（自动存档等），
 * 避免基于均值的窗口误触发紧急模式。
 * 高于全开阈值→1.0；两阈值之间→0.8；低于等于紧急阈值→0.0（仅单条串行分片）。
 * ±0.1 TPS 死区防止档位在阈值边缘抖动。纯计算——时间与阈值参数注入，可单测。
 */
public final class TpsQuotaManager {

    /** Global instance driven by the server-tick driver. / 全局实例，由服务器 tick 驱动点驱动。 */
    public static final TpsQuotaManager GLOBAL = new TpsQuotaManager();

    /** Sliding window size in ticks — per the blueprint. / 滑动窗口大小（tick），按蓝图固定。 */
    static final int WINDOW = 20;
    /** Dead band around tier thresholds. / 档位阈值死区。 */
    static final double DEADBAND = 0.1;

    static final double SCALE_FULL = 1.0;
    static final double SCALE_REDUCED = 0.8;
    static final double SCALE_EMERGENCY = 0.0;

    private final long[] intervals = new long[WINDOW];
    private int index;
    private int filled;
    private long lastNano;
    /** Current tier: 2 = full, 1 = reduced, 0 = emergency. / 当前档位。 */
    private int tier = 2;

    /**
     * Record one server tick and return the concurrency scale.
     * Before the window warms up the scale stays at full — no throttling during startup.
     * 记录一次服务器 tick 并返回并发缩放。窗口未填满前保持全开，启动阶段不限流。
     */
    public double tick(long nanoNow, double targetTickRate, double fullThreshold, double reducedThreshold) {
        if (lastNano != 0) {
            long delta = nanoNow - lastNano;
            if (delta > 0) {
                intervals[index] = delta;
                index = (index + 1) % WINDOW;
                if (filled < WINDOW) filled++;
            }
        }
        lastNano = nanoNow;
        if (filled < WINDOW) return SCALE_FULL;
        double tps = tps(targetTickRate);
        lastTps = tps;
        tier = nextTier(tier, tps, fullThreshold, reducedThreshold);
        return scaleOf(tier);
    }

    /**
     * Sliding-median TPS, clamped to the target tick rate.
     * Median ignores auto-save spikes (a single 300ms tick in a 20-tick window
     * drags the mean below 16.5; the median stays at 20.0).
     * 滑动中位数 TPS，钳位到目标 tickrate。
     * 中位数免疫自动存档尖峰（20 tick 窗口中单个 300ms 长 tick
     * 会把均值拉到 16.5 以下；中位数保持 20.0）。
     */
    public double tps(double targetTickRate) {
        if (filled == 0) return targetTickRate;
        long[] sorted = new long[filled];
        System.arraycopy(intervals, 0, sorted, 0, filled);
        java.util.Arrays.sort(sorted);
        long median = (filled % 2 == 0)
                ? (sorted[filled / 2 - 1] + sorted[filled / 2]) / 2
                : sorted[filled / 2];
        if (median <= 0) return targetTickRate;
        double raw = 1_000_000_000.0 / median;
        return Math.min(targetTickRate, raw);
    }

    /**
     * Tier transition with dead band: crossing a boundary only registers when the TPS
     * clears it by {@link #DEADBAND}; inside the band the previous tier holds.
     * 死区档位切换：TPS 越过边界超出死区才换档，落在死区内保持原档。
     */
    static int nextTier(int current, double tps, double fullThreshold, double reducedThreshold) {
        int raw = tps > fullThreshold ? 2 : tps > reducedThreshold ? 1 : 0;
        if (raw == current) return current;
        // Boundary between the two tiers being crossed / 本次跨越的档位边界
        double boundary = Math.min(current, raw) == 0 ? reducedThreshold : fullThreshold;
        if (raw > current) {
            return tps > boundary + DEADBAND ? raw : current;
        }
        return tps <= boundary - DEADBAND ? raw : current;
    }

    static double scaleOf(int tier) {
        return tier == 2 ? SCALE_FULL : tier == 1 ? SCALE_REDUCED : SCALE_EMERGENCY;
    }

    private double lastTps = -1;

    /** Recent TPS for /eeadmin stats. Returns -1 until window fills. / 最近 TPS，窗口填满前返回 -1。 */
    public double lastTps() { return lastTps; }

    /** Current tier for logging/inspection. / 当前档位（日志/诊断用）。 */
    public int tier() { return tier; }

    /** Reset all state (server stop). / 清空状态（服务器关闭时）。 */
    public void reset() {
        index = 0;
        filled = 0;
        lastNano = 0;
        tier = 2;
    }
}
