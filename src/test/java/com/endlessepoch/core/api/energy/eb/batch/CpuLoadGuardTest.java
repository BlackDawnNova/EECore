package com.endlessepoch.core.api.energy.eb.batch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-math tests for CpuLoadGuard — three-step scale plus unsampled passthrough.
 * CpuLoadGuard 纯计算测试——三级缩放与未采样直通。
 */
class CpuLoadGuardTest {

    private static final double WARN = 0.80, HIGH = 0.90, CRIT = 0.95;

    @Test
    void unsampled_fullConcurrency() {
        assertEquals(1.0, CpuLoadGuard.scale(-1.0, WARN, HIGH, CRIT));
    }

    @Test
    void belowWarn_fullConcurrency() {
        assertEquals(1.0, CpuLoadGuard.scale(0.50, WARN, HIGH, CRIT));
        assertEquals(1.0, CpuLoadGuard.scale(0.80, WARN, HIGH, CRIT)); // boundary is exclusive / 边界不含
    }

    @Test
    void threeSteps() {
        assertEquals(0.7, CpuLoadGuard.scale(0.85, WARN, HIGH, CRIT));
        assertEquals(0.4, CpuLoadGuard.scale(0.92, WARN, HIGH, CRIT));
        assertEquals(0.2, CpuLoadGuard.scale(0.97, WARN, HIGH, CRIT));
        assertEquals(0.2, CpuLoadGuard.scale(1.00, WARN, HIGH, CRIT));
    }
}
