package com.endlessepoch.core.api.energy.eb.batch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-math tests for PrimeOffsetScheduler — three modes, stagger period, phase spread.
 * PrimeOffsetScheduler 纯计算测试——三模式、错峰周期、相位分散。
 */
class PrimeOffsetSchedulerTest {

    @Test
    void speedMode_alwaysFires() {
        for (long t = 0; t < 100; t++) {
            assertTrue(PrimeOffsetScheduler.canProcess(12345L, t, "SPEED"));
            assertTrue(PrimeOffsetScheduler.canProcess(-7L, t, "SPEED"));
        }
    }

    @Test
    void unknownMode_treatedAsNoOffset() {
        assertTrue(PrimeOffsetScheduler.canProcess(1L, 7L, "WHATEVER"));
    }

    @Test
    void performanceMode_firesOncePerPrimePeriod() {
        long window = 11L * 19 * 41; // common multiple of the pool / 质数池公倍数
        long[] hashes = {0L, 1L, 17L, -99L, 0x123456789AL};
        for (long h : hashes) {
            int fires = 0;
            for (long t = 0; t < window; t++)
                if (PrimeOffsetScheduler.canProcess(h, t, "PERFORMANCE")) fires++;
            // Exactly window/prime fires for whichever prime the hash picked
            // 命中次数恰为 窗口/所选质数
            assertTrue(fires == window / 11 || fires == window / 19 || fires == window / 41,
                    "posHash " + h + " fired " + fires);
        }
    }

    @Test
    void compromiseMode_lowerLatencyPrimes() {
        long window = 3L * 7;
        int fires = 0;
        for (long t = 0; t < window; t++)
            if (PrimeOffsetScheduler.canProcess(5L, t, "COMPROMISE")) fires++;
        assertTrue(fires == window / 3 || fires == window / 7);
    }

    @Test
    void deterministic_periodRepeats() {
        long h = 4242L;
        long cycle = 11L * 19 * 41; // full pool cycle / 质数池全周期
        for (long t = 0; t < 200; t++) {
            assertEquals(PrimeOffsetScheduler.canProcess(h, t, "PERFORMANCE"),
                    PrimeOffsetScheduler.canProcess(h, t + cycle, "PERFORMANCE"));
        }
    }

    @Test
    void phases_spreadMachinesSharingAPrime() {
        // Two hashes picking the same prime should not fire on identical tick sets
        // unless their phases collide — verify at least one differing pair exists.
        // 同质数的机器相位应分散——验证存在至少一对机器错峰。
        boolean foundDifferentPhase = false;
        outer:
        for (long a = 0; a < 64; a++) {
            for (long b = a + 1; b < 64; b++) {
                for (long t = 0; t < 41; t++) {
                    boolean fa = PrimeOffsetScheduler.canProcess(a, t, "PERFORMANCE");
                    boolean fb = PrimeOffsetScheduler.canProcess(b, t, "PERFORMANCE");
                    if (fa != fb) { foundDifferentPhase = true; break outer; }
                }
            }
        }
        assertTrue(foundDifferentPhase);
    }
}
