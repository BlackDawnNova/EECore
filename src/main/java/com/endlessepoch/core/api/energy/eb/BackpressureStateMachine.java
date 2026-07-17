package com.endlessepoch.core.api.energy.eb;

/**
 * Debounced backpressure state machine — replaces naked boolean flags.
 * Each state transition requires consecutive ticks of the triggering condition
 * (5 for most states, 20 for RECIPE_MISMATCH), preventing UI flicker from
 * transient stalls. COLD_START is one-shot — it fires once then never re-triggers.
 * Pure POJO, unit-testable without any game dependencies.
 * 防抖背压状态机——替代裸 boolean 标记。每个状态转换需要连续触发 tick
 * （多数 5 tick，RECIPE_MISMATCH 20 tick），消除瞬时卡顿的 UI 抖动。
 * COLD_START 仅首次触发——此后永不重发。纯 POJO，不依赖任何游戏代码，可单测。
 */
public final class BackpressureStateMachine {

    public enum State {
        /** No backpressure. / 无背压。 */
        IDLE,
        /** Output bus is full, products have nowhere to go. / 输出总线满，产物无处放。 */
        OUTPUT_FULL,
        /** Recipe voltage requirement exceeds machine capability. / 配方电压需求超机器能力。 */
        VOLTAGE_LOW,
        /** Recipe matched but heat is zero (cold boot). One-shot. / 配方匹配但热量为零（冷启动），仅首次。 */
        COLD_START,
        /** No matching recipe, but items still present (KJS dynamic recipes etc.). / 无匹配配方但物品存在（KJS 动态配方等）。 */
        RECIPE_MISMATCH
    }

    // Debounce thresholds / 防抖阈值
    public static final int DEBOUNCE_DEFAULT = 5;
    public static final int DEBOUNCE_RECIPE_MISMATCH = 20;

    private State current = State.IDLE;
    private State candidate = State.IDLE;
    private int counter;
    private boolean coldStartFired;

    /**
     * Report the triggering condition observed this tick.
     * The state commits only after consecutive-ticks reach the debounce threshold.
     * 每 tick 报告观察到的触发条件。连续 tick 达到防抖阈值后才提交状态。
     */
    public void tick(State desired) {
        int threshold = (desired == State.RECIPE_MISMATCH) ? DEBOUNCE_RECIPE_MISMATCH : DEBOUNCE_DEFAULT;
        tick(desired, threshold);
    }

    /** Injected-threshold variant for tests. / 阈值注入版（供单测）。 */
    void tick(State desired, int threshold) {
        if (desired == State.COLD_START && coldStartFired) return;
        if (desired == current) {
            // Already committed — keep counting for hysteresis on exit
            // 已在当前状态——继续计数用于退出迟滞
            if (candidate != current) {
                candidate = current;
                counter = 1;
            } else {
                counter++;
            }
            return;
        }
        if (desired == candidate) {
            counter++;
        } else {
            candidate = desired;
            counter = 1;
        }
        if (counter >= threshold && current != candidate) {
            current = candidate;
            if (current == State.COLD_START) coldStartFired = true;
            counter = 0;
        }
    }

    /** Current committed state. / 当前已提交的状态。 */
    public State getState() { return current; }

    /** Has the cold-start one-shot already fired? / 冷启动是否已触发过？ */
    public boolean hasColdStarted() { return coldStartFired; }

    /** Reset everything (machine broken). / 全部复位（机器解体）。 */
    public void reset() {
        current = State.IDLE;
        candidate = State.IDLE;
        counter = 0;
        coldStartFired = false;
    }
}
