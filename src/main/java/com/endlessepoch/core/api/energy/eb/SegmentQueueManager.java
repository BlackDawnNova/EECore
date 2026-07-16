package com.endlessepoch.core.api.energy.eb;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Sharded queue with ThreadLocal buffer. Reduces contention when many machines
 * publish events concurrently. Each shard maps to one segment of the posHash range.
 * 分片队列+ThreadLocal缓冲，减少大量机器并发发布时的竞争。
 */
public final class SegmentQueueManager {

    // Config value read once at lazy class init (0 = auto CPU×2) / 懒加载时读取一次配置（0=自动 CPU×2）
    private static final int SEGMENT_COUNT = com.endlessepoch.core.Config.ebSegmentCount > 0
            ? com.endlessepoch.core.Config.ebSegmentCount
            : Math.max(4, Schedulers.CPU * 2);
    private static final int LOCAL_FLUSH = 10;
    static final int MAX_QUEUE = 16384;

    @SuppressWarnings("unchecked")
    private static final Queue<EeEvent>[] SEGMENTS = new Queue[SEGMENT_COUNT];
    static {
        for (int i = 0; i < SEGMENT_COUNT; i++)
            SEGMENTS[i] = new ConcurrentLinkedQueue<>();
    }

    private static final ThreadLocal<ArrayDeque<EeEvent>> LOCAL =
            ThreadLocal.withInitial(ArrayDeque::new);

    private SegmentQueueManager() {}

    /** Route event to the correct shard via posHash. / 按posHash路由事件到分片。 */
    public static void enqueue(EeEvent event) {
        var local = LOCAL.get();
        local.add(event);
        if (local.size() >= LOCAL_FLUSH) {
            for (var e : local) {
                int si = HashUtil.segment(e.posHash(), SEGMENT_COUNT);
                var q = SEGMENTS[si];
                if (q.size() >= MAX_QUEUE) {
                    q.poll(); // drop oldest / 丢弃最早
                }
                q.add(e);
            }
            local.clear();
        }
    }

    /** Drain a segment into the given consumer. / 排空一个分片到消费者。 */
    public static int drainTo(int segment, java.util.function.Consumer<EeEvent> consumer, int max) {
        var q = SEGMENTS[segment];
        int count = 0;
        EeEvent ev;
        while (count < max && (ev = q.poll()) != null) {
            consumer.accept(ev);
            count++;
        }
        return count;
    }

    public static int segmentCount() { return SEGMENT_COUNT; }
}
