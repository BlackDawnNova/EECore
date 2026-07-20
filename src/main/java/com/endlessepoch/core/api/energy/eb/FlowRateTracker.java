package com.endlessepoch.core.api.energy.eb;

import com.endlessepoch.core.Config;

/**
 * Tracks item flow rate over a sliding tick window. High input volatility signals
 * that speculative execution is likely to be invalidated; when {@code p4SpeculationEnabled}
 * is true, the rate feeds into speculation-level decisions.
 * <p>
 * 滑动窗口物品流入速率统计。波动剧烈时投机易作废，流速数据供投机等级决策。
 */
public final class FlowRateTracker {

    private final long[] history;
    private int cursor;
    private long total;

    public FlowRateTracker() { this(Config.p4FlowWindow); }

    FlowRateTracker(int windowSize) { this.history = new long[Math.max(1, windowSize)]; }

    public void record(long items) {
        total -= history[cursor];
        history[cursor] = items;
        total += items;
        if (++cursor >= history.length) cursor = 0;
    }

    public double avgPerTick() { return (double) total / history.length; }

    public double volatility() {
        double avg = avgPerTick();
        if (avg <= 0) return 0;
        double sumSq = 0;
        for (long v : history) { double d = v - avg; sumSq += d * d; }
        return Math.sqrt(sumSq / history.length) / avg;
    }
}
