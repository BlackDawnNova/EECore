package com.endlessepoch.core.api.energy.eb.batch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-math tests for CpuLoadGuard — three-step scale, unsampled passthrough,
 * and recovery hysteresis.
 * CpuLoadGuard 纯计算测试——三级缩放、未采样直通与升档迟滞。
 */
class CpuLoadGuardTest {

    private static final double WARN = 0.80, HIGH = 0.90, CRIT = 0.95;

    private static double scale(double usage, double last) {
        return CpuLoadGuard.scale(usage, last, WARN, HIGH, CRIT);
    }

    @Test
    void unsampled_fullConcurrency() {
        assertEquals(1.0, scale(-1.0, 1.0));
        assertEquals(1.0, scale(-1.0, 0.2)); // unsampled overrides history / 未采样直通
    }

    @Test
    void belowWarn_fullConcurrency() {
        assertEquals(1.0, scale(0.50, 1.0));
        assertEquals(1.0, scale(0.80, 1.0)); // boundary is exclusive / 边界不含
    }

    @Test
    void throttleDown_immediate() {
        assertEquals(0.7, scale(0.85, 1.0));
        assertEquals(0.4, scale(0.92, 1.0));
        assertEquals(0.2, scale(0.97, 1.0));
        assertEquals(0.2, scale(1.00, 0.4)); // deeper cut applies at once / 更深降档立即生效
    }

    @Test
    void recovery_needsDeadBand() {
        // Hovering just below warn keeps the old tier / 贴着警戒线下方维持原档
        assertEquals(0.7, scale(0.79, 0.7));
        // Clearing warn − 0.03 recovers / 低于死区才升档
        assertEquals(1.0, scale(0.76, 0.7));
        // Multi-step recovery jumps straight to raw / 跨档恢复直达目标档
        assertEquals(1.0, scale(0.50, 0.2));
        // From critical, hovering below its boundary holds / 临界档贴边维持
        assertEquals(0.2, scale(0.93, 0.2));
    }
}
