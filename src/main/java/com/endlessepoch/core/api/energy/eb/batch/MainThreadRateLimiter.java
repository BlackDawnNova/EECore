package com.endlessepoch.core.api.energy.eb.batch;

/**
 * Global main-thread write-back budget — at most mainThreadLimit recipe units per tick
 * across ALL machines (the per-machine P·D quota still paces each machine individually).
 * The server-tick driver refills the budget; machines acquire before writing back and
 * release what they didn't use. Main-thread only — no synchronization needed.
 * 主线程全局写回预算——所有机器合计每 tick 最多 mainThreadLimit 配方单元
 * （单机 P·D 配额仍各自节拍）。驱动点每 tick 充值，机器写回前 acquire、未用完 release。
 * 仅主线程访问，无需同步。
 */
public final class MainThreadRateLimiter {

    private static int budget;
    private static int currentLimit = 256;

    private MainThreadRateLimiter() {}

    /** Current dynamic limit for display. / 当前动态限额，供显示。 */
    public static int currentLimit() { return currentLimit; }

    /** Refill the budget at the start of each server tick. / 每 tick 开始时充值预算。 */
    public static void newTick(int limit) {
        budget = Math.max(0, limit);
        currentLimit = limit;
    }

    /** Grant up to wanted units from the remaining budget. / 从剩余预算中授予至多 wanted 单元。 */
    public static int acquire(int wanted) {
        int granted = Math.max(0, Math.min(wanted, budget));
        budget -= granted;
        return granted;
    }

    /** Return unused units (write-back stalled early). / 归还未用完的单元（写回提前中断）。 */
    public static void release(int unused) {
        if (unused > 0) budget += unused;
    }

    /** Remaining budget this tick. / 本 tick 剩余预算。 */
    public static int remaining() {
        return budget;
    }
}
