package com.endlessepoch.core.api.energy.eb;

import com.endlessepoch.core.Config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

/**
 * Thread-pool factory. Sizes come from config (0 = auto). Pools are created lazily
 * on first use — after config load — so the configured values are respected.
 * 线程池工厂，大小取自配置（0=自动）。池在首次使用时懒加载（晚于配置加载），配置值生效。
 */
public final class Schedulers {

    static final int CPU = Runtime.getRuntime().availableProcessors();

    /** Auto: server CPU−1, small hosts CPU/2, min 2 / 自动：CPU−1，小机 CPU/2，至少 2 */
    static final int BG_SIZE = resolve(Config.ebBgThreads,
            Math.max(2, CPU >= 4 ? CPU - 1 : CPU / 2));

    /** Background pool for recipe computation / 配方计算后台线程池 */
    public static final ExecutorService BACKGROUND = Executors.newFixedThreadPool(BG_SIZE, r -> {
        var t = new Thread(r, "EECore-EB-worker");
        t.setDaemon(true);
        return t;
    });

    /** ForkJoin pool (Phase 3 batch executor) / ForkJoin 池（Phase 3 批处理执行器） */
    public static final ForkJoinPool FORK_JOIN = new ForkJoinPool(
            resolve(Config.ebFjParallelism, Math.min(CPU * 16, 16384)),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            (t, e) -> System.err.println("[EB] ForkJoin uncaught: " + e.getMessage()),
            false
    );

    /** 0 = auto fallback / 0 表示取自动值 */
    private static int resolve(int configured, int auto) {
        return configured > 0 ? configured : auto;
    }

    private Schedulers() {}
}
