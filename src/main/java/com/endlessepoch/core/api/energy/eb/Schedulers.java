package com.endlessepoch.core.api.energy.eb;

import com.endlessepoch.core.Config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Thread-pool factory. Sizes come from config (0 = auto). Pools are created lazily
 * on first use — after config load — and can be shut down on ServerStopping, then
 * transparently recreated (single-player world switch keeps working).
 * 线程池工厂，大小取自配置（0=自动）。池在首次使用时懒加载（晚于配置加载），
 * 服务器关闭时可干净停机，再次使用自动重建（单人换存档不受影响）。
 */
public final class Schedulers {

    static final int LOGICAL_CPU = Runtime.getRuntime().availableProcessors();
    /** Physical cores (half of logical on x86 HT) — more accurate for pure-compute pool sizing. / 物理核心数——纯计算池配线程数更准。 */
    static final int PHYSICAL_CPU = Math.max(1, LOGICAL_CPU / 2);

    private static volatile ExecutorService background;
    private static volatile ForkJoinPool forkJoin;

    /** Background pool for recipe computation. / 配方计算后台线程池。 */
    public static ExecutorService background() {
        var p = background;
        if (p == null || p.isShutdown()) {
            synchronized (Schedulers.class) {
                p = background;
                if (p == null || p.isShutdown()) {
                    int size = resolve(Config.ebBgThreads, Math.max(2, LOGICAL_CPU >= 4 ? LOGICAL_CPU - 1 : LOGICAL_CPU / 2));
                    background = p = Executors.newFixedThreadPool(size, r -> {
                        var t = new Thread(r, "EECore-EB-worker");
                        t.setDaemon(true);
                        return t;
                    });
                }
            }
        }
        return p;
    }

    /** ForkJoin pool (Phase 3 batch executor). / ForkJoin 池（Phase 3 批处理执行器）。 */
    public static ForkJoinPool forkJoin() {
        var p = forkJoin;
        if (p == null || p.isShutdown()) {
            synchronized (Schedulers.class) {
                p = forkJoin;
                if (p == null || p.isShutdown()) {
                    // Pure-compute threads ≈ physical cores − 1 (leave one for main thread).
                    // Shard CONCURRENCY (queued tasks) is a separate knob clamped to [1024, 16384].
                    // 纯计算线程 ≈ 物理核心 − 1（留一个给主线程）。
                    // 分片"并发数"是任务排队量，独立于线程数，由 BatchExecutor 钳位 [1024, 16384]。
                    int workers = resolve(Config.ebFjParallelism, Math.max(2, PHYSICAL_CPU - 1));
                    forkJoin = p = new ForkJoinPool(
                            workers,
                            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                            (t, e) -> com.endlessepoch.core.EECore.LOGGER.error("[EB] ForkJoin uncaught in {}", t.getName(), e),
                            false);
                }
            }
        }
        return p;
    }

    /**
     * Shut both pools down and wait up to timeoutMillis for in-flight work.
     * Leftovers are cancelled via shutdownNow. Pools recreate lazily on next use.
     * 停机两个线程池并等待在途任务至多 timeoutMillis；超时强制取消。下次使用时自动重建。
     */
    public static void shutdownAll(long timeoutMillis) {
        ExecutorService bg;
        ForkJoinPool fj;
        synchronized (Schedulers.class) {
            bg = background;
            fj = forkJoin;
            background = null;
            forkJoin = null;
        }
        if (bg != null) bg.shutdown();
        if (fj != null) fj.shutdown();
        long half = Math.max(1, timeoutMillis / 2);
        try {
            if (bg != null && !bg.awaitTermination(half, TimeUnit.MILLISECONDS)) bg.shutdownNow();
            if (fj != null && !fj.awaitTermination(half, TimeUnit.MILLISECONDS)) fj.shutdownNow();
        } catch (InterruptedException e) {
            if (bg != null) bg.shutdownNow();
            if (fj != null) fj.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** 0 = auto fallback / 0 表示取自动值 */
    private static int resolve(int configured, int auto) {
        return configured > 0 ? configured : auto;
    }

    private Schedulers() {}
}
