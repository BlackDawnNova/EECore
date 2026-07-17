package com.endlessepoch.core.api.energy.eb.batch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the global main-thread write-back budget.
 * 主线程全局写回预算测试。
 */
class MainThreadRateLimiterTest {

    @Test
    void acquire_capsAtBudget() {
        MainThreadRateLimiter.newTick(256);
        assertEquals(200, MainThreadRateLimiter.acquire(200));
        assertEquals(56, MainThreadRateLimiter.acquire(100)); // only 56 left / 只剩 56
        assertEquals(0, MainThreadRateLimiter.acquire(10));
    }

    @Test
    void release_returnsUnused() {
        MainThreadRateLimiter.newTick(100);
        assertEquals(100, MainThreadRateLimiter.acquire(100));
        MainThreadRateLimiter.release(40);
        assertEquals(40, MainThreadRateLimiter.remaining());
        assertEquals(40, MainThreadRateLimiter.acquire(64));
    }

    @Test
    void newTick_resetsBudget() {
        MainThreadRateLimiter.newTick(16);
        MainThreadRateLimiter.acquire(16);
        MainThreadRateLimiter.newTick(2048);
        assertEquals(2048, MainThreadRateLimiter.remaining());
    }
}
