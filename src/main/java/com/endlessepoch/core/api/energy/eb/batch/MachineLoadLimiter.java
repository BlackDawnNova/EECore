package com.endlessepoch.core.api.energy.eb.batch;

import com.endlessepoch.core.Config;
import com.endlessepoch.core.EECore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-machine overload buffer. Splits one big BatchTask into fixed-size chunks and
 * feeds them to the ForkJoin pool gradually — a machine never floods the global shard
 * budget in one shot and stays under singleMachineShardLimit (≤ globalMaxShards / 3).
 * Results always deliver to {@link SegmentMergeManager}. Completions re-pump the same
 * machine; the server-tick driver re-pumps everyone starved by the global budget.
 * Pending shards above overloadWarnThreshold log one WARN per episode.
 * 单机过载缓冲。将大 BatchTask 切为固定分块渐进提交 ForkJoin——单机不会一次性灌满
 * 全局分片额度，且受 singleMachineShardLimit（≤ 全局并发/3）约束。结果恒投递
 * SegmentMergeManager。分块完成后续泵同机；被全局额度饿到的机器由每 tick 驱动点续泵。
 * 待处理分片超过 overloadWarnThreshold 每次过载输出一条 WARN。
 */
public final class MachineLoadLimiter {

    private static final Map<Long, MachineQueue> QUEUES = new ConcurrentHashMap<>();

    private MachineLoadLimiter() {}

    private static final class MachineQueue {
        final Queue<BatchTask> chunks = new ConcurrentLinkedQueue<>();
        final AtomicInteger inFlightShards = new AtomicInteger();
        volatile boolean cancelled;
        boolean overloadWarned; // guarded by synchronized(this) / 由 synchronized(this) 保护
    }

    /**
     * Split the task into chunks of chunkUnits inputs each. Pure — unit-testable.
     * 将任务按 chunkUnits 输入单元切块。纯函数，可单测。
     */
    static List<BatchTask> chunk(BatchTask task, int chunkUnits) {
        int size = Math.max(1, chunkUnits);
        List<InputUnit> units = task.units();
        if (units.size() <= size) return List.of(task);
        List<BatchTask> out = new ArrayList<>((units.size() + size - 1) / size);
        for (int from = 0; from < units.size(); from += size) {
            var sub = List.copyOf(units.subList(from, Math.min(units.size(), from + size)));
            out.add(new BatchTask(task.posHash(), task.machineTier(), task.heatValue(),
                    task.speedBoostMax(), task.maxOverclock(), task.energyEnabled(),
                    task.hardwareCap(), task.totalRate(), sub));
        }
        return out;
    }

    /** Queued + in-flight shards. Pure — unit-testable. / 待处理分片数。纯函数，可单测。 */
    static int pendingShards(Iterable<BatchTask> queued, int inFlight) {
        int total = inFlight;
        for (var t : queued) total += BatchExecutor.shardCount(t.units().size());
        return total;
    }

    /**
     * Enqueue a batch job in machine-local chunks and start pumping. Returns false only
     * for an empty task — a saturated pool just leaves chunks queued for later pumps.
     * 将批任务按分块入本机队列并开始泵送。仅空任务返回 false——池饱和时分块留队列等续泵。
     */
    public static boolean submit(BatchTask task) {
        if (task.units().isEmpty()) return false;
        var q = QUEUES.computeIfAbsent(task.posHash(), k -> new MachineQueue());
        var chunks = chunk(task, Config.p3BatchSize);
        q.chunks.addAll(chunks);
        if (Config.ebDebugLog)
            EECore.LOGGER.debug("[EB-DBG] limiter @{}: {} chunks queued ({} units → {} shards)",
                    task.posHash(), chunks.size(), task.units().size(),
                    pendingShards(chunks, 0));
        warnIfOverloaded(task.posHash(), q);
        pump(task.posHash(), q);
        return true;
    }

    /** Machine has nothing queued or in flight. / 该机器无排队也无在途分块。 */
    public static boolean isIdle(long posHash) {
        var q = QUEUES.get(posHash);
        return q == null || (q.chunks.isEmpty() && q.inFlightShards.get() == 0);
    }

    /** Queued + in-flight shards for diagnostics. / 待处理分片数（诊断用）。 */
    public static int pendingShards(long posHash) {
        var q = QUEUES.get(posHash);
        return q == null ? 0 : pendingShards(q.chunks, q.inFlightShards.get());
    }

    /**
     * Drop queued chunks for a removed/broken machine; in-flight completions are
     * discarded instead of delivered. / 机器移除/散型时清队列，在途完成丢弃不投递。
     */
    public static void cancel(long posHash) {
        var q = QUEUES.remove(posHash);
        if (q != null) {
            q.cancelled = true;
            q.chunks.clear();
        }
    }

    /** Server-tick driver: retry machines starved by the global budget. / 驱动点每 tick 续泵被全局额度饿到的机器。 */
    public static void pumpAll() {
        QUEUES.forEach(MachineLoadLimiter::pump);
    }

    /** Clear everything (server stopping). / 全部清空（服务器关闭）。 */
    public static void clearAll() {
        for (var q : QUEUES.values()) {
            q.cancelled = true;
            q.chunks.clear();
        }
        QUEUES.clear();
    }

    /**
     * Feed queued chunks to the ForkJoin pool while both the per-machine shard cap and
     * the global budget allow. Called from the main thread and from completions —
     * serialized per machine via the queue monitor.
     * 在单机分片上限与全局额度都允许时持续投喂分块。主线程与完成回调都会调用，
     * 以队列监视器按机器串行。
     */
    private static void pump(long posHash, MachineQueue q) {
        synchronized (q) {
            BatchTask head;
            while (!q.cancelled && (head = q.chunks.peek()) != null) {
                final int shards = BatchExecutor.shardCount(head.units().size());
                if (q.inFlightShards.get() + shards > Config.p3SingleMachineShardLimit) return;
                boolean ok = BatchExecutor.trySubmit(head, results -> {
                    q.inFlightShards.addAndGet(-shards);
                    if (!q.cancelled) {
                        SegmentMergeManager.deliver(posHash, results);
                        pump(posHash, q); // keep the machine fed / 续泵本机
                    }
                });
                if (!ok) return; // global budget saturated — driver re-pumps / 全局额度饱和，驱动点续泵
                q.chunks.poll();
                q.inFlightShards.addAndGet(shards);
            }
            if (q.chunks.isEmpty()) q.overloadWarned = false;
        }
    }

    /** One WARN per overload episode, reset when the queue drains. / 每次过载只 WARN 一次，排空后复位。 */
    private static void warnIfOverloaded(long posHash, MachineQueue q) {
        synchronized (q) {
            int pending = pendingShards(q.chunks, q.inFlightShards.get());
            if (pending > Config.p3OverloadWarnThreshold && !q.overloadWarned) {
                q.overloadWarned = true;
                EECore.LOGGER.warn("[EB-P3] machine @{} overloaded: {} pending shards (threshold {})",
                        posHash, pending, Config.p3OverloadWarnThreshold);
            }
        }
    }
}
