package com.endlessepoch.core.api.energy.eb.batch;

/**
 * Prime-offset stagger scheduler — machines start batch snapshots on different ticks
 * so simultaneous mass-feeding does not spike a single tick.
 * Modes: PERFORMANCE staggers over primes 11/19/41, COMPROMISE over 3/7 (lower latency),
 * SPEED disables the offset entirely. The prime and the phase inside its period are both
 * derived from posHash, so machines sharing a prime still fire on different ticks.
 * Pure static computation — mode string injected, unit-testable.
 * 质数偏移错峰调度器——各机器在不同 tick 启动批处理快照，海量同时投料不再挤爆单个 tick。
 * 三模式：PERFORMANCE 用 11/19/41 错峰、COMPROMISE 用 3/7 折中低延迟、SPEED 关闭偏移。
 * 质数与相位都由 posHash 派生，同质数的机器也错开。纯静态计算——模式注入，可单测。
 */
public final class PrimeOffsetScheduler {

    static final long[] PERFORMANCE_PRIMES = {11, 19, 41};
    static final long[] COMPROMISE_PRIMES = {3, 7};

    private PrimeOffsetScheduler() {}

    /**
     * Whether the machine at posHash may start a batch on this tick.
     * 该机器本 tick 是否允许启动批处理。
     */
    public static boolean canProcess(long posHash, long gameTick, String mode) {
        long[] primes = switch (mode) {
            case "PERFORMANCE" -> PERFORMANCE_PRIMES;
            case "COMPROMISE" -> COMPROMISE_PRIMES;
            default -> null; // SPEED or unknown → no offset / SPEED 或未知值→关闭偏移
        };
        if (primes == null) return true;
        long prime = primes[(int) Math.floorMod(posHash, primes.length)];
        long phase = Math.floorMod(posHash >>> 4, prime); // decouple from prime pick / 与选质数解耦
        return Math.floorMod(gameTick + phase, prime) == 0;
    }
}
