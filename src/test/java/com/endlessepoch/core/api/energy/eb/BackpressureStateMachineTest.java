package com.endlessepoch.core.api.energy.eb;

import org.junit.jupiter.api.Test;

import static com.endlessepoch.core.api.energy.eb.BackpressureStateMachine.State.*;
import static org.junit.jupiter.api.Assertions.*;

class BackpressureStateMachineTest {

    @Test
    void startsIdle() {
        var sm = new BackpressureStateMachine();
        assertEquals(IDLE, sm.getState());
    }

    @Test
    void singleTickDoesNotTransition() {
        var sm = new BackpressureStateMachine();
        sm.tick(OUTPUT_FULL, 5);
        assertEquals(IDLE, sm.getState()); // only 1 tick, not enough
    }

    @Test
    void transitionsAfterFiveConsecutive() {
        var sm = new BackpressureStateMachine();
        for (int i = 0; i < 5; i++) sm.tick(OUTPUT_FULL, 5);
        assertEquals(OUTPUT_FULL, sm.getState());
    }

    @Test
    void resetsCounterOnChange() {
        var sm = new BackpressureStateMachine();
        // 3 ticks OUTPUT_FULL, then switch → counter resets
        for (int i = 0; i < 3; i++) sm.tick(OUTPUT_FULL, 5);
        sm.tick(VOLTAGE_LOW, 5);
        assertEquals(IDLE, sm.getState()); // counter reset, only 1 VOLTAGE_LOW
    }

    @Test
    void transitionsBackToIdle() {
        var sm = new BackpressureStateMachine();
        // Enter OUTPUT_FULL
        for (int i = 0; i < 5; i++) sm.tick(OUTPUT_FULL, 5);
        assertEquals(OUTPUT_FULL, sm.getState());
        // Return to IDLE
        for (int i = 0; i < 5; i++) sm.tick(IDLE, 5);
        assertEquals(IDLE, sm.getState());
    }

    @Test
    void recipeMismatchRequiresTwentyTicks() {
        var sm = new BackpressureStateMachine();
        for (int i = 0; i < 19; i++) sm.tick(RECIPE_MISMATCH, BackpressureStateMachine.DEBOUNCE_RECIPE_MISMATCH);
        assertEquals(IDLE, sm.getState()); // 19 < 20
        sm.tick(RECIPE_MISMATCH, BackpressureStateMachine.DEBOUNCE_RECIPE_MISMATCH);
        assertEquals(RECIPE_MISMATCH, sm.getState()); // 20th tick
    }

    @Test
    void coldStartFiresOnceThenNeverAgain() {
        var sm = new BackpressureStateMachine();
        // Trigger COLD_START
        for (int i = 0; i < 5; i++) sm.tick(COLD_START, 5);
        assertEquals(COLD_START, sm.getState());
        assertTrue(sm.hasColdStarted());
        // Return to IDLE
        for (int i = 0; i < 5; i++) sm.tick(IDLE, 5);
        assertEquals(IDLE, sm.getState());
        // Try COLD_START again — should not fire
        for (int i = 0; i < 5; i++) sm.tick(COLD_START, 5);
        assertEquals(IDLE, sm.getState()); // one-shot fired, ignored
    }

    @Test
    void statePersistsThroughBriefClearance() {
        var sm = new BackpressureStateMachine();
        // Enter OUTPUT_FULL
        for (int i = 0; i < 5; i++) sm.tick(OUTPUT_FULL, 5);
        assertEquals(OUTPUT_FULL, sm.getState());
        // 1 tick clear → stays OUTPUT_FULL (debounce on exit too)
        sm.tick(IDLE, 5);
        assertEquals(OUTPUT_FULL, sm.getState());
    }

    @Test
    void resetClearsAll() {
        var sm = new BackpressureStateMachine();
        for (int i = 0; i < 5; i++) sm.tick(OUTPUT_FULL, 5);
        sm.reset();
        assertEquals(IDLE, sm.getState());
        assertFalse(sm.hasColdStarted());
        // Cold start should work again after reset
        for (int i = 0; i < 5; i++) sm.tick(COLD_START, 5);
        assertEquals(COLD_START, sm.getState());
    }
}
