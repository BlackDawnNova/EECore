package com.endlessepoch.core.api.energy.eb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlowRateTrackerTest {

    @Test
    void avgPerTick_steadyFlow() {
        FlowRateTracker t = new FlowRateTracker(5);
        for (int i = 0; i < 5; i++) t.record(100);
        assertEquals(100.0, t.avgPerTick(), 0.001);
    }

    @Test
    void avgPerTick_varyingFlow() {
        FlowRateTracker t = new FlowRateTracker(5);
        t.record(10); t.record(20); t.record(30); t.record(40); t.record(50);
        assertEquals(30.0, t.avgPerTick(), 0.001);
    }

    @Test
    void volatility_steadyIsZero() {
        FlowRateTracker t = new FlowRateTracker(5);
        for (int i = 0; i < 5; i++) t.record(100);
        assertEquals(0.0, t.volatility(), 0.001);
    }

    @Test
    void volatility_fluctuating() {
        FlowRateTracker t = new FlowRateTracker(5);
        t.record(0); t.record(100); t.record(0); t.record(100); t.record(0);
        assertTrue(t.volatility() > 0.8);
    }

    @Test
    void volatility_zeroAvg_returnsZero() {
        FlowRateTracker t = new FlowRateTracker(5);
        assertEquals(0.0, t.volatility(), 0.001);
    }

    @Test
    void record_overwritesOldest() {
        FlowRateTracker t = new FlowRateTracker(5);
        for (int i = 0; i < 5; i++) t.record(10);
        t.record(60);
        assertEquals(20.0, t.avgPerTick(), 0.001);
    }

    @Test
    void windowSize1() {
        FlowRateTracker t = new FlowRateTracker(1);
        t.record(42);
        assertEquals(42.0, t.avgPerTick(), 0.001);
    }
}
