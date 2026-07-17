package com.endlessepoch.core.api.energy.eb.batch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-math tests for TpsQuotaManager — sliding window, three tiers, dead band, warm-up.
 * TpsQuotaManager 纯计算测试——滑动窗口、三档、死区、预热期。
 */
class TpsQuotaManagerTest {

    private static final double FULL = 19.5, REDUCED = 16.5, TARGET = 20.0;

    /** Feed count ticks at a fixed interval, return the last scale. / 按固定间隔喂 count 个 tick，返回最后的缩放。 */
    private static double feed(TpsQuotaManager m, long startNano, int count, long intervalNanos) {
        double scale = 1.0;
        long t = startNano;
        for (int i = 0; i < count; i++) {
            scale = m.tick(t, TARGET, FULL, REDUCED);
            t += intervalNanos;
        }
        return scale;
    }

    @Test
    void warmup_staysFull() {
        var m = new TpsQuotaManager();
        // Window not filled yet (first tick records no interval) / 窗口未填满（首 tick 不记间隔）
        assertEquals(1.0, feed(m, 1, TpsQuotaManager.WINDOW, 50_000_000L));
    }

    @Test
    void steady20Tps_fullConcurrency() {
        var m = new TpsQuotaManager();
        assertEquals(1.0, feed(m, 1, 40, 50_000_000L)); // 50ms → 20 TPS
        assertEquals(2, m.tier());
        assertEquals(20.0, m.tps(TARGET), 0.01);
    }

    @Test
    void midTps_reducedTier() {
        var m = new TpsQuotaManager();
        assertEquals(0.8, feed(m, 1, 40, 55_000_000L)); // 55ms → ~18.2 TPS
        assertEquals(1, m.tier());
    }

    @Test
    void lowTps_emergencyTier() {
        var m = new TpsQuotaManager();
        assertEquals(0.0, feed(m, 1, 40, 70_000_000L)); // 70ms → ~14.3 TPS
        assertEquals(0, m.tier());
    }

    @Test
    void recovery_climbsBackToFull() {
        var m = new TpsQuotaManager();
        feed(m, 1, 40, 70_000_000L);                      // emergency / 先压到紧急档
        long resume = 1 + 40L * 70_000_000L;
        assertEquals(1.0, feed(m, resume, 41, 50_000_000L)); // window refills at 20 TPS / 窗口重填满 20 TPS
        assertEquals(2, m.tier());
    }

    @Test
    void deadband_holdsTierInsideBand() {
        var m = new TpsQuotaManager();
        feed(m, 1, 40, 50_000_000L); // tier 2 / 先到全开档
        // 19.45 TPS is below full(19.5) but inside the ±0.1 band → hold tier 2
        // 19.45 TPS 低于全开阈值但落在死区内 → 保持全开档
        long interval = (long) (1_000_000_000L / 19.45);
        long resume = 1 + 40L * 50_000_000L;
        assertEquals(1.0, feed(m, resume, 41, interval));
        assertEquals(2, m.tier());
    }

    @Test
    void nextTier_pureTransitions() {
        // Downward needs boundary − deadband / 降档需越过 边界−死区
        assertEquals(2, TpsQuotaManager.nextTier(2, 19.45, FULL, REDUCED));
        assertEquals(1, TpsQuotaManager.nextTier(2, 19.3, FULL, REDUCED));
        assertEquals(0, TpsQuotaManager.nextTier(1, 16.3, FULL, REDUCED));
        // Upward needs boundary + deadband / 升档需越过 边界+死区
        assertEquals(0, TpsQuotaManager.nextTier(0, 16.55, FULL, REDUCED));
        assertEquals(1, TpsQuotaManager.nextTier(0, 17.0, FULL, REDUCED));
        assertEquals(2, TpsQuotaManager.nextTier(1, 19.7, FULL, REDUCED));
        // Emergency straight to full in one jump / 紧急档一步跳回全开
        assertEquals(2, TpsQuotaManager.nextTier(0, 19.9, FULL, REDUCED));
    }

    @Test
    void reset_returnsToWarmup() {
        var m = new TpsQuotaManager();
        feed(m, 1, 40, 70_000_000L);
        m.reset();
        assertEquals(2, m.tier());
        assertEquals(1.0, feed(m, 1, 5, 70_000_000L)); // warming up again / 重新预热
    }
}
