package com.endlessepoch.core.api.energy.eb;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

/**
 * Thread-pool factory. Server: CPU−1, client: CPU/2.
 * 线程池工厂，服务端/客户端自动适配。
 */
public final class Schedulers {

    static final int CPU = Runtime.getRuntime().availableProcessors();
    static final int BG_SIZE = Math.max(2, CPU >= 4 ? CPU - 1 : CPU / 2);

    /** Background pool for recipe computation / 配方计算后台线程池 */
    public static final ExecutorService BACKGROUND = Executors.newFixedThreadPool(BG_SIZE, r -> {
        var t = new Thread(r, "EECore-EB-worker");
        t.setDaemon(true);
        return t;
    });

    /** ForkJoin pool (used in Phase 3) / ForkJoin 池（Phase 3 启用） */
    public static final ForkJoinPool FORK_JOIN = new ForkJoinPool(
            Math.min(CPU * 16, 16384),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            (t, e) -> System.err.println("[EB] ForkJoin uncaught: " + e.getMessage()),
            false
    );

    private Schedulers() {}
}
