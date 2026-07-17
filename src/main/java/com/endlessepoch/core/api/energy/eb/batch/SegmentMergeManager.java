package com.endlessepoch.core.api.energy.eb.batch;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Per-machine delivery queues for computed batch results. ForkJoin threads deliver,
 * the machine's serverTick polls its own queue — no shared main-thread bottleneck.
 * M4 adds segmentMergeCount-based partial delivery; M3 delivers whole tasks.
 * 每台机器一条批处理结果投递队列。ForkJoin 线程投递，机器 serverTick 自取。
 * M4 接入 segmentMergeCount 分段投递；M3 整任务投递。
 */
public final class SegmentMergeManager {

    private static final Map<Long, Queue<MergedSegment>> DELIVERY = new ConcurrentHashMap<>();

    private SegmentMergeManager() {}

    /** Deliver results (empty list = completion signal). / 投递结果（空列表也投递，作为完成信号）。 */
    public static void deliver(long posHash, List<ShardResultUnit> results) {
        DELIVERY.computeIfAbsent(posHash, k -> new ConcurrentLinkedQueue<>())
                .add(new MergedSegment(posHash, List.copyOf(results)));
    }

    /** Main-thread poll, null if nothing pending. / 主线程取段，无则 null。 */
    public static MergedSegment poll(long posHash) {
        var q = DELIVERY.get(posHash);
        return q == null ? null : q.poll();
    }

    /** Drop everything for a removed/broken machine. / 机器移除/散型时清空。 */
    public static void clear(long posHash) {
        DELIVERY.remove(posHash);
    }

    /** Drop all queues (server stopping). / 全部清空（服务器关闭）。 */
    public static void clearAll() {
        DELIVERY.clear();
    }
}
