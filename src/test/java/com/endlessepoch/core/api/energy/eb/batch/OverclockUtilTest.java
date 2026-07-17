package com.endlessepoch.core.api.energy.eb.batch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-math tests for OverclockUtil — voltage gate, overclock speed/energy, heat factor.
 * OverclockUtil 纯计算测试——电压门槛、超频速度/能耗、热机倍率。
 */
class OverclockUtilTest {

    @Test
    void voltageGate_lowerTierRejected() {
        assertFalse(OverclockUtil.canProcess(0, 1)); // ELV machine, LV recipe
        assertTrue(OverclockUtil.canProcess(1, 1));  // LV machine, LV recipe
        assertTrue(OverclockUtil.canProcess(3, 1));  // HV machine, LV recipe
    }

    @Test
    void overclockCount_sameTierIsZero() {
        assertEquals(0, OverclockUtil.overclockCount(1, 1, 8));
        assertEquals(2, OverclockUtil.overclockCount(3, 1, 8));
    }

    @Test
    void overclockCount_cappedByMax() {
        assertEquals(8, OverclockUtil.overclockCount(11, 0, 8));
        assertEquals(0, OverclockUtil.overclockCount(11, 0, 0)); // overclock disabled / 关闭超频
    }

    @Test
    void duration_halvesPerTier() {
        assertEquals(200, OverclockUtil.computeDuration(200, 0));
        assertEquals(100, OverclockUtil.computeDuration(200, 1));
        assertEquals(50, OverclockUtil.computeDuration(200, 2));
        assertEquals(1, OverclockUtil.computeDuration(200, 60)); // floor at 1 / 下限 1
    }

    @Test
    void energy_doublesTotalPerTier() {
        // 32 Ω/t × 200t = 6400 base / 基础总能耗
        assertEquals(6400, OverclockUtil.computeEnergyPerUnit(32, 200, 0));
        assertEquals(12800, OverclockUtil.computeEnergyPerUnit(32, 200, 1));
        assertEquals(25600, OverclockUtil.computeEnergyPerUnit(32, 200, 2));
        assertEquals(0, OverclockUtil.computeEnergyPerUnit(0, 200, 3)); // free recipe / 免费配方
    }

    @Test
    void heatFactor_linearInterpolation() {
        assertEquals(1.0, OverclockUtil.heatFactor(0.0, 10.0, 1.5), 1e-9);
        assertEquals(1.25, OverclockUtil.heatFactor(5.0, 10.0, 1.5), 1e-9);
        assertEquals(1.5, OverclockUtil.heatFactor(10.0, 10.0, 1.5), 1e-9);
        assertEquals(1.5, OverclockUtil.heatFactor(20.0, 10.0, 1.5), 1e-9); // clamped / 超上限钳位
    }

    @Test
    void sustainedParallel_normalMath() {
        // LV 2A ≈ 162 Ω/t, iron@LV-overclock: 12800 Ω/op over 100t → 1 parallel
        // LV 2A 供电只养得起 1 并行
        assertEquals(1, OverclockUtil.sustainedParallel(162, 100, 12800));
        assertEquals(10, OverclockUtil.sustainedParallel(1296, 100, 12800)); // LV 16A
    }

    @Test
    void sustainedParallel_qvRateDoesNotOverflow() {
        // QV rate saturates to Long.MAX — naive multiply wrapped negative and
        // clamped max-voltage machines to single parallel (real bug found in-game)
        // QV 速率饱和到 Long.MAX——朴素乘法曾溢出为负，把满压机器钳到单并行（实机踩到的 bug）
        assertEquals(Long.MAX_VALUE, OverclockUtil.sustainedParallel(Long.MAX_VALUE, 100, 12800));
        assertEquals(Long.MAX_VALUE, OverclockUtil.sustainedParallel(Long.MAX_VALUE / 2, 200, 1));
    }

    @Test
    void sustainedParallel_edges() {
        assertEquals(0, OverclockUtil.sustainedParallel(0, 100, 12800));   // no supply / 无供电
        assertEquals(Long.MAX_VALUE, OverclockUtil.sustainedParallel(100, 100, 0)); // free recipe / 免费配方
        assertEquals(1, OverclockUtil.sustainedParallel(1, 1, 12800));     // floor at 1 / 下限 1
    }

    @Test
    void saturatingAdd_overflowCaps() {
        assertEquals(3, OverclockUtil.saturatingAdd(1, 2));
        assertEquals(Long.MAX_VALUE, OverclockUtil.saturatingAdd(Long.MAX_VALUE, 1));
        assertEquals(Long.MAX_VALUE, OverclockUtil.saturatingAdd(Long.MAX_VALUE, Long.MAX_VALUE));
    }
}
